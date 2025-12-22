package gateway

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/google/go-github/v31/github"
	graphqlquery "github.com/motemen/go-graphql-query"
	"github.com/patrickmn/go-cache"
	"github.com/pkg/errors"
	"golang.org/x/oauth2"

	"github.com/motemen/prchecklist/v2"
)

const (
	cacheDurationPullReqBase = 30 * time.Second
	cacheDurationPullReqFeat = 5 * time.Minute
	cacheDurationBlob        = cache.NoExpiration
)

var (
	githubClientID     string
	githubClientSecret string
	githubDomain       string
)

func getenv(key, def string) string {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	return v
}

func init() {
	flag.StringVar(&githubClientID, "github-client-id", os.Getenv("GITHUB_CLIENT_ID"), "GitHub client ID (GITHUB_CLIENT_ID)")
	flag.StringVar(&githubClientSecret, "github-client-secret", os.Getenv("GITHUB_CLIENT_SECRET"), "GitHub client secret (GITHUB_CLIENT_SECRET)")
	flag.StringVar(&githubDomain, "github-domain", getenv("GITHUB_DOMAIN", "github.com"), "GitHub domain (GITHUB_DOMAIN)")
}

// NewGitHub creates a new GitHub gateway.
func NewGitHub() (*githubGateway, error) {
	if (githubClientID == "" || githubClientSecret == "") && os.Getenv("PRCHECKLIST_TEST_GITHUB_TOKEN") == "" {
		return nil, errors.New("gateway/github: both GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET must be set")
	}

	var githubEndpoint = oauth2.Endpoint{
		AuthURL:  "https://" + githubDomain + "/login/oauth/authorize",
		TokenURL: "https://" + githubDomain + "/login/oauth/access_token",
	}

	return &githubGateway{
		cache: cache.New(30*time.Second, 10*time.Minute),
		oauth2Config: &oauth2.Config{
			ClientID:     githubClientID,
			ClientSecret: githubClientSecret,
			Endpoint:     githubEndpoint,
			Scopes:       []string{"repo"},
		},
		domain: githubDomain,
	}, nil
}

type githubGateway struct {
	cache        *cache.Cache
	oauth2Config *oauth2.Config
	domain       string
}

type githubPullRequest struct {
	GraphQLArguments struct {
		IsBase bool `graphql:"$isBase,notnull"`
	}
	Repository *struct {
		GraphQLArguments struct {
			Owner string `graphql:"$owner,notnull"`
			Name  string `graphql:"$repo,notnull"`
		}
		IsPrivate   bool
		PullRequest struct {
			GraphQLArguments struct {
				Number int `graphql:"$number,notnull"`
			}
			Title  string
			Number int
			Body   string
			URL    string
			Author struct {
				Login string
			}
			Assignees struct {
				Edges []struct {
					Node struct {
						Login string
					}
				}
			} `graphql:"(first: 1)"`
			BaseRef struct {
				Name string
			}
			HeadRef struct {
				Target struct {
					Tree struct {
						Entries []struct {
							Name string
							Oid  string
							Type string
						}
					} `graphql:"... on Commit"`
				}
			} `graphql:"@include(if: $isBase)"`
			Commits struct {
				GraphQLArguments struct {
					First int    `graphql:"100"`
					After string `graphql:"$commitsAfter"`
				}
				Edges []struct {
					Node struct {
						Commit struct {
							Message string
							Oid     string
						}
					}
				}
				PageInfo struct {
					HasNextPage bool
					EndCursor   string
				}
				TotalCount int
			} `graphql:"@include(if: $isBase)"`
		}
	}
}

// Deprecated: githubRecentPullRequests is replaced by paginated queries
type githubRecentPullRequests struct {
	Viewer struct {
		Repositories struct {
			Edges []struct {
				Node struct {
					NameWithOwner string
					PullRequests  struct {
						Edges []struct {
							Node struct {
								Title  string
								Number int
								URL    string
							}
						}
					} `graphql:"(first: 5, orderBy: {field: UPDATED_AT, direction: DESC}, baseRefName: \"master\")"`
				}
			}
		} `graphql:"(first: 10, orderBy: {field: PUSHED_AT, direction: DESC}, affiliations: [OWNER, ORGANIZATION_MEMBER, COLLABORATOR])"`
	}
}

// githubViewerRepositories is used for paginating through viewer's repositories
type githubViewerRepositories struct {
	GraphQLArguments struct {
		After string `graphql:"$reposAfter"`
	}
	Viewer struct {
		Repositories struct {
			Edges []struct {
				Node struct {
					NameWithOwner string
					Owner         struct {
						Login string
					}
					Name string
				}
			}
			PageInfo struct {
				HasNextPage bool
				EndCursor   string
			}
		} `graphql:"(first: 100, after: $reposAfter, orderBy: {field: PUSHED_AT, direction: DESC}, affiliations: [OWNER, ORGANIZATION_MEMBER, COLLABORATOR])"`
	}
}

