package prchecklist

import (
	"context"
	"net/http"

	"golang.org/x/oauth2"
)

type Checklist struct {
	PullRequest *PullRequest
	Features    []*PullRequest
}

type ChecklistRef struct {
	Owner  string
	Repo   string
	Number int
}

type PullRequest struct {
	Title   string
	Owner   string
	Repo    string
	Number  int
	Commits []Commit
}

type Commit struct {
	Message string
}

type GitHubUser struct {
	ID        int
	Login     string
	AvatarURL string
	Token     *oauth2.Token
}

func (u GitHubUser) HTTPClient(ctx context.Context) *http.Client {
	return oauth2.NewClient(ctx, oauth2.StaticTokenSource(u.Token))
}
