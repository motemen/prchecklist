package repository

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/motemen/go-prchecklist"
)

func NewGitHub() *githubRepository {
	return &githubRepository{}
}

type githubRepository struct {
}

type graphqlResult struct {
	Repository struct {
		GraphQLParams struct {
			Owner string
			Repo  string
		}
		DefaultBranchRef struct {
			Name string
		}
		PullRequest struct {
			Title   string
			Number  int
			BaseRef struct {
				Name string
			}
			Commits struct {
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
			}
		}
	}
	RateLimit struct {
		Remaining int
	}
}

const pullRequestQuery = `query ($owner: String!, $repo: String!, $number: Int!, $commitsAfter: String) {
  repository(owner: $owner, name: $repo) {
    defaultBranchRef {
      name
    }
    pullRequest(number: $number) {
      title
      number
      baseRef {
        name
      }
      commits(first: 100, after: $commitsAfter) {
        edges {
          node {
            commit {
              message
            }
          }
        }
        pageInfo {
          hasNextPage
          endCursor
        }
        totalCount
      }
    }
  }
  rateLimit {
    remaining
  }
}
`

type graphQLResult struct {
	Data   interface{}
	Errors []struct {
		Message string
	}
}

func (r githubRepository) GetPullRequest(ctx context.Context, ref prchecklist.ChecklistRef) (*prchecklist.PullRequest, error) {
	var qr graphqlResult
	err := queryGraphQL(ctx, pullRequestQuery, map[string]interface{}{
		"owner":  ref.Owner,
		"repo":   ref.Repo,
		"number": ref.Number,
	}, &qr)
	if err != nil {
		return nil, err
	}

	graphqlResultToCommits := func(qr graphqlResult) []prchecklist.Commit {
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

		err := queryGraphQL(ctx, pullRequestQuery, map[string]interface{}{
			"owner":        ref.Owner,
			"repo":         ref.Repo,
			"number":       ref.Number,
			"commitsAfter": pageInfo.EndCursor,
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
