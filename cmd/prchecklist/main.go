package main

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/gob"
	"encoding/json"
	"log"
	"net/http"
	"os"

	"github.com/google/go-github/github"
	"github.com/gorilla/schema"
	"github.com/gorilla/sessions"
	_ "github.com/motemen/go-loghttp/global"
	"golang.org/x/oauth2"

	"github.com/motemen/go-prchecklist"
	"github.com/motemen/go-prchecklist/lib/repository"
	"github.com/motemen/go-prchecklist/lib/usecase"
)

var app *usecase.Usecase

func init() {
	gob.Register(&prchecklist.GitHubUser{})

	app = usecase.New(
		repository.NewGitHub(),
	)
}

const sessionName = "s"

const (
	sessionKeyOAuthState = "oauthState"
	sessionKeyGitHubUser = "prchecklist.GitHubUser"
)

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

func renderJSON(w http.ResponseWriter, v interface{}) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}

	w.Header().Add("Content-Type", "application/json")
	w.Write(b)
	return nil
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
		return err
	}

	state := req.URL.Query().Get("state")
	if state != sess.Values[sessionKeyOAuthState] {
		return httpError(http.StatusBadRequest)
	}

	delete(sess.Values, sessionKeyOAuthState)

	ctx := req.Context()

	conf := &oauth2.Config{
		ClientID:     githubClientID,
		ClientSecret: githubClientSecret,
		Endpoint:     githubEndpoint,
	}

	code := req.URL.Query().Get("code")
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

	user := &prchecklist.GitHubUser{
		ID:        u.GetID(),
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

func getAuthInfo(w http.ResponseWriter, req *http.Request) (*prchecklist.GitHubUser, error) {
	sess, err := sessionStore.Get(req, sessionName)
	if err != nil {
		// return nil, err
		return nil, nil
	}

	v, ok := sess.Values[sessionKeyGitHubUser]
	if !ok {
		return nil, nil
	}

	user, ok := v.(*prchecklist.GitHubUser)
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

	ctx := req.Context()
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, u.HTTPClient(ctx))

	cl, err := app.GetChecklist(ctx, prchecklist.ChecklistRef{
		Owner:  in.Owner,
		Repo:   in.Repo,
		Number: in.Number,
	})
	if err != nil {
		return err
	}

	return renderJSON(w, cl)
}
