package repository

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/go-github/github"
	"github.com/motemen/go-graphql-query"
	"github.com/patrickmn/go-cache"

	"github.com/motemen/go-prchecklist"
	"github.com/pkg/errors"
)

const (
	cacheDurationPullReqBase = 30 * time.Second
	cacheDurationPullReqFeat = 5 * time.Minute
	cacheDurationBlob        = cache.NoExpiration
)

func NewGitHub() *githubRepository {
	return &githubRepository{
		cache: cache.New(30*time.Second, 10*time.Minute),
	}
}

type githubRepository struct {
	cache *cache.Cache
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
			Title   string
			Number  int
			Body    string
			URL     string
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
	RateLimit struct {
		Remaining int
	}
}

type githubPullRequsetVars struct {
	Owner        string `json:"owner"`
	Repo         string `json:"repo"`
	Number       int    `json:"number"`
	IsBase       bool   `json:"isBase"`
	CommitsAfter string `json:"commitsAfter,omitempty"`
}

type graphQLResult struct {
	Data   interface{}
	Errors []struct {
		Message string
	}
}

var pullRequestQuery string

func init() {
	b, err := graphqlquery.Build(&githubPullRequest{})
	if err != nil {
		panic(err)
	}
	pullRequestQuery = string(b)
}

func (r githubRepository) GetBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error) {
	cacheKey := fmt.Sprintf("blob\000%s\000%s", ref.String(), sha)

	if data, ok := r.cache.Get(cacheKey); ok {
		if blob, ok := data.([]byte); ok {
			return blob, nil
		}
	}

	blob, err := r.getBlob(ctx, ref, sha)
	if err != nil {
		return nil, err
	}

	r.cache.Set(cacheKey, blob, cacheDurationBlob)

	return blob, nil
}

func (r githubRepository) getBlob(ctx context.Context, ref prchecklist.ChecklistRef, sha string) ([]byte, error) {
	gh := github.NewClient(prchecklist.ContextClient(ctx))
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
	if r, ok := ctx.Value(contextKeyRepoAccessRight).(repoRight); ok {
		return r.owner == ref.Owner && r.repo == ref.Repo
	}
	return false
}

func contextWithRepoAccessRight(ctx context.Context, ref prchecklist.ChecklistRef) context.Context {
	return context.WithValue(ctx, contextKeyRepoAccessRight, repoRight{owner: ref.Owner, repo: ref.Repo})
}

func (r githubRepository) GetPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isBase bool) (*prchecklist.PullRequest, context.Context, error) {
	cacheKey := fmt.Sprintf("pullRequest\000%s\000%v", ref.String(), isBase)

	if data, ok := r.cache.Get(cacheKey); ok {
		if pullReq, ok := data.(*prchecklist.PullRequest); ok {
			if pullReq.IsPrivate && !contextHasRepoAccessRight(ctx, ref) {
				// something's wrong!
			} else {
				return pullReq, ctx, nil
			}
		}
	}

	pullReq, err := r.getPullRequest(ctx, ref, isBase)
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

	r.cache.Set(cacheKey, pullReq, cacheDuration)

	return pullReq, contextWithRepoAccessRight(ctx, ref), nil
}

func (r githubRepository) getPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isBase bool) (*prchecklist.PullRequest, error) {
	var qr githubPullRequest
	err := queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
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
			commits[i] = prchecklist.Commit{Message: e.Node.Commit.Message}
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

		err := queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
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

func queryGraphQL(ctx context.Context, query string, variables interface{}, value interface{}) error {
	client := prchecklist.ContextClient(ctx)

	varBytes, err := json.Marshal(variables)

	var buf bytes.Buffer
	err = json.NewEncoder(&buf).Encode(map[string]string{"query": query, "variables": string(varBytes)})
	if err != nil {
		return err
	}

	req, err := http.NewRequest("POST", "https://api.github.com/graphql", &buf)
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
