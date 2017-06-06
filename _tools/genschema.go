package main

import (
	"encoding/json"
	"log"
	"os"

	"github.com/alecthomas/jsonschema"

	"github.com/motemen/prchecklist"
)

func main() {
	s := jsonschema.Reflect(&prchecklist.Checklist{})
	err := json.NewEncoder(os.Stdout).Encode(s)
	if err != nil {
		log.Fatal(err)
	}
}
