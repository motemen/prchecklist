BIN = prechecklist

$(BIN):
	go build -i -v ./cmd/prchecklist

.PHONY: $(BIN)
