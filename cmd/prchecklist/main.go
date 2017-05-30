package main

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/gob"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/google/go-github/github"
	"github.com/gorilla/mux"
	"github.com/gorilla/schema"
	"github.com/gorilla/sessions"
	_ "github.com/motemen/go-loghttp/global"
	"github.com/urfave/negroni"
	"golang.org/x/oauth2"

	"github.com/motemen/go-prchecklist"
	"github.com/motemen/go-prchecklist/lib/repository"
	"github.com/motemen/go-prchecklist/lib/usecase"
)

var app *usecase.Usecase

var (
	githubClientID     = os.Getenv("GITHUB_CLIENT_ID")
	githubClientSecret = os.Getenv("GITHUB_CLIENT_SECRET")
	sessionSecret      = os.Getenv("PRCHECKLIST_SESSION_SECRET")
	datasource         = getenv("PRCHECKLIST_DATASOURCE", "bolt:./prchecklist.db")
	addr               string
)

func getenv(key, def string) string {
	v := os.Getenv(key)
	if v != "" {
		return v
	}

	return def
}

func init() {
	gob.Register(&prchecklist.GitHubUser{})

	flag.StringVar(&githubClientID, "github-client-id", os.Getenv("GITHUB_CLIENT_ID"), "GitHub client ID")
	flag.StringVar(&githubClientSecret, "github-client-secret", os.Getenv("GITHUB_CLIENT_SECRET"), "GitHub client secret")
	flag.StringVar(&sessionSecret, "session-secret", os.Getenv("PRCHECKLIST_SESSION_SECRET"), "session secret")
	flag.StringVar(&datasource, "datasource", datasource, "database source name")
	flag.StringVar(&addr, "listen", "localhost:7888", "`address` to listen")
}

const sessionName = "s"

const (
	sessionKeyOAuthState = "oauthState"
	sessionKeyGitHubUser = "prchecklist.GitHubUser"
)

var sessionStore sessions.Store

var githubEndpoint = oauth2.Endpoint{
	AuthURL:  "https://github.com/login/oauth/authorize",
	TokenURL: "https://github.com/login/oauth/access_token",
}

func main() {
	flag.Parse()

	coreRepo, err := repository.NewBoltCore(datasource[len("bolt:"):])
	if err != nil {
		log.Fatal(err)
	}

	app = usecase.New(
		repository.NewGitHub(),
		coreRepo,
	)

	cookieStore := sessions.NewCookieStore([]byte(sessionSecret))
	cookieStore.Options = &sessions.Options{
		HttpOnly: true,
	}
	sessionStore = cookieStore

	router := mux.NewRouter()
	router.Handle("/", httpHandler(handleIndex))
	router.Handle("/auth", httpHandler(handleAuth))
	router.Handle("/auth/callback", httpHandler(handleAuthCallback))
	router.Handle("/auth/clear", httpHandler(handleAuthClear))
	router.Handle("/api/checklist", httpHandler(handleAPIChecklist))
	router.Handle("/api/check", httpHandler(handleAPICheck)).Methods("PUT", "DELETE")
	router.Handle("/{owner}/{repo}/pull/{number}", httpHandler(handleChecklist))
	router.Handle("/{owner}/{repo}/pull/{number}/{stage}", httpHandler(handleChecklist))

	n := negroni.New(negroni.NewStatic(http.Dir("./static")))
	n.UseHandler(router)

	log.Printf("prchecklist starting at %s", addr)
	err = http.ListenAndServe(addr, n)
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
		log.Printf("ServeHTTP: %s (%+v)", err, err)

		status := http.StatusInternalServerError
		if he, ok := err.(httpError); ok {
			status = int(he)
		}

		http.Error(w, fmt.Sprintf("%+v", err), status)
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

	fmt.Fprintf(w, `<!DOCTYPE html>
<pre>%#v</pre>
<form action="/api/check" method="post">
<input name="owner" value="motemen">
<input name="repo" value="test-repository">
<input name="number" value="2">
<input name="featureNumber" value="1">
<input type="submit">
</form>
`, u)
	return nil
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

	user := prchecklist.GitHubUser{
		ID:        u.GetID(),
		Login:     u.GetLogin(),
		AvatarURL: u.GetAvatarURL(),
		Token:     token,
	}
	sess.Values[sessionKeyGitHubUser] = user

	log.Printf("user: %#v", user)

	err = app.AddUser(ctx, user)
	if err != nil {
		return err
	}

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
		// FIXME
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
		Stage  string
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

	return renderJSON(w, &prchecklist.ChecklistResponse{
		Checklist: cl,
		Me:        u,
	})
}

func handleAPICheck(w http.ResponseWriter, req *http.Request) error {
	u, err := getAuthInfo(w, req)
	if err != nil {
		return err
	}
	if u == nil {
		return httpError(http.StatusForbidden)
	}

	type inQuery struct {
		Owner         string
		Repo          string
		Number        int
		Stage         string
		FeatureNumber int
	}

	if err := req.ParseForm(); err != nil {
		return err
	}

	var in inQuery
	err = schema.NewDecoder().Decode(&in, req.Form)
	if err != nil {
		return err
	}

	ctx := req.Context()
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, u.HTTPClient(ctx))

	log.Printf("handleAPICheck: %s %+v", req.Method, in)

	switch req.Method {
	case "PUT":
		err = app.AddCheck(ctx, prchecklist.ChecklistRef{
			Owner:  in.Owner,
			Repo:   in.Repo,
			Number: in.Number,
		}, in.FeatureNumber, *u)
		if err != nil {
			return err
		}

	case "DELETE":
		err = app.RemoveCheck(ctx, prchecklist.ChecklistRef{
			Owner:  in.Owner,
			Repo:   in.Repo,
			Number: in.Number,
		}, in.FeatureNumber, *u)
		if err != nil {
			return err
		}

	default:
		return httpError(http.StatusMethodNotAllowed)
	}

	cl, err := app.GetChecklist(ctx, prchecklist.ChecklistRef{
		Owner:  in.Owner,
		Repo:   in.Repo,
		Number: in.Number,
	})
	if err != nil {
		return err
	}

	return renderJSON(w, &prchecklist.ChecklistResponse{
		Checklist: cl,
		Me:        u,
	})
}

func handleChecklist(w http.ResponseWriter, req *http.Request) error {
	fmt.Fprint(w, `<!DOCTYPE html>
<div id="main"></div>
<script src="/js/bundle.js"></script>`)
	return nil
}
