package usecase

import (
	"context"
	"log"
	"regexp"
	"strconv"

	"github.com/pkg/errors"
	"golang.org/x/sync/errgroup"
	"golang.org/x/tools/container/intsets"
	"gopkg.in/yaml.v2"

	"github.com/motemen/prchecklist"
)

type GitHubGateway interface {
	GetBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error)
	GetPullRequest(ctx context.Context, clRef prchecklist.ChecklistRef, isMain bool) (*prchecklist.PullRequest, context.Context, error)
	GetRecentPullRequests(ctx context.Context) (map[string][]*prchecklist.PullRequest, error)
}

type CoreRepository interface {
	GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error)
	AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error
	RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error

	AddUser(ctx context.Context, user prchecklist.GitHubUser) error
	GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error)
}

type Usecase struct {
	coreRepo CoreRepository
	github   GitHubGateway
}

func New(github GitHubGateway, coreRepo CoreRepository) *Usecase {
	return &Usecase{
		coreRepo: coreRepo,
		github:   github,
	}
}

// Only this method can create prchecklist.Checklist.
func (u Usecase) GetChecklist(ctx context.Context, clRef prchecklist.ChecklistRef) (*prchecklist.Checklist, error) {
	pr, ctx, err := u.github.GetPullRequest(ctx, clRef, true)
	if err != nil {
		return nil, err
	}

	refs := u.mergedPullRequestRefs(pr)

	checklist := &prchecklist.Checklist{
		PullRequest: pr,
		Stage:       clRef.Stage,
		Items:       make([]*prchecklist.ChecklistItem, len(refs)),
		Config:      nil,
	}

	{
		g, ctx := errgroup.WithContext(ctx)
		for i, ref := range refs {
			i, ref := i, ref
			g.Go(func() error {
				featurePullReq, _, err := u.github.GetPullRequest(ctx, ref, false)
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
				buf, err := u.github.GetBlob(ctx, clRef, pr.ConfigBlobID)
				if err != nil {
					return errors.Wrap(err, "github.GetBlob")
				}

				checklist.Config, err = u.loadConfig(buf)
				return err
			})
		}

		err = g.Wait()
		if err != nil {
			return nil, err
		}
	}

	// may move to before fetching feature pullreqs
	// for early return
	checks, err := u.coreRepo.GetChecks(ctx, clRef)
	if err != nil {
		return nil, err
	}

	log.Printf("%s: checks: %+v", clRef, checks)

	var s intsets.Sparse
	for _, userIDs := range checks {
		for _, id := range userIDs {
			s.Insert(id)
		}
	}

	users, err := u.coreRepo.GetUsers(ctx, s.AppendTo(nil))
	if err != nil {
		return nil, err
	}

	for _, item := range checklist.Items {
		for _, id := range checks[prchecklist.ChecksKeyFeatureNum(item.Number)] {
			item.CheckedBy = append(item.CheckedBy, users[id])
		}
	}

	return checklist, nil
}

func (u Usecase) loadConfig(buf []byte) (*prchecklist.ChecklistConfig, error) {
	var config prchecklist.ChecklistConfig
	err := yaml.Unmarshal(buf, &config)
	if err != nil {
		return nil, errors.Wrap(err, "yaml.Unmarshal")
	}

	if config.Notification.Events.OnCheck == nil && config.Notification.Events.OnComplete == nil {
		config.Notification.Events.OnCheck = []string{"default"}
		config.Notification.Events.OnComplete = []string{"default"}
	}

	return &config, nil
}

func (u Usecase) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	return u.coreRepo.AddUser(ctx, user)
}

func (u Usecase) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) (*prchecklist.Checklist, error) {
	// NOTE: could receive only token (from ctx) and check visiblities & get user info
	err := u.coreRepo.AddCheck(ctx, clRef, prchecklist.ChecksKeyFeatureNum(featNum), user)
	if err != nil {
		return nil, err
	}

	checklist, err := u.GetChecklist(ctx, clRef)
	if err != nil {
		return nil, err
	}

	// TODO: check item existence?
	go func() {
		// notify in sequence
		events := []notificationEvent{
			addCheckEvent{checklist: checklist, item: checklist.Item(featNum), user: user},
		}
		if checklist.Completed() {
			events = append(events, completeEvent{checklist: checklist})
		}
		for _, event := range events {
			err := u.notifyEvent(ctx, checklist, event)
			if err != nil {
				log.Printf("notifyEvent(%v): %s", event, err)
			}
		}
	}()

	return checklist, nil
}

func (u Usecase) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) (*prchecklist.Checklist, error) {
	// TODO: check featNum existence
	// NOTE: could receive only token (from ctx) and check visiblities & get user info
	err := u.coreRepo.RemoveCheck(ctx, clRef, prchecklist.ChecksKeyFeatureNum(featNum), user)
	if err != nil {
		return nil, err
	}

	return u.GetChecklist(ctx, clRef)
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

func (u Usecase) GetRecentPullRequests(ctx context.Context) (map[string][]*prchecklist.PullRequest, error) {
	return u.github.GetRecentPullRequests(ctx)
}
