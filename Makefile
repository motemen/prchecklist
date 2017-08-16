BIN = prchecklist
TOOLDIR = internal/bin

GOASSETSBUILDER = internal/bin/go-assets-builder

bundle_sources = $(wildcard static/typescript/* static/scss/*)

$(BIN): lib/web/assets.go
	go build -i -v ./cmd/prchecklist

test:
	go vet . ./lib/...
	go test -cover . ./lib/...

develop:
	yarn run webpack-dev-server & \
	    { git ls-files lib | entr -r sh -c 'make && ./prchecklist --listen localhost:8081'; }

lib/web/assets.go: static/js/bundle.js $(GOASSETSBUILDER)
	go-assets-builder -p web -o $@ -s /static static/js

static/js/bundle.js: $(bundle_sources)
	yarn run webpack -- -p --progress

$(GOASSETSBUILDER):
	which $(GOASSETSBUILDER) || GOBIN=$(abspath $(TOOLDIR)) go get -v github.com/jessevdk/go-assets-builder

always:
