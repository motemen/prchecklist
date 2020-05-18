//go:generate go run github.com/golang/mock/mockgen -package web -destination web_mock_test.go github.com/motemen/prchecklist/v2/lib/web GitHubGateway
package web

import (
	"os"
	"testing"

	"net/http"
	"net/http/httptest"
	"net/url"

	"github.com/golang/mock/gomock"
	"github.com/motemen/go-nuts/httputil"
	"github.com/stretchr/testify/require"
)

var noRedirectClient = http.Client{
	CheckRedirect: func(req *http.Request, via []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

func TestWeb_HandleAuth(t *testing.T) {
	ctrl := gomock.NewController(t)

	g := NewMockGitHubGateway(ctrl)

	web := New(nil, g)
	s := httptest.NewServer(web.Handler())
	defer s.Close()

	u, _ := url.Parse(s.URL)
	u.Path = "/auth/callback"
	u.RawQuery = url.Values{"return_to": {"/motemen/test-repository/pull/2"}}.Encode()

	g.EXPECT().AuthCodeURL(gomock.Any(), u).Return("http://github-auth-stub")

	resp, err := noRedirectClient.Get(s.URL + "/auth?return_to=/motemen/test-repository/pull/2")
	if err != nil {
		t.Fatal(err)
	}

	if got, expected := resp.Header.Get("Location"), "http://github-auth-stub"; got != expected {
		t.Fatalf("Location header differs: %v != %v", got, expected)
	}
}

func TestWeb_Static(t *testing.T) {
	ctrl := gomock.NewController(t)

	g := NewMockGitHubGateway(ctrl)

	web := New(nil, g)
	s := httptest.NewServer(web.Handler())
	defer s.Close()

	_, err := httputil.Successful(http.Get(s.URL + "/js/bundle.js"))
	if err != nil {
		t.Fatal(err)
	}
}

func TestWeb_HandleAuth_forward(t *testing.T) {
	type testServer struct {
		mock   *gomock.Controller
		github *MockGitHubGateway
		web    *Web
		server *httptest.Server
	}

	build := func() testServer {
		ctrl := gomock.NewController(t)
		g := NewMockGitHubGateway(ctrl)
		web := New(nil, g)
		s := httptest.NewServer(web.Handler())
		return testServer{
			mock:   ctrl,
			github: g,
			web:    web,
			server: s,
		}
	}

	mainApp := build()

	mainAppURL, _ := url.Parse(mainApp.server.URL)
	os.Setenv("PRCHECKLIST_OAUTH_CALLBACK_ORIGIN", mainAppURL.Scheme+"://"+mainAppURL.Host)
	reviewApp := build()
	os.Setenv("PRCHECKLIST_OAUTH_CALLBACK_ORIGIN", "")

	defer mainApp.server.Close()
	defer reviewApp.server.Close()

	reviewApp.github.EXPECT().AuthCodeURL(gomock.Any(), gomock.Any()).DoAndReturn(func(state string, redirectURI *url.URL) string {
		return "http://github-auth-stub?redirect_uri=" + url.QueryEscape(redirectURI.String())
	})

	var redirectURI *url.URL

	t.Run("GET /auth to review app redirects to main app", func(t *testing.T) {
		resp, err := noRedirectClient.Get(reviewApp.server.URL + "/auth?return_to=/motemen/test-repository/pull/2")
		require.NoError(t, err)

		// 1. Location: header is a URL to GitHub OAuth authz page
		// 2. with redirect_uri set to main app's auth page
		// 3. forwarding to review app's auth callback
		// 4. with return_to set to original return_to param.

		// 1.
		location, err := url.Parse(resp.Header.Get("Location"))
		require.NoError(t, err, "parsing Location")

		t.Logf("Location: %s", location)

		// 2.
		redirectURI, err = url.Parse(location.Query().Get("redirect_uri"))
		require.NoError(t, err, "parsing redirect_uri")
		require.Equal(t,
			mainApp.server.URL+"/auth/callback/forward",
			"http://"+redirectURI.Host+redirectURI.Path,
			"redirect_uri",
		)

		// 3.
		forward, err := url.Parse(redirectURI.Query().Get("to"))
		require.NoError(t, err, "parsing forward")
		require.Equal(t,
			reviewApp.server.URL+"/auth/callback",
			"http://"+forward.Host+forward.Path,
			"forward",
		)

		// 4.
		require.Equal(t, "/motemen/test-repository/pull/2", forward.Query().Get("return_to"))
	})

	t.Run("forward succeeds", func(t *testing.T) {
		resp, err := noRedirectClient.Get(redirectURI.String() + "&code=STUBCODE")
		require.NoError(t, err)

		location, err := url.Parse(resp.Header.Get("Location"))
		require.NoError(t, err)

		require.Equal(t, "/motemen/test-repository/pull/2", location.Query().Get("return_to"))
		require.Equal(t, "STUBCODE", location.Query().Get("code"))
	})

	t.Run("forward fails with invalid signature", func(t *testing.T) {
		invalidRedirectURI := *redirectURI
		q := invalidRedirectURI.Query()
		q.Set("sig", "invalid")
		invalidRedirectURI.RawQuery = q.Encode()

		resp, err := noRedirectClient.Get(invalidRedirectURI.String() + "&code=STUBCODE")
		require.NoError(t, err)
		require.True(t, resp.StatusCode >= 400)
	})
}
