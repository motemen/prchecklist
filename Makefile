BIN = prechecklist

$(BIN): always
	go build -i -v ./cmd/prchecklist

static/js/bundle.js: always
	yarn run webpack -- -p

develop: $(BIN)
	yarn run webpack -- --watch & yarn run browser-sync -- start --proxy localhost:8081 --port 8080 --files static/js/bundle.js & ./prchecklist --listen localhost:8081

always:

.PHONY: always
