package repository

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/coocood/freecache"
	"github.com/motemen/go-graphql-query"

	"github.com/motemen/go-prchecklist"
)

const (
	cacheSize                         = 10 * 1024 * 1024
	cacheSecondsPullReqWithCommits    = 30
	cacheSecondsPullReqWithoutCommits = 300
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
		WithCommits bool `graphql:"$withCommits,notnull"`
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
			BaseRef struct {
				Name string
			}
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
			} `graphql:"@include(if: $withCommits)"`
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
	WithCommits  bool   `json:"withCommits"`
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

func (r githubRepository) GetPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, withCommits bool) (*prchecklist.PullRequest, error) {
	cacheKey := []byte(fmt.Sprintf("%s\000%v", ref.String(), withCommits))

	data, err := r.cache.Get(cacheKey)
	if data != nil {
		var pullReq prchecklist.PullRequest
		err := json.Unmarshal(data, &pullReq)
		if err == nil {
			return &pullReq, nil
		}

		log.Println("githubRepository.GetPullRequest(%q, %v): json.Unmarshal: %s", ref.String(), withCommits, err)
	}

	pullReq, err := r.getPullRequest(ctx, ref, withCommits)
	if err != nil {
		return nil, err
	}

	data, err = json.Marshal(&pullReq)
	if err != nil {
		log.Println("githubRepository.GetPullRequest(%q, %v): json.Marshal: %s", ref.String(), withCommits, err)
	} else {
		var cacheSeconds int
		if withCommits {
			cacheSeconds = cacheSecondsPullReqWithCommits
		} else {
			cacheSeconds = cacheSecondsPullReqWithoutCommits
		}

		err := r.cache.Set(cacheKey, data, cacheSeconds)
		if err != nil {
			log.Println("githubRepository.GetPullRequest(%q, %v): cache.Set: %s", ref.String(), withCommits, err)
		}
	}

	return pullReq, nil
}

func (r githubRepository) getPullRequest(ctx context.Context, ref prchecklist.ChecklistRef, withCommits bool) (*prchecklist.PullRequest, error) {
	var qr githubPullRequest
	err := queryGraphQL(ctx, pullRequestQuery, githubPullRequsetVars{
		Owner:       ref.Owner,
		Repo:        ref.Repo,
		Number:      ref.Number,
		WithCommits: withCommits,
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
		Title:     qr.Repository.PullRequest.Title,
		Body:      qr.Repository.PullRequest.Body,
		IsPrivate: qr.Repository.IsPrivate,
		Owner:     ref.Owner,
		Repo:      ref.Repo,
		Number:    ref.Number,
		Commits:   graphqlResultToCommits(qr),
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
			WithCommits:  true,
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
