package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	_ "github.com/motemen/go-loghttp/global"

	"github.com/motemen/prchecklist"
	"github.com/motemen/prchecklist/lib/gateway"
	"github.com/motemen/prchecklist/lib/repository"
	"github.com/motemen/prchecklist/lib/usecase"
	"github.com/motemen/prchecklist/lib/web"
)

var (
	datasource   string
	addr         string
	showVersion  bool
	showLicenses bool
)

const shutdownTimeout = 30 * time.Second

func getenv(key, def string) string {
	v := os.Getenv(key)
	if v != "" {
		return v
	}

	return def
}

func init() {
	var defaultDatasource = "bolt:./prchecklist.db"
	if os.Getenv("GOOGLE_CLOUD_PROJECT") != "" {
		defaultDatasource = "datastore:" + os.Getenv("GOOGLE_CLOUD_PROJECT")
	}

	datasource = getenv("PRCHECKLIST_DATASOURCE", defaultDatasource)
	flag.StringVar(&datasource, "datasource", datasource, "database source name (PRCHECKLIST_DATASOURCE)")
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	flag.StringVar(&addr, "listen", ":"+port, "`address` to listen")
	flag.BoolVar(&showVersion, "version", false, "show version information")
	flag.BoolVar(&showLicenses, "licenses", false, "show license notifications")
}

func main() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	flag.Parse()

	if showVersion {
		fmt.Printf("prchecklist %s\n", prchecklist.Version)
		os.Exit(0)
	}

	if showLicenses {
		b := web.MustAsset("text/licenses")
		fmt.Println(string(b))
		os.Exit(0)
	}

	coreRepo, err := repository.NewCore(datasource)
	if err != nil {
		log.Fatal(err)
	}

	github, err := gateway.NewGitHub()
	if err != nil {
		log.Fatal(err)
	}

	app := usecase.New(github, coreRepo)
	w := web.New(app, github)

	log.Printf("prchecklist starting at %s", addr)

	server := http.Server{
		Addr:    addr,
		Handler: w.Handler(),
	}

	go func() {
		err := server.ListenAndServe()
		log.Println(err)
	}()

	sigc := make(chan os.Signal, 1)
	signal.Notify(sigc, syscall.SIGTERM, syscall.SIGINT, syscall.SIGHUP)

	sig := <-sigc
	log.Printf("received signal %q; shutdown gracefully in %s ...", sig, shutdownTimeout)

	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()

	errc := make(chan error)
	go func() { errc <- server.Shutdown(ctx) }()

	select {
	case sig := <-sigc:
		log.Printf("received 2nd signal %q; shutdown now", sig)
		cancel()
		server.Close()

	case err := <-errc:
		if err != nil {
			log.Fatalf("while shutdown: %s", err)
		}
	}
}
