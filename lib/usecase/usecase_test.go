//go:generate go run github.com/golang/mock/mockgen -package mocks -destination ../mocks/core_repository.go . CoreRepository
//go:generate go run github.com/golang/mock/mockgen -package mocks -destination ../mocks/github_gateway.go . GitHubGateway

package usecase

import (
	"context"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"

	prchecklist "github.com/motemen/prchecklist/v2"
	"github.com/motemen/prchecklist/v2/lib/repository_mock"
)

func TestUseCase_GetChecklist(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	repo := repository_mock.NewMockCoreRepository(ctrl)
	github := NewMockGitHubGateway(ctrl)

	clRef := prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 1, Stage: "default"}

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		clRef,
		true,
	).Return(&prchecklist.PullRequest{
		Owner: "test",
		Repo:  "test",
		Commits: []prchecklist.Commit{
			{Message: "Merge pull request #2 "},
		},
		ConfigBlobID: "DUMMY-CONFIG-BLOB-ID",
	}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 2},
		false,
	).Return(&prchecklist.PullRequest{}, context.Background(), nil)

	github.EXPECT().GetBlob(
		gomock.Any(),
		clRef,
		"DUMMY-CONFIG-BLOB-ID",
	).Return(
		[]byte(`---
stages:
  - qa
  - production
`),
		nil,
	)

	repo.EXPECT().GetChecks(gomock.Any(), clRef).
		Return(prchecklist.Checks{}, nil)

	repo.EXPECT().GetUsers(gomock.Any(), gomock.Len(0)).
		Return(map[int]prchecklist.GitHubUser{}, nil)

	app := New(github, repo)
	ctx := context.Background()

	cl, err := app.GetChecklist(ctx, clRef)

	assert.NoError(t, err)
	assert.Equal(
		t, cl.Config.Stages, []string{"qa", "production"},
	)
}

func TestUseCase_GetChecklist_SkipsItemsByLabel(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	repo := repository_mock.NewMockCoreRepository(ctrl)
	github := NewMockGitHubGateway(ctrl)

	clRef := prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 1, Stage: "qa"}

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		clRef,
		true,
	).Return(&prchecklist.PullRequest{
		Owner: "test",
		Repo:  "test",
		Commits: []prchecklist.Commit{
			{Message: "Merge pull request #2 "},
			{Message: "Merge pull request #3 "},
			{Message: "Merge pull request #4 "},
		},
		ConfigBlobID: "DUMMY-CONFIG-BLOB-ID",
	}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 2},
		false,
	).Return(&prchecklist.PullRequest{Number: 2, Labels: []string{"no-qa"}}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 3},
		false,
	).Return(&prchecklist.PullRequest{Number: 3, Labels: []string{"no-production-check"}}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 4},
		false,
	).Return(&prchecklist.PullRequest{Number: 4}, context.Background(), nil)

	github.EXPECT().GetBlob(
		gomock.Any(),
		clRef,
		"DUMMY-CONFIG-BLOB-ID",
	).Return(
		[]byte(`---
stages:
  - qa
  - production
skip:
  labels:
    - name: no-qa
    - name: no-production-check
      stages:
        - production
`),
		nil,
	)

	repo.EXPECT().GetChecks(gomock.Any(), clRef).
		Return(prchecklist.Checks{}, nil)

	repo.EXPECT().GetUsers(gomock.Any(), gomock.Len(0)).
		Return(map[int]prchecklist.GitHubUser{}, nil)

	app := New(github, repo)

	cl, err := app.GetChecklist(context.Background(), clRef)

	assert.NoError(t, err)
	assert.True(t, cl.Item(2).Skipped, "#2 has a label to skip on all stages")
	assert.False(t, cl.Item(3).Skipped, "#3 has a label to skip only on production")
	assert.False(t, cl.Item(4).Skipped, "#4 has no labels")
}

func setupMocks(clRef prchecklist.ChecklistRef, github *MockGitHubGateway, repo *repository_mock.MockCoreRepository) {
	github.EXPECT().GetPullRequest(
		gomock.Any(),
		clRef,
		true,
	).Return(&prchecklist.PullRequest{
		Owner: "test",
		Repo:  "test",
		Commits: []prchecklist.Commit{
			{Message: "Merge pull request #2 "},
			{Message: "Merge pull request #3 "},
		},
	}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 2},
		false,
	).Return(&prchecklist.PullRequest{
		Number: 2,
	}, context.Background(), nil)

	github.EXPECT().GetPullRequest(
		gomock.Any(),
		prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 3},
		false,
	).Return(&prchecklist.PullRequest{
		Number: 3,
	}, context.Background(), nil)

	repo.EXPECT().GetChecks(gomock.Any(), clRef).
		Return(prchecklist.Checks{}, nil)

	repo.EXPECT().GetUsers(gomock.Any(), gomock.Len(0)).
		Return(map[int]prchecklist.GitHubUser{}, nil)
}

func TestUsecase_AddCheck(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	clRef := prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 1, Stage: "default"}
	repo := repository_mock.NewMockCoreRepository(ctrl)
	github := NewMockGitHubGateway(ctrl)

	setupMocks(clRef, github, repo)

	repo.EXPECT().AddCheck(
		gomock.Any(),
		clRef,
		"2",
		gomock.Any(),
	)

	app := New(github, repo)
	ctx := context.Background()

	cl, err := app.AddCheck(
		ctx,
		clRef,
		2,
		prchecklist.GitHubUser{
			ID:    1,
			Login: "test",
		},
	)

	assert.NoError(t, err)
	t.Log(cl)
}

func TestUsecase_RemoveCheck(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	clRef := prchecklist.ChecklistRef{Owner: "test", Repo: "test", Number: 1, Stage: "default"}
	repo := repository_mock.NewMockCoreRepository(ctrl)
	github := NewMockGitHubGateway(ctrl)

	setupMocks(clRef, github, repo)

	repo.EXPECT().RemoveCheck(
		gomock.Any(),
		clRef,
		"2",
		gomock.Any(),
	)

	app := New(github, repo)
	ctx := context.Background()

	cl, err := app.RemoveCheck(
		ctx,
		clRef,
		2,
		prchecklist.GitHubUser{
			ID:    1,
			Login: "test",
		},
	)

	assert.NoError(t, err)
	t.Log(cl)
}
