package oauthforwarder

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/url"
)

// Forwarder is the implementation root regarding OAuth callback forwarder.
type Forwarder struct {
	CallbackURL *url.URL
	Secret      []byte
}

func (f *Forwarder) hashString(in string) []byte {
	h := hmac.New(sha256.New, f.Secret)
	h.Write([]byte(in))
	return h.Sum(nil)
}

// CreateURL creates URL to callback host which in success returns back to callback.
func (f *Forwarder) CreateURL(callback string) *url.URL {
	u := *f.CallbackURL
	q := u.Query()
	q.Set("to", callback)
	q.Set("sig", fmt.Sprintf("%x", f.hashString(callback)))
	u.RawQuery = q.Encode()
	return &u
}

// Wrap wraps an http.Handler which forwards client to original callback URL.
func (f *Forwarder) Wrap(base http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path != f.CallbackURL.Path {
			base.ServeHTTP(w, req)
			return
		}

		query := req.URL.Query()

		to := query.Get("to")
		sig := query.Get("sig")

		if to == "" || sig == "" {
			http.Error(w, http.StatusText(http.StatusBadRequest), http.StatusBadRequest)
			return
		}

		forwardURL, err := url.Parse(to)
		if err != nil {
			http.Error(w, "Invalid URL", http.StatusBadRequest)
			return
		}

		sigBytes := make([]byte, 32)
		_, err = hex.Decode(sigBytes, []byte(sig))
		if err != nil {
			http.Error(w, "Invalid signature", http.StatusBadRequest)
			return
		}
		if !hmac.Equal(f.hashString(to), sigBytes) {
			http.Error(w, "Invalid signature", http.StatusBadRequest)
			return
		}

		q := forwardURL.Query()
		q.Set("code", query.Get("code"))
		q.Set("state", query.Get("state"))
		forwardURL.RawQuery = q.Encode()

		http.Redirect(w, req, forwardURL.String(), http.StatusTemporaryRedirect)
	})
}
