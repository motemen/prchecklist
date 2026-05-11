package gateway

import (
	"context"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/oauth2"

	"github.com/motemen/prchecklist/v2"
)

func TestGitHub_GetPullRequest(t *testing.T) {
	token := os.Getenv("PRCHECKLIST_TEST_GITHUB_TOKEN")
	if token == "" {
		t.Skipf("PRCHECKLIST_TEST_GITHUB_TOKEN not set")
	}

	github, err := NewGitHub()
	assert.NoError(t, err)

	ctx := context.Background()
	cli := oauth2.NewClient(
		ctx,
		oauth2.StaticTokenSource(&oauth2.Token{AccessToken: token}),
	)
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, cli)
	_, _, err = github.GetPullRequest(ctx, prchecklist.ChecklistRef{
		Owner:  "motemen",
		Repo:   "test-repository",
		Number: 2,
	}, true)
	assert.NoError(t, err)
}

func TestGitHub_GetPullRequest_UsesCompareCommitsWhenTooManyCommits(t *testing.T) {
	mux := http.NewServeMux()

	randomRefName := func() string {
		const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		seededRand := rand.New(rand.NewSource(time.Now().UnixNano()))
		b := make([]byte, 20)
		for i := range b {
			b[i] = charset[seededRand.Intn(len(charset))]
		}
		return string(b)
	}

	randomBaseRef := randomRefName()
	randomBaseSHA := randomRefName()

	mux.HandleFunc("/api/graphql", func(w http.ResponseWriter, r *http.Request) {
		res := map[string]interface{}{
			"data": map[string]interface{}{
				"repository": map[string]interface{}{
					"isPrivate": false,
					"pullRequest": map[string]interface{}{
						"url":    "http://example.com/1",
						"title":  "title",
						"number": 1,
						"body":   "body",
						"author": map[string]interface{}{
							"login": "author",
						},
						"assignees": map[string]interface{}{
							"edges": []interface{}{},
						},
						"baseRef": map[string]interface{}{
							"name": randomBaseRef,
						},
						"headRef": map[string]interface{}{
							"target": map[string]interface{}{
								"tree": map[string]interface{}{
									"entries": []interface{}{},
								},
							},
						},
						"commits": map[string]interface{}{
							"totalCount": 300,
							"edges": []interface{}{
								map[string]interface{}{
									"node": map[string]interface{}{
										"commit": map[string]interface{}{
											"message": "graphql commit",
											"oid":     "abc",
										},
									},
								},
							},
							"pageInfo": map[string]interface{}{
								"hasNextPage": false,
								"endCursor":   "",
							},
						},
					},
				},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(res)
	})

	mux.HandleFunc("/api/v3/repos/o/r/pulls/1", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(fmt.Sprintf(`{
			"commits": 300,
			"head": {"sha": "headsha"},
			"base": {"ref": %q, "sha": %q}
		}`, randomBaseRef, randomBaseSHA)))
	})

	mux.HandleFunc("/api/v3/repos/o/r/compare/", func(w http.ResponseWriter, r *http.Request) {

		// Parse query parameters to check for pagination
		page := 1
		if pageStr := r.URL.Query().Get("page"); pageStr != "" {
			_, _ = fmt.Sscanf(pageStr, "%d", &page)
		}

		// Return different commits for each page
		var commits []map[string]interface{}
		if page == 1 {
			// First page: 250 commits (full page)
			for i := 1; i <= 250; i++ {
				commits = append(commits, map[string]interface{}{
					"sha": fmt.Sprintf("c%d", i),
					"commit": map[string]interface{}{
						"message": fmt.Sprintf("feature commit %d", i),
					},
				})
			}
		} else if page == 2 {
			// Second page: 2 commits (partial page - indicates last page)
			commits = []map[string]interface{}{
				{
					"sha": "c251",
					"commit": map[string]interface{}{
						"message": "feature commit 251",
					},
				},
				{
					"sha": "c252",
					"commit": map[string]interface{}{
						"message": "feature commit 252",
					},
				},
			}
		}

		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"commits": commits,
		})
	})

	ts := httptest.NewTLSServer(mux)
	defer ts.Close()

	u, _ := url.Parse(ts.URL)
	g := githubGateway{
		domain: u.Host,
	}

	ctx := context.Background()
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, ts.Client())

	ref := prchecklist.ChecklistRef{Owner: "o", Repo: "r", Number: 1}
	pr, err := g.getPullRequest(ctx, ref, true)

	require.NoError(t, err)
	require.NotNil(t, pr)
	require.Len(t, pr.Commits, 252)
	assert.Equal(t, "feature commit 1", pr.Commits[0].Message)
	assert.Equal(t, "feature commit 251", pr.Commits[250].Message)
	assert.Equal(t, "feature commit 252", pr.Commits[251].Message)
}

