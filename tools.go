// +build tools

package tools

import (
	_ "github.com/Songmu/gocredits/cmd/gocredits"
	_ "github.com/cespare/reflex"
	_ "github.com/golang/mock/mockgen"
	_ "github.com/motemen/go-generate-jsschema/cmd/gojsschemagen"
	_ "golang.org/x/lint/golint"
)
