package prchecklist

import "testing"

import (
	"net/http"
)

func TestContextClient(t *testing.T) {
}

func TestRequestContext(t *testing.T) {
	req, err := http.NewRequest("GET", "https://example.com:1234/foo/bar", nil)
	if err != nil {
		t.Error(err)
		return
	}

	ctx := RequestContext(req)
	url := ContextRequestOrigin(ctx)
	if expected, got := "https://example.com:1234", url.String(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}

func TestBuildURL(t *testing.T) {
	req, err := http.NewRequest("GET", "https://example.com:1234/foo/bar", nil)
	if err != nil {
		t.Error(err)
		return
	}

	ctx := RequestContext(req)
	if expected, got := "https://example.com:1234/path/a", BuildURL(ctx, "path/a").String(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}

	if expected, got := "https://example.com:1234/path/a", BuildURL(ctx, "/path/a").String(); got != expected {
		t.Errorf("expected %v but got %v", expected, got)
	}
}
