package prchecklist

import (
	"context"
	"net/http"
)

type contextKey struct{ name string }

var ContextKeyHTTPClient = &contextKey{"httpClient"}

func ContextClient(ctx context.Context) *http.Client {
	if client, ok := ctx.Value(ContextKeyHTTPClient).(*http.Client); ok && client != nil {
		return client
	}

	return http.DefaultClient
}
