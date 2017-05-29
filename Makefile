BIN = prechecklist

$(BIN):
	go build -i -v ./cmd/prchecklist

static/js/bundle.js:
	yarn run webpack

.PHONY: $(BIN)
