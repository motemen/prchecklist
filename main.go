package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/gob"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/google/go-github/github"
	"github.com/gorilla/schema"
	"github.com/gorilla/sessions"
	"golang.org/x/oauth2"
)

func init() {
	gob.Register(&githubUser{})
}

const sessionName = "s"

const (
	sessionKeyOAuthState = "oauthState"
	sessionKeyGitHubUser = "githubUser"
)

type githubUser struct {
	Login     string
	AvatarURL string
	Token     *oauth2.Token
}

var (
	githubClientID     = os.Getenv("GITHUB_CLIENT_ID")
	githubClientSecret = os.Getenv("GITHUB_CLIENT_SECRET")
	sessionSecret      = os.Getenv("PRCHECKLIST_SESSION_SECRET")
)

var sessionStore = sessions.NewCookieStore([]byte(sessionSecret))

var githubEndpoint = oauth2.Endpoint{
	AuthURL:  "https://github.com/login/oauth/authorize",
	TokenURL: "https://github.com/login/oauth/access_token",
}

func main() {
	mux := http.NewServeMux()
	mux.Handle("/", httpHandler(handleIndex))
	mux.Handle("/auth", httpHandler(handleAuth))
	mux.Handle("/auth/callback", httpHandler(handleAuthCallback))
	mux.Handle("/auth/clear", httpHandler(handleAuthClear))
	mux.Handle("/api/checklist", httpHandler(handleAPIChecklist))
	err := http.ListenAndServe("localhost:7888", mux)
	log.Fatal(err)
}

type Checklist struct {
	PullRequest PullRequest
	Features    []PullRequest
}

type PullRequest struct {
	Title  string
	Number int
}

type httpError int

func (he httpError) Error() string {
	return http.StatusText(int(he))
}

type httpHandler func(w http.ResponseWriter, req *http.Request) error

func (h httpHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	err := h(w, req)
	if err != nil {
		log.Printf("ServeHTTP: %s (%#v)", err, err)

		status := http.StatusInternalServerError
		if he, ok := err.(httpError); ok {
			status = int(he)
		}
		http.Error(w, http.StatusText(status), status)
	}
}

func handleAuth(w http.ResponseWriter, req *http.Request) error {
	conf := &oauth2.Config{
		ClientID:     githubClientID,
		ClientSecret: githubClientSecret,
		Endpoint:     githubEndpoint,
		Scopes:       []string{"repo"},
	}

	sess, err := sessionStore.Get(req, sessionName)
	if err != nil {
		// return err
	}

	state, err := makeRandomString()
	if err != nil {
		return err
	}

	sess.Values[sessionKeyOAuthState] = state
	sessionStore.Save(req, w, sess)

	http.Redirect(w, req, conf.AuthCodeURL(state), http.StatusFound)

	return nil
}

func makeRandomString() (string, error) {
	buf := make([]byte, 16)
	_, err := rand.Read(buf)
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

func handleIndex(w http.ResponseWriter, req *http.Request) error {
	u, err := getAuthInfo(w, req)
	if err != nil {
		return err
	}

	return json.NewEncoder(w).Encode(u)
}

func handleAuthCallback(w http.ResponseWriter, req *http.Request) error {
	sess, err := sessionStore.Get(req, sessionName)
	if err != nil {
		// return err
	}

	code := req.URL.Query().Get("code")

	state := req.URL.Query().Get("state")
	if state != sess.Values[sessionKeyOAuthState] {
		return httpError(http.StatusBadRequest)
	}

	delete(sess.Values, sessionKeyOAuthState)

	ctx := context.Background()
	conf := &oauth2.Config{
		ClientID:     githubClientID,
		ClientSecret: githubClientSecret,
		Endpoint:     githubEndpoint,
	}
	token, err := conf.Exchange(ctx, code)
	if err != nil {
		return err
	}

	log.Printf("received token: %#v", token)

	client := github.NewClient(
		oauth2.NewClient(ctx, oauth2.StaticTokenSource(token)),
	)
	u, _, err := client.Users.Get(ctx, "")
	if err != nil {
		return err
	}

	user := &githubUser{
		Login:     u.GetLogin(),
		AvatarURL: u.GetAvatarURL(),
		Token:     token,
	}
	sess.Values[sessionKeyGitHubUser] = user

	log.Printf("user: %#v", user)

	err = sess.Save(req, w)
	if err != nil {
		return err
	}

	http.Redirect(w, req, "/", http.StatusFound)

	return nil
}

func handleAuthClear(w http.ResponseWriter, req *http.Request) error {
	sess, err := sessionStore.Get(req, sessionName)
	if err != nil {
		return err
	}

	delete(sess.Values, sessionKeyGitHubUser)

	return sess.Save(req, w)
}

func getAuthInfo(w http.ResponseWriter, req *http.Request) (*githubUser, error) {
	sess, err := sessionStore.Get(req, sessionName)
	if err != nil {
		// return nil, err
		return nil, nil
	}

	v, ok := sess.Values[sessionKeyGitHubUser]
	if !ok {
		return nil, nil
	}

	user, ok := v.(*githubUser)
	if !ok || user.Token == nil {
		delete(sess.Values, sessionKeyGitHubUser)
		return nil, sess.Save(req, w)
	}

	return user, nil
}

func handleAPIChecklist(w http.ResponseWriter, req *http.Request) error {
	u, err := getAuthInfo(w, req)
	if err != nil {
		return err
	}
	if u == nil {
		return httpError(http.StatusForbidden)
	}

	type inQuery struct {
		Owner  string
		Repo   string
		Number int
	}

	var in inQuery
	err = schema.NewDecoder().Decode(&in, req.URL.Query())
	if err != nil {
		return err
	}

	log.Printf("%v", in)

	type queryResult struct {
		Data struct {
			Repository struct {
				PullRequest struct {
					Title      string
					Number     int
					Repository struct {
						NameWithOwner string
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
	}

	query := fmt.Sprintf(`query {
  repository(owner: %q, name: %q) {
    pullRequest(number: %d) {
      title
      number
      repository {
        nameWithOwner
      }
      commits(first: 100) {
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
}`, in.Owner, in.Repo, in.Number)

	var buf bytes.Buffer
	err = json.NewEncoder(&buf).Encode(map[string]string{"query": query})
	if err != nil {
		return err
	}

	httpReq, err := http.NewRequest("POST", "https://api.github.com/graphql", &buf)
	if err != nil {
		return err
	}

	httpReq.Header.Set("Authorization", "token "+u.Token.AccessToken)

	client := http.Client{}
	resp, err := client.Do(httpReq)
	if err != nil {
		return err
	}

	var result queryResult
	defer resp.Body.Close()
	err = json.NewDecoder(resp.Body).Decode(&result)
	if err != nil {
		return err
	}

	log.Printf("%+v", result)

	return nil
}