// githubRepoPullRequests is used for paginating through a repository's pull requests
type githubRepoPullRequests struct {
	GraphQLArguments struct {
		Owner      string `graphql:"$owner,notnull"`
		Name       string `graphql:"$name,notnull"`
		PullsAfter string `graphql:"$pullsAfter"`
	}
	Repository *struct {
		PullRequests struct {
			Edges []struct {
				Node struct {
					Title  string
					Number int
					URL    string
				}
			}
			PageInfo struct {
				HasNextPage bool
				EndCursor   string
			}
		} `graphql:"(first: 100, after: $pullsAfter, orderBy: {field: UPDATED_AT, direction: DESC}, baseRefName: \"master\")"`
	}
}

type githubPullRequsetVars struct {
	Owner        string `json:"owner"`
	Repo         string `json:"repo"`
	Number       int    `json:"number"`
	IsBase       bool   `json:"isBase"`
	CommitsAfter string `json:"commitsAfter,omitempty"`
}

type viewerRepositoriesVars struct {
	ReposAfter string `json:"reposAfter,omitempty"`
}

type repoPullRequestsVars struct {
	Owner      string `json:"owner"`
	Name       string `json:"name"`
	PullsAfter string `json:"pullsAfter,omitempty"`
}

type graphQLResult struct {
	Data   interface{}
	Errors []struct {
		Message string
	}
}

var (
	pullRequestQuery             string
	recentPullRequestsQuery      string // Deprecated: replaced by paginated queries
	viewerRepositoriesQuery      string
	repositoryPullRequestsQuery  string
)

func mustBuildGraphQLQuery(q interface{}) []byte {
	b, err := graphqlquery.Build(q)
	if err != nil {
		panic(err)
	}

	return b
}

func init() {
	pullRequestQuery = string(mustBuildGraphQLQuery(&githubPullRequest{}))
	recentPullRequestsQuery = string(mustBuildGraphQLQuery(&githubRecentPullRequests{}))
	viewerRepositoriesQuery = string(mustBuildGraphQLQuery(&githubViewerRepositories{}))
	repositoryPullRequestsQuery = string(mustBuildGraphQLQuery(&githubRepoPullRequests{}))
}

func (g githubGateway) GetBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error) {
	cacheKey := fmt.Sprintf("blob\000%s\000%s", ref.String(), sha)

	if data, ok := g.cache.Get(cacheKey); ok {
		if blob, ok := data.([]byte); ok {
			return blob, nil
		}
	}

	blob, err := g.getBlob(ctx, ref, sha)
	if err != nil {
		return nil, err
	}

	g.cache.Set(cacheKey, blob, cacheDurationBlob)

	return blob, nil
}

func (g githubGateway) getBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error) {
	gh, err := g.newGitHubClient(prchecklist.ContextClient(ctx))
	if err != nil {
		return nil, err
	}

	blob, _, err := gh.Git.GetBlob(ctx, ref.Owner, ref.Repo, sha)
	if err != nil {
		return nil, err
	}

	content := blob.GetContent()
	if enc := blob.GetEncoding(); enc != "base64" {
		return nil, errors.Errorf("unknown encoding: %q", enc)
	}

	buf, err := base64.StdEncoding.DecodeString(content)
	return buf, errors.Wrap(err, "base64")
}

var contextKeyRepoAccessRight = &struct{ key string }{"repoRight"}

type repoRight struct {
	owner string
	repo  string
}

func contextHasRepoAccessRight(ctx context.Context, ref prchecklist.ChecklistRef) bool {
	if g, ok := ctx.Value(contextKeyRepoAccessRight).(repoRight); ok {
		return g.owner == ref.Owner && g.repo == ref.Repo
	}
	return false
}

func contextWithRepoAccessRight(ctx context.Context, ref prchecklist.ChecklistRef) context.Context {
	return context.WithValue(ctx, contextKeyRepoAccessRight, repoRight{owner: ref.Owner, repo: ref.Repo})
}

