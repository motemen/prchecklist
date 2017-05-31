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
	Stage string
	Items []*ChecklistItem
	// Stage  string
	// Stages []string
	Config *ChecklistConfig
}

func (c Checklist) Completed() bool {
	for _, item := range c.Items {
		if len(item.CheckedBy) == 0 {
			return false
		}
	}
	return true
}

func (c Checklist) Item(featNum int) *ChecklistItem {
	for _, item := range c.Items {
		if item.Number == featNum {
			return item
		}
	}
	return nil
}

func (c Checklist) Path() string {
	path := fmt.Sprintf("%s/%s/pull/%d", c.Owner, c.Repo, c.Number)
	if c.Stage != "" {
		path = path + "/" + c.Stage
	}
	return path
}

func (c Checklist) String() string {
	s := fmt.Sprintf("%s/%s#%d", c.Owner, c.Repo, c.Number)
	if c.Stage != "default" {
		s = s + "::" + c.Stage
	}
	return s
}

type ChecklistConfig struct {
	Stages       []string
	Notification struct {
		Events struct {
			OnComplete []string `yaml:"on_complete"` // channel names
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

func (c Checks) Add(featNum int, user GitHubUser) bool {
	for _, userID := range c[featNum] {
		if user.ID == userID {
			// already checked
			return false
		}
	}

	c[featNum] = append(c[featNum], user.ID)
	return true
}

func (c Checks) Remove(featNum int, user GitHubUser) bool {
	for i, userID := range c[featNum] {
		if user.ID == userID {
			c[featNum] = append(c[featNum][0:i], c[featNum][i+1:]...)
			return true
		}
	}

	return false
}

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
	URL       string
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