func TestGitHub_GetPullRequest_FallsBackToGraphQLCommitsWhenCompareCommitsIsEmpty(t *testing.T) {
	mux := http.NewServeMux()

	randomRefName := func() string {
		const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		seededRand := rand.New(rand.NewSource(time.Now().UnixNano()))
		b := make([]byte, 20)
		for i := range b {
			b[i] = charset[seededRand.Intn(len(charset))]
		}
		return string(b)
	}

	randomBaseRef := randomRefName()
	randomBaseSHA := randomRefName()
	compareCalled := false

	mux.HandleFunc("/api/graphql", func(w http.ResponseWriter, r *http.Request) {
		res := map[string]interface{}{
			"data": map[string]interface{}{
				"repository": map[string]interface{}{
					"isPrivate": false,
					"pullRequest": map[string]interface{}{
						"url":    "http://example.com/1",
						"title":  "title",
						"number": 1,
						"body":   "body",
						"author": map[string]interface{}{
							"login": "author",
						},
						"assignees": map[string]interface{}{
							"edges": []interface{}{},
						},
						"baseRef": map[string]interface{}{
							"name": randomBaseRef,
						},
						"headRef": map[string]interface{}{
							"target": map[string]interface{}{
								"tree": map[string]interface{}{
									"entries": []interface{}{},
								},
							},
						},
						"commits": map[string]interface{}{
							"totalCount": 300,
							"edges": []interface{}{
								map[string]interface{}{
									"node": map[string]interface{}{
										"commit": map[string]interface{}{
											"message": "graphql commit",
											"oid":     "abc",
										},
									},
								},
							},
							"pageInfo": map[string]interface{}{
								"hasNextPage": false,
								"endCursor":   "",
							},
						},
					},
				},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(res)
	})

	mux.HandleFunc("/api/v3/repos/o/r/pulls/1", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(fmt.Sprintf(`{
			"commits": 300,
			"head": {"sha": "headsha"},
			"base": {"ref": %q, "sha": %q}
		}`, randomBaseRef, randomBaseSHA)))
	})

	mux.HandleFunc("/api/v3/repos/o/r/compare/", func(w http.ResponseWriter, r *http.Request) {
		compareCalled = true
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"commits": []interface{}{},
		})
	})

	ts := httptest.NewTLSServer(mux)
	defer ts.Close()

	u, _ := url.Parse(ts.URL)
	g := githubGateway{
		domain: u.Host,
	}

	ctx := context.Background()
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, ts.Client())

	ref := prchecklist.ChecklistRef{Owner: "o", Repo: "r", Number: 1}
	pr, err := g.getPullRequest(ctx, ref, true)

	require.NoError(t, err)
	require.True(t, compareCalled)
	require.NotNil(t, pr)
	require.Len(t, pr.Commits, 1)
	assert.Equal(t, "graphql commit", pr.Commits[0].Message)
	assert.Equal(t, "abc", pr.Commits[0].Oid)
}
