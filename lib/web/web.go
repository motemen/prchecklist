package web

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
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/elazarl/go-bindata-assetfs"
	"github.com/gorilla/handlers"
	"github.com/gorilla/mux"
	"github.com/gorilla/schema"
	"github.com/gorilla/sessions"
	"github.com/pkg/errors"

	"github.com/motemen/prchecklist/v2"
	"github.com/motemen/prchecklist/v2/lib/oauthforwarder"
	"github.com/motemen/prchecklist/v2/lib/usecase"
)

var (
	sessionSecret = os.Getenv("PRCHECKLIST_SESSION_SECRET")
	behindProxy   = os.Getenv("PRCHECKLIST_BEHIND_PROXY") != ""
)

const sessionName = "s"

const (
	sessionKeyOAuthState = "oauthState"
	sessionKeyGitHubUser = "githubUser"
)

var htmlContent = `<!DOCTYPE html>
<html>
<head>
  <meta name=viewport content="width=device-width">
  <meta name="prchecklist version" content="` + prchecklist.Version + `">
  <title>prchecklist</title>
</head>
<body>
  <div id="container">
    <div id="main"></div>
  </div>
  <footer><a href="https://github.com/motemen/prchecklist">prchecklist</a> ` + prchecklist.Version + `</footer>
  <script src="/js/bundle.js"></script>
</body>
</html>
`

func init() {
	flag.StringVar(&sessionSecret, "session-secret", sessionSecret, "session secret (PRCHECKLIST_SESSION_SECRET)")
	flag.BoolVar(&behindProxy, "behind-proxy", behindProxy, "prchecklist is behind a reverse proxy (PRCHECKLIST_BEHIND_PROXY)")

	gob.Register(&prchecklist.GitHubUser{})
}

// GitHubGateway is an interface that makes API calls to GitHub (Enterprise).
// Used for OAuth interaction.
type GitHubGateway interface {
	AuthCodeURL(state string, redirectURI *url.URL) string
	AuthenticateUser(ctx context.Context, code string) (*prchecklist.GitHubUser, error)
}

// Web is a web server implementation.
type Web struct {
	app            *usecase.Usecase
	github         GitHubGateway
	sessionStore   sessions.Store
	oauthForwarder oauthforwarder.Forwarder
}

// New creates a new Web.
func New(app *usecase.Usecase, github GitHubGateway) *Web {
	cookieStore := sessions.NewCookieStore([]byte(sessionSecret))
	cookieStore.Options = &sessions.Options{
		Path:     "/",
		MaxAge:   int(30 * 24 * time.Hour / time.Second),
		HttpOnly: true,
	}

	// TODO: write doc about it
	// TODO be a flag variable
	oauthCallbackOrigin := os.Getenv("PRCHECKLIST_OAUTH_CALLBACK_ORIGIN")
	if oauthCallbackOrigin == "" {
		// deprecated
		oauthCallbackOrigin = "https://" + os.Getenv("PRCHECKLIST_OAUTH_CALLBACK_HOST")
	}
	u, _ := url.Parse(oauthCallbackOrigin + "/auth/callback/forward")
	forwarder := oauthforwarder.Forwarder{
		CallbackURL: u,
		Secret:      []byte(sessionSecret),
	}

	return &Web{
		app:            app,
		github:         github,
		sessionStore:   cookieStore,
		oauthForwarder: forwarder,
	}

}

