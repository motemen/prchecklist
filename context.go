package prchecklist

import (
	"context"
	"net/http"
	"net/url"
)

// TODO: use go4.org/ctxutil

type contextKey struct{ name string }

var ContextKeyHTTPClient = &contextKey{"httpClient"}

func ContextClient(ctx context.Context) *http.Client {
	if client, ok := ctx.Value(ContextKeyHTTPClient).(*http.Client); ok && client != nil {
		return client
	}

	return http.DefaultClient
}

var ContextKeyRequestOrigin = &contextKey{"requestOrigin"}

func RequestContext(req *http.Request) context.Context {
	ctx := req.Context()
	origin := &url.URL{
		Scheme: req.URL.Scheme,
		Host:   req.Host,
	}
	if origin.Scheme == "" {
		origin.Scheme = "http"
	}
	return context.WithValue(ctx, ContextKeyRequestOrigin, origin)
}

func ContextRequestOrigin(ctx context.Context) *url.URL {
	return ctx.Value(ContextKeyRequestOrigin).(*url.URL)
}

func BuildURL(ctx context.Context, path string) *url.URL {
	origin := ContextRequestOrigin(ctx)
	return &url.URL{
		Scheme: origin.Scheme,
		Host:   origin.Host,
		Path:   path,
	}
}