func (g githubGateway) GetPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isBase bool) (*prchecklist.PullRequest, context.Context, error) {
	cacheKey := fmt.Sprintf("pullRequest\000%s\000%v", ref.String(), isBase)

	if data, ok := g.cache.Get(cacheKey); ok {
		if pullReq, ok := data.(*prchecklist.PullRequest); ok {
			if pullReq.IsPrivate && !contextHasRepoAccessRight(ctx, ref) {
				// something's wrong!
			} else {
				return pullReq, ctx, nil
			}
		}
	}

	pullReq, err := g.getPullRequest(ctx, ref, isBase)
	if err != nil {
		return nil, ctx, err
	}
	if isBase && pullReq.IsPrivate {
		// Do not cache result if the pull request is private
		// and isBase is true to check if the visitor has rights to
		// read the repo.
		// If isBase is false, we don't need to check vititor's rights
		// because GetPullRequest() with truthy isBase must be called before falsy one.
		return pullReq, contextWithRepoAccessRight(ctx, ref), nil
	}

	var cacheDuration time.Duration
	if isBase {
		cacheDuration = cacheDurationPullReqBase
	} else {
		cacheDuration = cacheDurationPullReqFeat
	}

	g.cache.Set(cacheKey, pullReq, cacheDuration)

	return pullReq, contextWithRepoAccessRight(ctx, ref), nil
}

// GetRecentPullRequests retrieves all pull requests with baseRefName "master"
// from all repositories the viewer has access to.
// This function uses cursor-based pagination to fetch all repositories and all PRs,
// removing the previous hard-coded limits of 10 repositories and 5 PRs per repository.
//
// Performance consideration: For users with access to many repositories or PRs,
// this can result in many API calls. If needed, pagination limits can be made
// configurable via environment variables (e.g., MAX_REPOS, MAX_PRS_PER_REPO).
func (g githubGateway) GetRecentPullRequests(ctx context.Context) (map[string][]*prchecklist.PullRequest, error) {
	pullRequests := map[string][]*prchecklist.PullRequest{}

	// Paginate through all viewer's repositories
	reposAfter := ""
	for {
		var reposResult githubViewerRepositories
		err := g.queryGraphQL(ctx, viewerRepositoriesQuery, viewerRepositoriesVars{
			ReposAfter: reposAfter,
		}, &reposResult)
		if err != nil {
			return nil, err
		}

		// For each repository, paginate through all pull requests
		for _, repoEdge := range reposResult.Viewer.Repositories.Edges {
			repo := repoEdge.Node
			
			// Paginate through pull requests for this repository
			pullsAfter := ""
			var repoPRs []*prchecklist.PullRequest
			for {
				var prsResult githubRepoPullRequests
				err := g.queryGraphQL(ctx, repositoryPullRequestsQuery, repoPullRequestsVars{
					Owner:      repo.Owner.Login,
					Name:       repo.Name,
					PullsAfter: pullsAfter,
				}, &prsResult)
				if err != nil {
					return nil, err
				}

				// Check if repository was found
				if prsResult.Repository == nil {
					break
				}

				// Collect pull requests from this page
				for _, prEdge := range prsResult.Repository.PullRequests.Edges {
					pr := prEdge.Node
					repoPRs = append(repoPRs, &prchecklist.PullRequest{
						Title:  pr.Title,
						URL:    pr.URL,
						Number: pr.Number,
					})
				}

				// Check if there are more pages
				if !prsResult.Repository.PullRequests.PageInfo.HasNextPage {
					break
				}
				pullsAfter = prsResult.Repository.PullRequests.PageInfo.EndCursor
			}

			// Add to result if there are any PRs for this repo
			if len(repoPRs) > 0 {
				pullRequests[repo.NameWithOwner] = repoPRs
			}
		}

		// Check if there are more repository pages
		if !reposResult.Viewer.Repositories.PageInfo.HasNextPage {
			break
		}
		reposAfter = reposResult.Viewer.Repositories.PageInfo.EndCursor
	}

	return pullRequests, nil
}

