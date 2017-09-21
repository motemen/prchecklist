GOBINDATA = .bin/go-bindata
GOX       = .bin/gox
MOCKGEN   = .bin/mockgen
REFLEX    = .bin/reflex
GOVENDOR  = .bin/govendor

WEBPACK          = node_modules/.bin/webpack
WEBPACKDEVSERVER = node_modules/.bin/webpack-dev-server

GOLDFLAGS = -X github.com/motemen/prchecklist.Version=$$(git describe --tags HEAD)
GOOSARCH  = linux/amd64

go_tools   = $(GOBINDATA) $(REFLEX) $(MOCKGEN) $(GOX) $(GOVENDOR)
node_tools = $(WEBPACKDEVSERVER) $(WEBPACK)

bundled_sources = $(wildcard static/typescript/* static/scss/*)

default: build

setup: setup-go setup-node

setup-go: $(go_tools)

setup-node: $(node_tools)

$(go_tools): Makefile
	GOBIN=$(abspath .bin) go get -v \
	      gobin.cc/go-bindata \
	      gobin.cc/reflex \
	      gobin.cc/mockgen \
	      gobin.cc/gox \
	      gobin.cc/govendor
	@touch .bin/*

$(node_tools): package.json
	yarn install
	@touch node_modules/.bin/*

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

static/js/bundle.js: $(bundled_sources) $(WEBPACK)
	$(WEBPACK) -p --progress

static/text/licenses: vendor/vendor.json $(GOVENDOR)
	$(GOVENDOR) license > $@

lib/web/web_mock_test.go: lib/web/web.go $(MOCKGEN)
	$(MOCKGEN) -package web -source $< GitHubGateway > $@

.PHONY: build xbuild test develop setup setup-go setup-node
