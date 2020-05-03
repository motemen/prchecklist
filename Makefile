GOBINDATA     = go run github.com/a-urth/go-bindata/go-bindata
MOCKGEN       = go run github.com/golang/mock/mockgen
REFLEX        = go run github.com/cespare/reflex
GOCREDITS     = go run github.com/Songmu/gocredits/cmd/gocredits
GOJSSCHEMAGEN = go run github.com/motemen/go-generate-jsschema/cmd/gojsschemagen
GOLINT        = go run golang.org/x/lint/golint

WEBPACK          = yarn webpack
WEBPACKDEVSERVER = yarn webpack-dev-server
ESLINT           = yarn eslint

VERSION := $(shell git describe --tags HEAD 2> /dev/null)

ifeq ($(VERSION),)
    GOLDFLAGS =
else
    GOLDFLAGS = -X github.com/motemen/prchecklist/v2.Version=$(VERSION)
endif
GOOSARCH  = linux/amd64

bundled_sources = $(wildcard static/typescript/* static/scss/*)
export GO111MODULE=on

default: build

setup: setup-node

setup-node:
	yarn install --frozen-lockfile

node_modules/%: package.json
	@$(MAKE) setup-node
	@touch $@

build: lib/web/assets.go
	go build \
	    $(BUILDFLAGS) \
	    -ldflags "$(GOLDFLAGS)" \
	    -v \
	    ./cmd/prchecklist

lint: lint-go lint-ts

lint-go:
	$(GOLINT) -min_confidence=0.9 -set_exit_status . ./lib/...
	go vet . ./lib/...

lint-ts:
	$(ESLINT) 'static/typescript/**/*.{ts,tsx}'

fix:
	$(ESLINT) 'static/typescript/**/*.{ts,tsx}' --fix --quiet

lib/mocks:
	go generate -x ./lib/...

test: test-go test-ts

test-go: lib/mocks
	go test -v -coverprofile=coverage.out . ./lib/...

test-ts:
	yarn test --coverage --coverageDirectory=./coverage

develop:
	test "$$GITHUB_CLIENT_ID" && test "$$GITHUB_CLIENT_SECRET"
	$(WEBPACKDEVSERVER) & \
	    { $(REFLEX) -r '\.go\z' -R node_modules -s -- \
	      sh -c 'make build && ./prchecklist --listen localhost:8081'; }

lib/web/assets.go: static/js/bundle.js static/text/licenses
	$(GOBINDATA) -pkg web -o $@ -prefix static/ -modtime 1 static/js static/text

static/js/bundle.js: static/typescript/api-schema.ts $(bundled_sources)
	$(WEBPACK) --progress

static/text/licenses:
	$(GOCREDITS) . > $@

static/typescript/api-schema.ts: models.go node_modules/json-schema-to-typescript
	$(GOJSSCHEMAGEN) $< | ./scripts/json-schema-to-typescript > $@

.PHONY: default build xbuild test lint develop setup setup-go setup-node
