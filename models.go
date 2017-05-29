package prchecklist

import (
	"context"
	"fmt"
	"net/http"

	"golang.org/x/oauth2"
)

type ChecklistResponse struct {
	Checklist *Checklist
	Me        *GitHubUser
}

type Checklist struct {
	*PullRequest
	Items []*ChecklistItem
	// Stage  string
	// Stages []string
}

type ChecklistItem struct {
	*PullRequest
	CheckedBy   []GitHubUser
	CheckedByMe bool
}

type Checks map[int][]int // PullReqNumber -> []UserID

type ChecklistRef struct {
	Owner  string
	Repo   string
	Number int
}

func (clRef ChecklistRef) String() string {
	return fmt.Sprintf("%s/%s#%d", clRef.Owner, clRef.Repo, clRef.Number)
}

type PullRequest struct {
	// URL     string
	Title     string
	Body      string
	Owner     string
	Repo      string
	Number    int
	Commits   []Commit
	IsPrivate bool
	// Assignees []GitHubUser
}

type Commit struct {
	Message string
}

type GitHubUser struct {
	ID        int
	Login     string
	AvatarURL string
	Token     *oauth2.Token `json:"-"`
}

func (u GitHubUser) HTTPClient(ctx context.Context) *http.Client {
	return oauth2.NewClient(ctx, oauth2.StaticTokenSource(u.Token))
}
