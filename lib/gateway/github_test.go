package gateway

import (
	"context"
	"os"
	"testing"

	"github.com/google/go-github/v31/github"
	"github.com/stretchr/testify/assert"
	"golang.org/x/oauth2"

	"github.com/motemen/prchecklist/v2"
)

func TestGitHub_GetPullRequest(t *testing.T) {
	token := os.Getenv("PRCHECKLIST_TEST_GITHUB_TOKEN")
	if token == "" {
		t.Skipf("PRCHECKLIST_TEST_GITHUB_TOKEN not set")
	}

	github, err := NewGitHub()
	assert.NoError(t, err)

	ctx := context.Background()
	cli := oauth2.NewClient(
		ctx,
		oauth2.StaticTokenSource(&oauth2.Token{AccessToken: token}),
	)
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, cli)
	_, _, err = github.GetPullRequest(ctx, prchecklist.ChecklistRef{
		Owner:  "motemen",
		Repo:   "test-repository",
		Number: 2,
	}, true)
	assert.NoError(t, err)
}

func TestGitHub_GetPullRequest_MoreThan100Commits(t *testing.T) {
	token := os.Getenv("PRCHECKLIST_TEST_GITHUB_TOKEN")
	if token == "" {
		t.Skipf("PRCHECKLIST_TEST_GITHUB_TOKEN not set")
	}

	gw, err := NewGitHub()
	assert.NoError(t, err)

	ctx := context.Background()
	cli := oauth2.NewClient(
		ctx,
		oauth2.StaticTokenSource(&oauth2.Token{AccessToken: token}),
	)
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, cli)

	// openengsb/openengsb PR #509 has 134 commits
	pullReq, _, err := gw.GetPullRequest(ctx, prchecklist.ChecklistRef{
		Owner:  "openengsb",
		Repo:   "openengsb",
		Number: 509,
	}, true)
	assert.NoError(t, err)
	assert.NotNil(t, pullReq)

	// Verify that we got more than 100 commits (pagination working)
	assert.Greater(t, len(pullReq.Commits), 100, "Expected more than 100 commits to verify pagination is working")

	// Use REST API to verify the exact commit count
	restClient := github.NewClient(cli)
	restPR, _, err := restClient.PullRequests.Get(ctx, "openengsb", "openengsb", 509)
	assert.NoError(t, err)
	assert.NotNil(t, restPR)

	expectedCommits := restPR.GetCommits()
	assert.Equal(t, expectedCommits, len(pullReq.Commits), "GraphQL commit count should match REST API commit count")
}
