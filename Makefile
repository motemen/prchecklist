BIN = prchecklist
TOOLDIR = internal/bin

GOASSETSBUILDER = $(TOOLDIR)/go-assets-builder
REFLEX = $(TOOLDIR)/reflex

bundle_sources = $(wildcard static/typescript/* static/scss/*)

$(BIN): lib/web/assets.go always
	go build -ldflags "-X github.com/motemen/prchecklist.Version=$$(git describe --tags HEAD)" -i -v ./cmd/prchecklist

test:
	go vet . ./lib/...
	./scripts/go-test-cover . ./lib/...

develop: $(REFLEX)
	yarn run webpack-dev-server & \
	    { $(REFLEX) -r '\.go\z' -R node_modules -s -- sh -c 'make && ./prchecklist --listen localhost:8081'; }

lib/web/assets.go: static/js/bundle.js $(GOASSETSBUILDER)
	$(GOASSETSBUILDER) -p web -o $@ -s /static static/js

static/js/bundle.js: $(bundle_sources)
	yarn run webpack -- -p --progress

$(GOASSETSBUILDER):
	which $(GOASSETSBUILDER) || GOBIN=$(abspath $(TOOLDIR)) go get -v github.com/jessevdk/go-assets-builder

$(REFLEX):
	which $(REFLEX) || GOBIN=$(abspath $(TOOLDIR)) go get -v github.com/cespare/reflex

always:
