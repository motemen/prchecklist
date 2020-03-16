GOBINDATA     = go run github.com/a-urth/go-bindata/go-bindata
GOX           = go run github.com/mitchellh/gox
MOCKGEN       = go run github.com/golang/mock/mockgen
REFLEX        = go run github.com/cespare/reflex
GOCREDITS     = go run github.com/Songmu/gocredits/cmd/gocredits
GOJSSCHEMAGEN = go run github.com/motemen/go-generate-jsschema/cmd/gojsschemagen
GOLINT        = go run golang.org/x/lint/golint

WEBPACK          = yarn webpack
WEBPACKDEVSERVER = yarn webpack-dev-server
TSLINT           = yarn tslint

GOLDFLAGS = -X github.com/motemen/prchecklist.Version=$$(git describe --tags HEAD)
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
	    -i \
	    -v \
	    ./cmd/prchecklist

xbuild: lib/web/assets.go
	$(GOX) \
	    -osarch $(GOOSARCH) \
	    -output "build/{{.Dir}}_{{.OS}}_{{.Arch}}" \
	    -ldflags "$(GOLDFLAGS)" \
	    ./cmd/prchecklist

lint:
	$(GOLINT) -min_confidence=0.9 -set_exit_status . ./lib/...
	$(TSLINT) --exclude '**/api-schema.ts' static/typescript/*

test: lib/web/web_mock_test.go
	go vet . ./lib/...
	./scripts/go-test-cover . ./lib/...

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
	go mod tidy # get all the dependencies regardress of OS, architecture and build tags
	$(GOCREDITS) . > $@

lib/web/web_mock_test.go: lib/web/web.go
	$(MOCKGEN) -package web -destination $@ github.com/motemen/prchecklist/lib/web GitHubGateway

static/typescript/api-schema.ts: models.go node_modules/json-schema-to-typescript
	$(GOJSSCHEMAGEN) $< | ./scripts/json-schema-to-typescript > $@

.PHONY: default build xbuild test lint develop setup setup-go setup-node
