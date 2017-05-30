package usecase

import (
	"context"
	"log"
	"regexp"
	"strconv"

	"golang.org/x/sync/errgroup"
	"gopkg.in/yaml.v2"

	"github.com/motemen/go-prchecklist"
	"github.com/pkg/errors"
)

type GitHubRepository interface {
	GetBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error)
	GetPullRequest(ctx context.Context, clRef prchecklist.ChecklistRef, isMain bool) (*prchecklist.PullRequest, error)
}

type CoreRepository interface {
	GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error)
	AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, number int, user prchecklist.GitHubUser) error
	RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, number int, user prchecklist.GitHubUser) error

	AddUser(ctx context.Context, user prchecklist.GitHubUser) error
	GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error)
}

type Usecase struct {
	coreRepo   CoreRepository
	githubRepo GitHubRepository
}

func New(githubRepo GitHubRepository, coreRepo CoreRepository) *Usecase {
	return &Usecase{
		coreRepo:   coreRepo,
		githubRepo: githubRepo,
	}
}

func (u Usecase) GetChecklist(ctx context.Context, clRef prchecklist.ChecklistRef) (*prchecklist.Checklist, error) {
	pr, err := u.githubRepo.GetPullRequest(ctx, clRef, true)
	if err != nil {
		return nil, err
	}

	refs := u.mergedPullRequestRefs(pr)

	checklist := &prchecklist.Checklist{
		PullRequest: pr,
		Items:       make([]*prchecklist.ChecklistItem, len(refs)),
		Config:      nil,
	}

	g, ctx := errgroup.WithContext(ctx)
	for i, ref := range refs {
		i, ref := i, ref
		g.Go(func() error {
			featurePullReq, err := u.githubRepo.GetPullRequest(ctx, ref, false)
			if err != nil {
				return err
			}

			checklist.Items[i] = &prchecklist.ChecklistItem{
				PullRequest: featurePullReq,
				CheckedBy:   []prchecklist.GitHubUser{}, // filled up later
			}
			return nil
		})
	}

	if pr.ConfigBlobID != "" {
		g.Go(func() error {
			buf, err := u.githubRepo.GetBlob(ctx, clRef, pr.ConfigBlobID)
			if err != nil {
				return errors.Wrap(err, "githubRepo.GetBlob")
			}

			var config prchecklist.ChecklistConfig
			err = yaml.Unmarshal(buf, &config)
			if err != nil {
				return errors.Wrap(err, "yaml.Unmarshal")
			}
			checklist.Config = &config

			return nil
		})
	}

	err = g.Wait()
	if err != nil {
		return nil, err
	}

	// may move to before fetching feature pullreqs
	// for early return
	checks, err := u.coreRepo.GetChecks(ctx, clRef)
	if err != nil {
		return nil, err
	}

	log.Printf("%s: checks: %+v", clRef, checks)

	for featNum, userIDs := range checks {
		if len(userIDs) == 0 {
			continue
		}

		users, err := u.coreRepo.GetUsers(ctx, userIDs)
		if err != nil {
			return nil, err
		}

		for _, item := range checklist.Items {
			if item.Number != featNum {
				continue
			}

			for _, id := range userIDs {
				item.CheckedBy = append(item.CheckedBy, users[id])
			}
		}
	}

	return checklist, nil
}

func (u Usecase) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	return u.coreRepo.AddUser(ctx, user)
}

func (u Usecase) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) error {
	// TODO: check visibilities
	// TODO: check featNum existence
	// NOTE: could receive only token (from ctx) and check visiblities & get user info
	return u.coreRepo.AddCheck(ctx, clRef, featNum, user)
}

func (u Usecase) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) error {
	// TODO: check visibilities
	// TODO: check featNum existence
	// NOTE: could receive only token (from ctx) and check visiblities & get user info
	return u.coreRepo.RemoveCheck(ctx, clRef, featNum, user)
}

var rxMergeCommitMessage = regexp.MustCompile(`\AMerge pull request #(?P<number>\d+) `)

func (u Usecase) mergedPullRequestRefs(pr *prchecklist.PullRequest) []prchecklist.ChecklistRef {
	refs := []prchecklist.ChecklistRef{}
	for _, commit := range pr.Commits {
		m := rxMergeCommitMessage.FindStringSubmatch(commit.Message)
		if m == nil {
			continue
		}
		n, _ := strconv.ParseInt(m[1], 10, 0)
		if n > 0 {
			refs = append(refs, prchecklist.ChecklistRef{
				Owner:  pr.Owner,
				Repo:   pr.Repo,
				Number: int(n),
			})
		}
	}
	return refs
}
