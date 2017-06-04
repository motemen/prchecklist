BIN = prechecklist

$(BIN):
	go build -i -v ./cmd/prchecklist

static/js/bundle.js:
	yarn run webpack -- -p

.PHONY: $(BIN)
