package usecase

import (
	"context"
	"golang.org/x/sync/errgroup"
	"regexp"
	"strconv"

	"github.com/motemen/go-prchecklist"
)

type GitHubRepository interface {
	GetPullRequest(ctx context.Context, clRef prchecklist.ChecklistRef, withCommits bool) (*prchecklist.PullRequest, error)
}

type Usecase struct {
	githubRepo GitHubRepository
}

func New(githubRepo GitHubRepository) *Usecase {
	return &Usecase{
		githubRepo: githubRepo,
	}
}

var rxMergeCommitMessage = regexp.MustCompile(`\AMerge pull request #(?P<number>\d+) `)

func (u Usecase) GetChecklist(ctx context.Context, clRef prchecklist.ChecklistRef) (*prchecklist.Checklist, error) {
	pr, err := u.githubRepo.GetPullRequest(ctx, clRef, true)
	if err != nil {
		return nil, err
	}

	refs := mergedPullRequestRefs(pr)
	featurePRsC := make(chan *prchecklist.PullRequest, len(refs))
	g, ctx := errgroup.WithContext(ctx)
	for _, ref := range refs {
		g.Go(func() error {
			featurePR, err := u.githubRepo.GetPullRequest(ctx, ref, false)
			featurePRsC <- featurePR
			return err
		})
	}

	err = g.Wait()
	if err != nil {
		return nil, err
	}

	close(featurePRsC)

	featurePRs := make([]*prchecklist.PullRequest, 0, len(refs))
	for pr := range featurePRsC {
		featurePRs = append(featurePRs, pr)
	}

	return &prchecklist.Checklist{
		PullRequest: pr,
		Features:    featurePRs,
	}, nil
}

func mergedPullRequestRefs(pr *prchecklist.PullRequest) []prchecklist.ChecklistRef {
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
