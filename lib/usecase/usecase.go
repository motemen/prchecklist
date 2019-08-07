package usecase

import (
	"context"
	"fmt"
	"log"
	"regexp"
	"strconv"

	"github.com/pkg/errors"
	"golang.org/x/sync/errgroup"
	"golang.org/x/tools/container/intsets"
	"gopkg.in/yaml.v2"

	"github.com/motemen/prchecklist"
)

// GitHubGateway is an interface that makes API calls to GitHub (Enterprise) and
// retrieves information about repositories.
// Implemented by gateway.GitHub.
type GitHubGateway interface {
	GetBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error)
	GetPullRequest(ctx context.Context, clRef prchecklist.ChecklistRef, isMain bool) (*prchecklist.PullRequest, context.Context, error)
	GetRecentPullRequests(ctx context.Context) (map[string][]*prchecklist.PullRequest, error)
	SetRepositoryStatusAs(ctx context.Context, owner, repo, ref, contextName, state, targetURL string) error
}

// CoreRepository is a repository for prchecklist's core data,
// namely Checks and GitHubUsers.
type CoreRepository interface {
	// GetChecks returns the Checks for the checklist pointed by clRef
	GetChecks(ctx context.Context, clRef prchecklist.ChecklistRef) (prchecklist.Checks, error)
	// AddCheck updates the Checks for the checklist pointed by clRef, by adding a check of the user for the item specified by key.
	AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error
	// RemoveCheck updates the Checks for the checklist pointed by clRef, by removing a check of the user for the item specified by key.
	RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, key string, user prchecklist.GitHubUser) error

	// AddUser registers the user's data, which can retrieved by GetUsers.
	AddUser(ctx context.Context, user prchecklist.GitHubUser) error
	// GetUsers retrieves the users' data registered by AddUser.
	GetUsers(ctx context.Context, userIDs []int) (map[int]prchecklist.GitHubUser, error)
}

// Usecase stands for the use cases of this application by its methods.
type Usecase struct {
	coreRepo CoreRepository
	github   GitHubGateway
}

// New creates a new Usecase.
func New(github GitHubGateway, coreRepo CoreRepository) *Usecase {
	return &Usecase{
		coreRepo: coreRepo,
		github:   github,
	}
}

// GetChecklist retrieves a Checklist pointed by clRef.
// It makes some call to GitHub to create a complete view of one checklist.
// Only this method can create prchecklist.Checklist.
func (u Usecase) GetChecklist(ctx context.Context, clRef prchecklist.ChecklistRef) (*prchecklist.Checklist, error) {
	pr, ctx, err := u.github.GetPullRequest(ctx, clRef, true)
	if err != nil {
		return nil, err
	}

	refs := u.mergedPullRequestRefs(pr)

	checklist := &prchecklist.Checklist{
		PullRequest: pr,
		Stage:       clRef.Stage,
		Items:       make([]*prchecklist.ChecklistItem, len(refs)),
		Config:      nil,
	}

	{
		g, ctx := errgroup.WithContext(ctx)
		for i, ref := range refs {
			i, ref := i, ref
			g.Go(func() error {
				featurePullReq, _, err := u.github.GetPullRequest(ctx, ref, false)
				if err != nil {
					return err
				}

				checklist.Items[i] = &prchecklist.ChecklistItem{
					PullRequest: featurePullReq,
					CheckedBy:   []prchecklist.GitHubUser{}, // filled up later
				}
				return nil
			})
		}

		if pr.ConfigBlobID != "" {
			g.Go(func() error {
				buf, err := u.github.GetBlob(ctx, clRef, pr.ConfigBlobID)
				if err != nil {
					return errors.Wrap(err, "github.GetBlob")
				}

				checklist.Config, err = u.loadConfig(buf)
				return err
			})
		}

		err = g.Wait()
		if err != nil {
			return nil, err
		}
	}

	// may move to before fetching feature pullreqs
	// for early return
	checks, err := u.coreRepo.GetChecks(ctx, clRef)
	if err != nil {
		return nil, err
	}

	log.Printf("%s: checks: %+v", clRef, checks)

	var s intsets.Sparse
	for _, userIDs := range checks {
		for _, id := range userIDs {
			s.Insert(id)
		}
	}

	users, err := u.coreRepo.GetUsers(ctx, s.AppendTo(nil))
	if err != nil {
		return nil, err
	}

	for _, item := range checklist.Items {
		for _, id := range checks[prchecklist.ChecksKeyFeatureNum(item.Number)] {
			item.CheckedBy = append(item.CheckedBy, users[id])
		}
	}

	return checklist, nil
}

