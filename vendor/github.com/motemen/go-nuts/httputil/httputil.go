package httputil

import (
	"fmt"
	"net/http"
)

func Succeeding(resp *http.Response, err error) (*http.Response, error) {
	if err != nil {
		return resp, err
	}
	if resp.StatusCode >= 400 {
		return resp, fmt.Errorf("%d %s", resp.StatusCode, resp.Status)
	}
	return resp, nil
}
