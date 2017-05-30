package repository

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/coocood/freecache"
	"github.com/google/go-github/github"
	"github.com/motemen/go-graphql-query"

	"github.com/motemen/go-prchecklist"
	"github.com/pkg/errors"
)

const (
	cacheSize               = 10 * 1024 * 1024
	cacheSecondsPullReqMain = 30
	cacheSecondsPullReqFeat = 300
	cacheSecondsBlob        = 7 * 24 * 60 * 60 // infinity
)

func NewGitHub() *githubRepository {
	return &githubRepository{
		cache: freecache.NewCache(cacheSize),
	}
}

type githubRepository struct {
	cache *freecache.Cache
}

type githubPullRequest struct {
	GraphQLArguments struct {
		IsMain bool `graphql:"$isMain,notnull"`
	}
	Repository struct {
		GraphQLArguments struct {
			Owner string `graphql:"$owner,notnull"`
			Name  string `graphql:"$repo,notnull"`
		}
		IsPrivate        bool
		DefaultBranchRef struct {
			Name string
		}
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
			} `graphql:"@include(if: $isMain)"`
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
			} `graphql:"@include(if: $isMain)"`
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
	IsMain       bool   `json:"isMain"`
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
	cacheKey := []byte(fmt.Sprintf("blob\000%s\000%s", ref.String(), sha))

	if data, err := r.cache.Get(cacheKey); data != nil {
		return data, nil
	} else if err != nil && err != freecache.ErrNotFound {
		log.Printf("githubRepository.GetBlob: cache.Get: %s", err)
	}

	blob, err := r.getBlob(ctx, ref, sha)
	if err != nil {
		return nil, err
	}

	err = r.cache.Set(cacheKey, blob, cacheSecondsBlob)
	if err != nil {
		log.Printf("githubRepository.GetBlob: cache.Set: %s", err)
	}

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

func (r githubRepository) GetPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isMain bool) (*prchecklist.PullRequest, error) {
	cacheKey := []byte(fmt.Sprintf("pullRequest\000%s\000%v", ref.String(), isMain))

	data, err := r.cache.Get(cacheKey)
	if data != nil {
		var pullReq prchecklist.PullRequest
		err := json.Unmarshal(data, &pullReq)
		if err == nil {
			return &pullReq, nil
		}

		log.Println("githubRepository.GetPullRequest(%q, %v): json.Unmarshal: %s", ref.String(), isMain, err)
	}

	pullReq, err := r.getPullRequest(ctx, ref, isMain)
	if err != nil {
		return nil, err
	}

	data, err = json.Marshal(&pullReq)
	if err != nil {
		log.Println("githubRepository.GetPullRequest(%q, %v): json.Marshal: %s", ref.String(), isMain, err)
	} else {
		var cacheSeconds int
		if isMain {
			cacheSeconds = cacheSecondsPullReqMain
		} else {
			cacheSeconds = cacheSecondsPullReqFeat
		}

		err := r.cache.Set(cacheKey, data, cacheSeconds)
		if err != nil {
			log.Println("githubRepository.GetPullRequest(%q, %v): cache.Set: %s", ref.String(), isMain, err)
		}
	}

	return pullReq, nil
}

func (r githubRepository) getPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, isMain bool) (*prchecklist.PullRequest, error) {
	var qr githubPullRequest
	err := queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
		Owner:  ref.Owner,
		Repo:   ref.Repo,
		Number: ref.Number,
		IsMain: isMain,
	}, &qr)
	if err != nil {
		return nil, err
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
			IsMain:       isMain,
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