func (u Usecase) loadConfig(buf []byte) (*prchecklist.ChecklistConfig, error) {
	var config prchecklist.ChecklistConfig
	err := yaml.Unmarshal(buf, &config)
	if err != nil {
		return nil, errors.Wrap(err, "yaml.Unmarshal")
	}

	if config.Notification.Events.OnCheck == nil {
		config.Notification.Events.OnCheck = []string{"default"}
	}

	if config.Notification.Events.OnComplete == nil {
		config.Notification.Events.OnComplete = []string{"default"}
	}

	if config.Notification.Events.OnRemove == nil {
		config.Notification.Events.OnRemove = []string{"default"}
	}

	if config.Notification.Events.OnUserComplete == nil {
		config.Notification.Events.OnUserComplete = []string{}
	}

	return &config, nil
}

// AddUser calls a repo to register the information of a user.
func (u Usecase) AddUser(ctx context.Context, user prchecklist.GitHubUser) error {
	return u.coreRepo.AddUser(ctx, user)
}

// AddCheck adds a check by the user for a checklist item for a feature pull reuquest number featNum, for the checklist pointed by clRef.
// On checking, it may send notifications according to the configuration on prchecklist.yml.
// NOTE: we may not need user, could receive only token (from ctx) for checking visiblities & gettting user info
func (u Usecase) AddCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) (*prchecklist.Checklist, error) {
	err := u.coreRepo.AddCheck(ctx, clRef, prchecklist.ChecksKeyFeatureNum(featNum), user)
	if err != nil {
		return nil, err
	}

	checklist, err := u.GetChecklist(ctx, clRef)
	if err != nil {
		return nil, err
	}

	// TODO: check item existence?
	go func(ctx context.Context) {
		// notify in sequence
		events := []notificationEvent{
			addCheckEvent{checklist: checklist, item: checklist.Item(featNum), user: user},
		}
		if checklist.UserCompleted(checklist.Item(featNum).User) {
			events = append(events, userCompleteEvent{checklist: checklist, user: user})
		}
		if checklist.Completed() {
			events = append(events, completeEvent{checklist: checklist})
		}
		for _, event := range events {
			err := u.notifyEvent(ctx, checklist, event)
			if err != nil {
				log.Printf("notifyEvent(%v): %s", event, err)
			}
		}
	}(prchecklist.NewContextWithValuesOf(ctx))

	return checklist, nil
}

// RemoveCheck removes a check from a checklist pointed by clRef.
func (u Usecase) RemoveCheck(ctx context.Context, clRef prchecklist.ChecklistRef, featNum int, user prchecklist.GitHubUser) (*prchecklist.Checklist, error) {
	// TODO: check featNum existence
	// NOTE: could receive only token (from ctx) and check visiblities & get user info
	err := u.coreRepo.RemoveCheck(ctx, clRef, prchecklist.ChecksKeyFeatureNum(featNum), user)
	if err != nil {
		return nil, err
	}

	cl, err := u.GetChecklist(ctx, clRef)
	if err != nil {
		return nil, err
	}

	go func(ctx context.Context, cl *prchecklist.Checklist) {
		events := []notificationEvent{
			removeCheckEvent{
				checklist: cl,
				item:      cl.Item(featNum),
				user:      user,
			},
		}
		for _, event := range events {
			err := u.notifyEvent(ctx, cl, event)
			if err != nil {
				log.Printf("notifyEvent(%v): %s", event, err)
			}
		}
	}(prchecklist.NewContextWithValuesOf(ctx), cl)

	return cl, nil
}

var rxMergeCommitMessage = regexp.MustCompile(`\AMerge pull request #(?P<number>\d+) `)

func (u Usecase) mergedPullRequestRefs(pr *prchecklist.PullRequest) []prchecklist.ChecklistRef {
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

// GetRecentPullRequests list recent pullrequests the user may be interested in.
// Crafted for the top page.
func (u Usecase) GetRecentPullRequests(ctx context.Context) (map[string][]*prchecklist.PullRequest, error) {
	return u.github.GetRecentPullRequests(ctx)
}

func (u Usecase) setRepositoryCompletedStatusAs(ctx context.Context, owner, repo, ref, state, stage, targetURL string) error {
	return u.github.SetRepositoryStatusAs(ctx, owner, repo, ref, fmt.Sprintf("prchecklist/%s/completed", stage), state, targetURL)
}
