MOCKGEN       = go run github.com/golang/mock/mockgen
REFLEX        = go run github.com/cespare/reflex
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

.PHONY: default
default: build

.PHONY: setup
setup: setup-node

.PHONY: setup-node
setup-node:
	yarn install --frozen-lockfile

node_modules/%: package.json
	@$(MAKE) setup-node
	@touch $@

.PHONY: build
build: static/js/bundle.js
	go build \
	    $(BUILDFLAGS) \
	    -ldflags "$(GOLDFLAGS)" \
	    -v \
	    ./cmd/prchecklist

.PHONY: lint
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

.PHONY: test
test: test-unit test-integration

.PHONY: test-unit
test-unit: test-go test-ts

.PHONY: test-go
test-go: lib/mocks
	go test -v -coverprofile=coverage.out . ./lib/...

.PHONY: test-ts
test-ts:
	yarn test --coverage --coverageDirectory=./coverage

.PHONY: test-integration
test-integration:
ifdef PRCHECKLIST_TEST_GITHUB_TOKEN
	yarn jest -c ./integration/jest.config.js
else
	$(warning PRCHECKLIST_TEST_GITHUB_TOKEN is not set)
endif

.PHONY: develop
develop:
	test "$$GITHUB_CLIENT_ID" && test "$$GITHUB_CLIENT_SECRET"
	$(WEBPACKDEVSERVER) & \
	    { $(REFLEX) -r '\.go\z' -R node_modules -s -- \
	      sh -c 'make build && ./prchecklist --listen localhost:8081'; }

static/js/bundle.js: static/typescript/api-schema.ts $(bundled_sources)
	$(WEBPACK) --progress

static/typescript/api-schema.ts: models.go node_modules/json-schema-to-typescript
	$(GOJSSCHEMAGEN) $< | ./scripts/json-schema-to-typescript > $@