// Handler is the main logic of Web.
func (web *Web) Handler() http.Handler {
	router := mux.NewRouter()
	router.Handle("/", httpHandler(web.handleIndex))
	router.Handle("/auth", httpHandler(web.handleAuth))
	router.Handle("/auth/callback", httpHandler(web.handleAuthCallback))
	router.Handle("/auth/clear", httpHandler(web.handleAuthClear))
	router.Handle("/api/me", httpHandler(web.handleAPIMe))
	router.Handle("/api/checklist", httpHandler(web.handleAPIChecklist))
	router.Handle("/api/check", httpHandler(web.handleAPICheck)).Methods("PUT", "DELETE")
	router.Handle("/{owner}/{repo}/pull/{number}", httpHandler(web.handleChecklist))
	router.Handle("/{owner}/{repo}/pull/{number}/{stage}", httpHandler(web.handleChecklist))
	router.PathPrefix("/js/").Handler(http.FileServer(&assetfs.AssetFS{Asset: Asset, AssetDir: AssetDir, AssetInfo: AssetInfo}))

	handler := http.Handler(router)

	if behindProxy {
		handler = handlers.ProxyHeaders(handler)
	}

	return web.oauthForwarder.Wrap(handler)
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

func (web *Web) handleAuth(w http.ResponseWriter, req *http.Request) error {
	sess, _ := web.sessionStore.Get(req, sessionName)

	state, err := makeRandomString()
	if err != nil {
		return err
	}

	sess.Values[sessionKeyOAuthState] = state
	err = web.sessionStore.Save(req, w, sess)
	if err != nil {
		return err
	}

	ctx := prchecklist.RequestContext(req)

	callback := prchecklist.BuildURL(ctx, "/auth/callback")

	if returnTo := req.URL.Query().Get("return_to"); returnTo != "" {
		callback.RawQuery = url.Values{"return_to": {returnTo}}.Encode()
	}

	// XXX Special and ad-hoc implementation for review apps
	if web.oauthForwarder.CallbackURL.Host != "" {
		callback = web.oauthForwarder.CreateURL(callback.String())
	}

	authURL := web.github.AuthCodeURL(state, callback)

	http.Redirect(w, req, authURL, http.StatusFound)

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

func (web *Web) handleIndex(w http.ResponseWriter, req *http.Request) error {
	fmt.Fprint(w, htmlContent)
	return nil
}

func (web *Web) handleAuthCallback(w http.ResponseWriter, req *http.Request) error {
	sess, err := web.sessionStore.Get(req, sessionName)
	if err != nil {
		return errors.Wrapf(err, "sessionStore.Get")
	}

	state := req.URL.Query().Get("state")
	log.Printf("%#v", sess.Values)
	if state != sess.Values[sessionKeyOAuthState] {
		log.Printf("%v != %v", state, sess.Values[sessionKeyOAuthState])
		return httpError(http.StatusBadRequest)
	}

	delete(sess.Values, sessionKeyOAuthState)

	ctx := prchecklist.RequestContext(req)

	code := req.URL.Query().Get("code")
	user, err := web.github.AuthenticateUser(ctx, code)
	if err != nil {
		return err
	}

	sess.Values[sessionKeyGitHubUser] = *user

	err = web.app.AddUser(ctx, *user)
	if err != nil {
		return err
	}

	err = sess.Save(req, w)
	if err != nil {
		return err
	}

	returnTo := req.URL.Query().Get("return_to")
	if !strings.HasPrefix(returnTo, "/") {
		returnTo = "/"
	}

	http.Redirect(w, req, returnTo, http.StatusFound)

	return nil
}

func (web *Web) handleAuthClear(w http.ResponseWriter, req *http.Request) error {
	http.SetCookie(w, &http.Cookie{
		Name:    sessionName,
		Path:    "/",
		Expires: time.Now().Add(-1 * time.Hour),
	})

	http.Redirect(w, req, "/", http.StatusFound)

	return nil
}

func (web *Web) getAuthInfo(w http.ResponseWriter, req *http.Request) (*prchecklist.GitHubUser, error) {
	sess, err := web.sessionStore.Get(req, sessionName)
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

func (web *Web) handleAPIMe(w http.ResponseWriter, req *http.Request) error {
	u, _ := web.getAuthInfo(w, req)
	result := prchecklist.MeResponse{Me: u}
	if u != nil {
		ctx := prchecklist.RequestContext(req)
		ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, u.HTTPClient(ctx))
		var err error
		result.PullRequests, err = web.app.GetRecentPullRequests(ctx)
		if err != nil {
			return err
		}
	}

	return renderJSON(w, &result)
}

func (web *Web) handleAPIChecklist(w http.ResponseWriter, req *http.Request) error {
	u, err := web.getAuthInfo(w, req)
	if err != nil {
		return err
	}
	if u == nil {
		w.WriteHeader(http.StatusForbidden)
		return renderJSON(w, &prchecklist.ErrorResponse{
			Type: prchecklist.ErrorTypeNotAuthed,
		})
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
	if in.Stage == "" {
		in.Stage = "default"
	}

	ctx := prchecklist.RequestContext(req)
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, u.HTTPClient(ctx))

	cl, err := web.app.GetChecklist(ctx, prchecklist.ChecklistRef{
		Owner:  in.Owner,
		Repo:   in.Repo,
		Number: in.Number,
		Stage:  in.Stage,
	})
	if err != nil {
		return err
	}

	return renderJSON(w, &prchecklist.ChecklistResponse{
		Checklist: cl,
		Me:        u,
	})
}

func (web *Web) handleAPICheck(w http.ResponseWriter, req *http.Request) error {
	u, err := web.getAuthInfo(w, req)
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
	if in.Stage == "" {
		in.Stage = "default"
	}

	clRef := prchecklist.ChecklistRef{
		Owner:  in.Owner,
		Repo:   in.Repo,
		Number: in.Number,
		Stage:  in.Stage,
	}
	ctx := prchecklist.RequestContext(req)
	ctx = context.WithValue(ctx, prchecklist.ContextKeyHTTPClient, u.HTTPClient(ctx))

	log.Printf("handleAPICheck: %s %+v", req.Method, in)

	switch req.Method {
	case "PUT":
		checklist, err := web.app.AddCheck(ctx, clRef, in.FeatureNumber, *u)
		if err != nil {
			return err
		}
		return renderJSON(w, &prchecklist.ChecklistResponse{
			Checklist: checklist,
			Me:        u,
		})

	case "DELETE":
		checklist, err := web.app.RemoveCheck(ctx, clRef, in.FeatureNumber, *u)
		if err != nil {
			return err
		}
		return renderJSON(w, &prchecklist.ChecklistResponse{
			Checklist: checklist,
			Me:        u,
		})

	default:
		return httpError(http.StatusMethodNotAllowed)
	}
}

func (web *Web) handleChecklist(w http.ResponseWriter, req *http.Request) error {
	// handle logged-out state earlier than APIs called
	u, _ := web.getAuthInfo(w, req)
	if u == nil {
		http.Redirect(w, req, "/auth?"+url.Values{"return_to": {req.URL.Path}}.Encode(), http.StatusFound)
		return nil
	}

	fmt.Fprint(w, htmlContent)
	return nil
}
