package prchecklist

import (
	"context"
	"fmt"
	"github.com/pkg/errors"
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
	Config *ChecklistConfig
}

type ChecklistConfig struct {
	Stages       []string
	Notification struct {
		Events struct {
			OnComplete []string `yaml:"on_complete"`
			OnCheck    []string `yaml:"on_check"`
		}
		Channels map[string]struct{ URL string }
	}
}

type ChecklistItem struct {
	*PullRequest
	CheckedBy []GitHubUser
}

type Checks map[int][]int // PullReqNumber -> []UserID

type ChecklistRef struct {
	Owner  string
	Repo   string
	Number int
	Stage  string
}

func (clRef ChecklistRef) String() string {
	return fmt.Sprintf("%s/%s#%d::%s", clRef.Owner, clRef.Repo, clRef.Number, clRef.Stage)
}

func (clRef ChecklistRef) Validate() error {
	if clRef.Number == 0 || clRef.Stage == "" {
		return errors.Errorf("not a valid checklist reference: %q", clRef)
	}

	return nil
}

type PullRequest struct {
	// URL     string
	Title     string
	Body      string
	Owner     string
	Repo      string
	Number    int
	IsPrivate bool
	// Assignees []GitHubUser

	// Filled for "main" pull reqs
	Commits      []Commit
	ConfigBlobID string
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
