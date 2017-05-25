package usecase

import (
	"context"
	"regexp"
	"strconv"

	"github.com/motemen/go-prchecklist"
)

type GitHubRepository interface {
	GetPullRequest(ctx context.Context, clRef prchecklist.ChecklistRef) (*prchecklist.PullRequest, error)
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
	pr, err := u.githubRepo.GetPullRequest(ctx, clRef)
	if err != nil {
		return nil, err
	}

	// featurePullRequestNums := mergedPullRequestNumbers(pr)

	return &prchecklist.Checklist{
		PullRequest: pr,
	}, nil
}

func mergedPullRequestNumbers(pr *prchecklist.PullRequest) []int {
	numbers := []int{}
	for _, commit := range pr.Commits {
		m := rxMergeCommitMessage.FindStringSubmatch(commit.Message)
		if m == nil {
			continue
		}
		n, _ := strconv.ParseInt(m[1], 10, 0)
		if n > 0 {
			numbers = append(numbers, int(n))
		}
	}
	return numbers
}
