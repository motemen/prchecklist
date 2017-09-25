package prchecklist

import (
	"context"
	"net/http"
	"net/url"
)

type contextKey struct{ name string }

// ContextKeyHTTPClient is the context key to use with context.WithValue
// to associate an *http.Client to a context.
// The associated client can be retrieved by ContextClient.
// In this application, the client should be one created by oauth2.NewClient.
// TODO(motemen): use go4.org/ctxutil?
var ContextKeyHTTPClient = &contextKey{"httpClient"}

// ContextClient returns a client associated with the context ctx.
// If none is found, returns http.DefaultClient.
func ContextClient(ctx context.Context) *http.Client {
	if client, ok := ctx.Value(ContextKeyHTTPClient).(*http.Client); ok && client != nil {
		return client
	}

	return http.DefaultClient
}

var contextKeyRequestOrigin = &contextKey{"requestOrigin"}

// RequestContext creates a context from an HTTP request req,
// along with the origin data constructed from client request.
func RequestContext(req *http.Request) context.Context {
	ctx := req.Context()
	origin := &url.URL{
		Scheme: req.URL.Scheme,
		Host:   req.Host,
	}
	if origin.Scheme == "" {
		origin.Scheme = "http"
	}
	return context.WithValue(ctx, contextKeyRequestOrigin, origin)
}

// ContextRequestOrigin retrieves origin data from the context
// created by RequestContext.
func ContextRequestOrigin(ctx context.Context) *url.URL {
	return ctx.Value(contextKeyRequestOrigin).(*url.URL)
}

// BuildURL builds an absolute URL with a given path.
// Context ctx must be one obtained by RequestContext.
func BuildURL(ctx context.Context, path string) *url.URL {
	origin := ContextRequestOrigin(ctx)
	return &url.URL{
		Scheme: origin.Scheme,
		Host:   origin.Host,
		Path:   path,
	}
}
