// +build ignore

package main

import (
	"log"
	"net/http"
	"os"

	"google.golang.org/appengine"

	"github.com/motemen/prchecklist/lib/repository"
	"github.com/motemen/prchecklist/lib/usecase"
	"github.com/motemen/prchecklist/lib/web"
)

func main() {
	coreRepo, err := repository.NewDatastoreCore(os.Getenv("DATASTORE_PROJECT_ID"))
	if err != nil {
		log.Fatal(err)
	}

	app := usecase.New(
		repository.NewGitHub(),
		coreRepo,
	)

	w := web.New(app)

	http.Handle("/", w.Handler())

	appengine.Main()
}
