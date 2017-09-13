package web

import (
	"testing"

	"net/http"
	"net/http/httptest"
	"net/url"

	"github.com/golang/mock/gomock"
)

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

	client := http.Client{
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	resp, err := client.Get(s.URL + "/auth?return_to=/motemen/test-repository/pull/2")
	if err != nil {
		t.Fatal(err)
	}

	if got, expected := resp.Header.Get("Location"), "http://github-auth-stub"; got != expected {
		t.Fatalf("Location header differs: %v != %v", got, expected)
	}
}