func (g githubGateway) getPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isBase bool) (*prchecklist.PullRequest, error) {
	var qr githubPullRequest
	err := g.queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
		Owner:  ref.Owner,
		Repo:   ref.Repo,
		Number: ref.Number,
		IsBase: isBase,
	}, &qr)
	if err != nil {
		return nil, err
	}
	if qr.Repository == nil {
		return nil, errors.Errorf("could not retrieve repo/pullreq")
	}

	graphqlResultToCommits := func(qr githubPullRequest) []prchecklist.Commit {
		commits := make([]prchecklist.Commit, len(qr.Repository.PullRequest.Commits.Edges))
		for i, e := range qr.Repository.PullRequest.Commits.Edges {
			commits[i] = prchecklist.Commit{Message: e.Node.Commit.Message, Oid: e.Node.Commit.Oid}
		}
		return commits
	}

	pullReq := &prchecklist.PullRequest{
		URL:       qr.Repository.PullRequest.URL,
		Title:     qr.Repository.PullRequest.Title,
		Body:      qr.Repository.PullRequest.Body,
		IsPrivate: qr.Repository.IsPrivate,
		Owner:     ref.Owner,
		Repo:      ref.Repo,
		Number:    ref.Number,
		Commits:   graphqlResultToCommits(qr),
		User: prchecklist.GitHubUserSimple{
			Login: qr.Repository.PullRequest.Author.Login,
		},
	}

	// prefer assignee
	if len(qr.Repository.PullRequest.Assignees.Edges) > 0 {
		pullReq.User.Login = qr.Repository.PullRequest.Assignees.Edges[0].Node.Login
	}

	for _, e := range qr.Repository.PullRequest.HeadRef.Target.Tree.Entries {
		if e.Name == "prchecklist.yml" && e.Type == "blob" {
			pullReq.ConfigBlobID = e.Oid
			break
		}
	}

	for {
		pageInfo := qr.Repository.PullRequest.Commits.PageInfo
		if !pageInfo.HasNextPage {
			break
		}

		err := g.queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
			Owner:        ref.Owner,
			Repo:         ref.Repo,
			Number:       ref.Number,
			IsBase:       isBase,
			CommitsAfter: pageInfo.EndCursor,
		}, &qr)
		if err != nil {
			return nil, err
		}

		pullReq.Commits = append(pullReq.Commits, graphqlResultToCommits(qr)...)
	}

	return pullReq, nil
}

func (g githubGateway) graphqlEndpoint() string {
	if g.domain == "github.com" {
		return "https://api.github.com/graphql"
	}

	return "https://" + g.domain + "/api/graphql"
}

func (g githubGateway) queryGraphQL(ctx context.Context, query string, variables interface{}, value interface{}) error {
	client := prchecklist.ContextClient(ctx)

	varBytes, err := json.Marshal(variables)

	var buf bytes.Buffer
	err = json.NewEncoder(&buf).Encode(map[string]string{"query": query, "variables": string(varBytes)})
	if err != nil {
		return err
	}

	req, err := http.NewRequest("POST", g.graphqlEndpoint(), &buf)
	if err != nil {
		return err
	}

	resp, err := client.Do(req)
	if err != nil {
		return err
	}

	result := graphQLResult{
		Data: value,
	}

	defer resp.Body.Close()
	err = json.NewDecoder(resp.Body).Decode(&result)
	if err != nil {
		return err
	}

	if len(result.Errors) > 0 {
		return fmt.Errorf("GraphQL error: %v", result.Errors)
	}

	return nil
}

func (g githubGateway) AuthCodeURL(state string, redirectURI *url.URL) string {
	opts := []oauth2.AuthCodeOption{}
	if redirectURI != nil {
		opts = append(opts, oauth2.SetAuthURLParam("redirect_uri", redirectURI.String()))
	}
	return g.oauth2Config.AuthCodeURL(state, opts...)
}

func (g githubGateway) newGitHubClient(base *http.Client) (*github.Client, error) {
	client := github.NewClient(base)
	if g.domain != "github.com" {
		var err error
		// TODO(motemen): parsing url can be done earlier
		client.BaseURL, err = url.Parse("https://" + g.domain + "/api/v3/")
		if err != nil {
			return nil, err
		}
	}
	return client, nil
}

func (g githubGateway) AuthenticateUser(ctx context.Context, code string) (*prchecklist.GitHubUser, error) {
	token, err := g.oauth2Config.Exchange(ctx, code)
	if err != nil {
		return nil, err
	}

	return g.GetUserFromToken(ctx, token)
}

func (g githubGateway) GetUserFromToken(ctx context.Context, token *oauth2.Token) (*prchecklist.GitHubUser, error) {
	client, err := g.newGitHubClient(
		oauth2.NewClient(ctx, oauth2.StaticTokenSource(token)),
	)
	if err != nil {
		return nil, err
	}

	u, _, err := client.Users.Get(ctx, "")
	if err != nil {
		return nil, err
	}

	return &prchecklist.GitHubUser{
		ID:        int(u.GetID()),
		Login:     u.GetLogin(),
		AvatarURL: u.GetAvatarURL(),
		Token:     token,
	}, nil
}

func (g githubGateway) SetRepositoryStatusAs(ctx context.Context, owner, repo, ref, contextName, state, targetURL string) error {
	gh, err := g.newGitHubClient(prchecklist.ContextClient(ctx))
	if err != nil {
		return err
	}

	status := &github.RepoStatus{
		State:     &state,
		Context:   &contextName,
		TargetURL: &targetURL,
	}
	if _, _, err = gh.Repositories.CreateStatus(ctx, owner, repo, ref, status); err != nil {
		return err
	}

	return nil
}
