GOBINDATA     = .bin/go-bindata
GOX           = .bin/gox
MOCKGEN       = .bin/mockgen
REFLEX        = .bin/reflex
GOVENDOR      = .bin/govendor
GOJSSCHEMAGEN = .bin/gojsschemagen
GOLINT        = .bin/golint

WEBPACK          = node_modules/.bin/webpack
WEBPACKDEVSERVER = node_modules/.bin/webpack-dev-server
TSLINT           = node_modules/.bin/tslint

GOLDFLAGS = -X github.com/motemen/prchecklist.Version=$$(git describe --tags HEAD)
GOOSARCH  = linux/amd64

bundled_sources = $(wildcard static/typescript/* static/scss/*)

default: build

setup: setup-go setup-node

setup-go:
	GOBIN=$(abspath .bin) go get -v \
	    github.com/jteeuwen/go-bindata/go-bindata \
	    github.com/cespare/reflex \
	    github.com/golang/mock/mockgen \
	    github.com/mitchellh/gox \
	    github.com/kardianos/govendor \
	    golang.org/x/lint/golint \
	    github.com/motemen/go-generate-jsschema/cmd/gojsschemagen

setup-node:
	yarn install

.bin/%: Makefile
	@$(MAKE) setup-go
	@touch $@

node_modules/%: package.json
	@$(MAKE) setup-node
	@touch $@

build: lib/web/assets.go
	go build \
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

lint: $(GOLINT) $(TSLINT)
	$(GOLINT) -min_confidence=0.9 -set_exit_status . ./lib/...
	$(TSLINT) --exclude '**/api-schema.ts' static/typescript/*

test: lib/web/web_mock_test.go
	go vet . ./lib/...
	./scripts/go-test-cover . ./lib/...

develop: $(REFLEX) $(WEBPACKDEVSERVER)
	test "$$GITHUB_CLIENT_ID" && test "$$GITHUB_CLIENT_SECRET"
	$(WEBPACKDEVSERVER) & \
	    { $(REFLEX) -r '\.go\z' -R node_modules -s -- \
	      sh -c 'make build && ./prchecklist --listen localhost:8081'; }

lib/web/assets.go: static/js/bundle.js static/text/licenses $(GOBINDATA)
	$(GOBINDATA) -pkg web -o $@ -prefix static/ -modtime 1 static/js static/text

static/js/bundle.js: static/typescript/api-schema.ts $(bundled_sources) $(WEBPACK)
	$(WEBPACK) -p --progress

static/text/licenses: vendor/vendor.json $(GOVENDOR)
	$(GOVENDOR) license > $@

lib/web/web_mock_test.go: lib/web/web.go $(MOCKGEN)
	$(MOCKGEN) -package web -destination $@ github.com/motemen/prchecklist/lib/web GitHubGateway

static/typescript/api-schema.ts: models.go $(GOJSSCHEMAGEN) node_modules/json-schema-to-typescript
	$(GOJSSCHEMAGEN) $< | ./scripts/json-schema-to-typescript > $@

.PHONY: default build xbuild test lint develop setup setup-go setup-node
