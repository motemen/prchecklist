package prchecklist

import (
	"context"
	"fmt"
	"net/http"

	"github.com/pkg/errors"
	"golang.org/x/oauth2"
)

// ChecklistResponse represents the JSON for a single Checklist.
type ChecklistResponse struct {
	Checklist *Checklist
	Me        *GitHubUser
}

// MeResponse represents the JSON for the top page.
type MeResponse struct {
	Me           *GitHubUser
	PullRequests map[string][]*PullRequest
}

// Checklist is the main entity of prchecklist.
// It is identified by a "release" pull request PullRequest
// (which is identified by its Owner, Repo and Number) and a Stage, if any.
// The checklist Items correspond to "feature" pull requests
// that have been merged into the head of "release" pull request
// and the "release" pull request is about to merge into master.
type Checklist struct {
	*PullRequest
	Stage  string
	Items  []*ChecklistItem
	Config *ChecklistConfig
}

// Completed returns whether all the items are checked by any user.
func (c Checklist) Completed() bool {
	for _, item := range c.Items {
		if len(item.CheckedBy) == 0 {
			return false
		}
	}
	return true
}

// UserCompleted returns whether all the items are checked by any user.
func (c Checklist) UserCompleted(user GitHubUserSimple) bool {
	for _, item := range c.Items {
		if len(item.CheckedBy) == 0 && item.User.Login == user.Login {
			return false
		}
	}
	return true
}

// Item returns the ChecklistItem associated by the feature PR number featNum.
func (c Checklist) Item(featNum int) *ChecklistItem {
	for _, item := range c.Items {
		if item.Number == featNum {
			return item
		}
	}
	return nil
}

// Path returns the path used for the permalink of the checklist c.
func (c Checklist) Path() string {
	path := fmt.Sprintf("/%s/%s/pull/%d", c.Owner, c.Repo, c.Number)
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

// ChecklistConfig is a configuration object for the repository,
// which is specified by prchecklist.yml on the top of the repository.
type ChecklistConfig struct {
	Stages       []string
	Notification struct {
		Events struct {
			OnComplete     []string `yaml:"on_complete"`      // channel names
			OnUserComplete []string `yaml:"on_user_complete"` // channel names
			OnCheck        []string `yaml:"on_check"`         // channel names
			OnRemove       []string `yaml:"on_remove"`        // channel names
		}
		Channels map[string]struct{ URL string }
	}
}

// ChecklistItem is a checklist item, which belongs to a Checklist
// and can be checked by multiple GitHubUsers.
type ChecklistItem struct {
	// the "feature" pull request corresponds to this item
	*PullRequest
	CheckedBy []GitHubUser
}

// Checks is a value object obtained by repository.Repositor.GetChecks,
// which is a map from string key to IDs of GitHubUsers.
// It is ready for serialization/deserialization.
// For future extension, use strings instead of ints
// for the keys of Checks.
type Checks map[string][]int // "PullReqNumber" -> []UserID

// ChecksKeyFeatureNum builds key string to use for Checks
// from a feature pull request number featNum.
func ChecksKeyFeatureNum(featNum int) string {
	return fmt.Sprint(featNum)
}

// Add adds a check for featNum by user.
func (c Checks) Add(featNum string, user GitHubUser) bool {
	for _, userID := range c[featNum] {
		if user.ID == userID {
			// already checked
			return false
		}
	}

	c[featNum] = append(c[featNum], user.ID)
	return true
}

// Remove removes a check for featNum by user.
func (c Checks) Remove(featNum string, user GitHubUser) bool {
	for i, userID := range c[featNum] {
		if user.ID == userID {
			c[featNum] = append(c[featNum][0:i], c[featNum][i+1:]...)
			return true
		}
	}

	return false
}

// ChecklistRef represents a pointer to Checklist.
type ChecklistRef struct {
	Owner  string
	Repo   string
	Number int
	Stage  string
}

func (clRef ChecklistRef) String() string {
	return fmt.Sprintf("%s/%s#%d::%s", clRef.Owner, clRef.Repo, clRef.Number, clRef.Stage)
}

// Validate validates is clRef is valid or returns error.
func (clRef ChecklistRef) Validate() error {
	if clRef.Number == 0 || clRef.Stage == "" {
		return errors.Errorf("not a valid checklist reference: %q", clRef)
	}

	return nil
}

// PullRequest represens a pull request on GitHub.
type PullRequest struct {
	URL       string
	Title     string
	Body      string
	Owner     string
	Repo      string
	Number    int
	IsPrivate bool
	User      GitHubUserSimple

	// Filled for "base" pull reqs
	Commits      []Commit
	ConfigBlobID string
}

// Commit is a commit data on GitHub.
type Commit struct {
	Message string
	Oid     string
}

// GitHubUserSimple is a minimalistic GitHub user data.
type GitHubUserSimple struct {
	Login string
}

// GitHubUser is represents a GitHub user.
// Its Token field is populated only for the representation of
// a visiting client.
type GitHubUser struct {
	ID        int
	Login     string
	AvatarURL string
	Token     *oauth2.Token `json:"-"`
}

// HTTPClient creates an *http.Client which uses u.Token
// to be used for GitHub API client on behalf of the user u.
func (u GitHubUser) HTTPClient(ctx context.Context) *http.Client {
	return oauth2.NewClient(ctx, oauth2.StaticTokenSource(u.Token))
}

// ErrorResponse corresponds to JSON containing error results in APIs.
type ErrorResponse struct {
	Type ErrorType
}

// ErrorType indicates the type of ErrorResponse.
type ErrorType string

const (
	// ErrorTypeNotAuthed means: Visitor has not been authenticated. Should visit /auth
	ErrorTypeNotAuthed ErrorType = "not_authed"
)
