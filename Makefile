BIN = prechecklist
TOOLDIR = internal/bin

GOBINDATA = internal/bin/go-bindata

$(BIN):
	go build -i -v ./cmd/prchecklist

test:
	go vet . ./lib/...
	go test . ./lib/...

develop:
	yarn run webpack-dev-server & \
	    { git ls-files lib | entr -r sh -c 'make && ./prchecklist --listen localhost:8081'; }

lib/web/bindata.go: static/js/bundle.js $(GOBINDATA)
	$(GOBINDATA) -nometadata -pkg web -o $@ static/js

static/js/bundle.js: always
	yarn run webpack -- -p --progress

$(GOBINDATA):
	which $(GOBINDATA) || GOBIN=$(abspath $(TOOLDIR)) go get -v github.com/jteeuwen/go-bindata/go-bindata

always:
