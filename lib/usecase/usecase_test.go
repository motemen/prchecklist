//go:generate go run github.com/golang/mock/mockgen -package mocks -destination ../mocks/core_repository.go . CoreRepository
//go:generate go run github.com/golang/mock/mockgen -package mocks -destination ../mocks/github_gateway.go . GitHubGateway

package usecase

import (
	"context"
	"testing"

	"github.com/golang/mock/gomock"

	prchecklist "github.com/motemen/prchecklist/v2"
	"github.com/motemen/prchecklist/v2/lib/repository_mock"
)

func TestGetChecklist(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	repo := repository_mock.NewMockCoreRepository(ctrl)
	g := NewMockGitHubGateway(ctrl)

	u := New(g, repo)
	ctx := context.Background()
	clRef := prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 1, Stage: "default"}

	g.EXPECT().GetPullRequest(ctx, clRef, true).Return(&prchecklist.PullRequest{}, ctx, nil)
	repo.EXPECT().GetChecks(ctx, clRef).Return(prchecklist.Checks{}, nil)
	repo.EXPECT().GetUsers(ctx, gomock.Len(0)).Return(map[int]prchecklist.GitHubUser{}, nil)

	c, err := u.GetChecklist(ctx, clRef)
	t.Log(c, err)
}
