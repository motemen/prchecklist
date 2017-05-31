package main

import (
	"flag"
	"log"
	"net/http"
	"os"

	_ "github.com/motemen/go-loghttp/global"
	"golang.org/x/oauth2"

	"github.com/motemen/go-prchecklist/lib/repository"
	"github.com/motemen/go-prchecklist/lib/usecase"
	"github.com/motemen/go-prchecklist/lib/web"
)

var (
	githubClientID     = os.Getenv("GITHUB_CLIENT_ID")
	githubClientSecret = os.Getenv("GITHUB_CLIENT_SECRET")
	datasource         = getenv("PRCHECKLIST_DATASOURCE", "bolt:./prchecklist.db")
	addr               string
)

var githubEndpoint = oauth2.Endpoint{
	AuthURL:  "https://github.com/login/oauth/authorize",
	TokenURL: "https://github.com/login/oauth/access_token",
}

func getenv(key, def string) string {
	v := os.Getenv(key)
	if v != "" {
		return v
	}

	return def
}

func init() {
	flag.StringVar(&githubClientID, "github-client-id", os.Getenv("GITHUB_CLIENT_ID"), "GitHub client ID")
	flag.StringVar(&githubClientSecret, "github-client-secret", os.Getenv("GITHUB_CLIENT_SECRET"), "GitHub client secret")
	flag.StringVar(&datasource, "datasource", datasource, "database source name")
	flag.StringVar(&addr, "listen", "localhost:8080", "`address` to listen")
}

func main() {
	flag.Parse()

	coreRepo, err := repository.NewBoltCore(datasource[len("bolt:"):])
	if err != nil {
		log.Fatal(err)
	}

	app := usecase.New(
		repository.NewGitHub(),
		coreRepo,
	)
	w := web.New(app, &oauth2.Config{
		ClientID:     githubClientID,
		ClientSecret: githubClientSecret,
		Endpoint:     githubEndpoint,
		Scopes:       []string{"repo"},
	})

	log.Printf("prchecklist starting at %s", addr)

	err = http.ListenAndServe(addr, w.Handler())
	log.Fatal(err)
}
