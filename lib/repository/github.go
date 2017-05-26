package repository

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/motemen/go-graphql-query"
	"github.com/motemen/go-prchecklist"
)

func NewGitHub() *githubRepository {
	return &githubRepository{}
}

type githubRepository struct {
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
		DefaultBranchRef struct {
			Name string
		}
		PullRequest struct {
			GraphQLArguments struct {
				Number int `graphql:"$number,notnull"`
			}
			Title   string
			Number  int
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

	result := &prchecklist.PullRequest{
		Title:   qr.Repository.PullRequest.Title,
		Owner:   ref.Owner,
		Repo:    ref.Repo,
		Number:  ref.Number,
		Commits: graphqlResultToCommits(qr),
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

		result.Commits = append(result.Commits, graphqlResultToCommits(qr)...)
	}

	return result, nil
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
