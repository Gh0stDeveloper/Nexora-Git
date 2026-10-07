package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

func testConfig() config {
	return config{
		ClientID:          "Iv1.test",
		ClientSecret:      "server-secret",
		GitHubCallbackURL: "https://auth.example.test/oauth/callback",
		AppCallbackURI:    "https://auth.example.test/oauth/android/callback",
		GitHubOAuthBase:   "https://github.invalid",
		GitHubAPIBase:     "https://api.github.invalid",
		Port:              "8080",
	}
}

func TestOAuthCallbackForwardsCodeAndState(t *testing.T) {
	s := newServer(testConfig(), &http.Client{Timeout: time.Second})

	request := httptest.NewRequest(
		http.MethodGet,
		"/oauth/callback?code=abc123&state=state123",
		nil,
	)
	response := httptest.NewRecorder()

	s.routes().ServeHTTP(response, request)

	if response.Code != http.StatusSeeOther {
		t.Fatalf("expected 303, got %d", response.Code)
	}

	location := response.Header().Get("Location")
	parsed, err := url.Parse(location)
	if err != nil {
		t.Fatal(err)
	}
	if parsed.Scheme != "https" || parsed.Host != "auth.example.test" || parsed.Path != "/oauth/android/callback" {
		t.Fatalf("unexpected callback target: %s", location)
	}
	if parsed.Query().Get("code") != "abc123" || parsed.Query().Get("state") != "state123" {
		t.Fatalf("missing forwarded OAuth parameters: %s", location)
	}
}

func TestExchangeForwardsPKCEWithoutLeakingSecretToClient(t *testing.T) {
	var received url.Values

	github := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/login/oauth/access_token" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		received, _ = url.ParseQuery(string(body))
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"access_token":"ghu_testtoken","expires_in":28800,"refresh_token":"ghr_testrefresh","refresh_token_expires_in":15897600,"token_type":"bearer"}`)
	}))
	defer github.Close()

	cfg := testConfig()
	cfg.GitHubOAuthBase = github.URL
	s := newServer(cfg, github.Client())

	verifier := strings.Repeat("A", 43)
	payload, _ := json.Marshal(map[string]string{
		"code":          "code-value",
		"code_verifier": verifier,
	})

	request := httptest.NewRequest(
		http.MethodPost,
		"/v1/oauth/exchange",
		strings.NewReader(string(payload)),
	)
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()

	s.routes().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", response.Code, response.Body.String())
	}
	if received.Get("client_secret") != cfg.ClientSecret {
		t.Fatal("broker did not supply confidential client secret to GitHub")
	}
	if received.Get("code_verifier") != verifier {
		t.Fatal("broker did not forward PKCE verifier")
	}
	if strings.Contains(response.Body.String(), cfg.ClientSecret) {
		t.Fatal("client secret leaked in broker response")
	}
}

func TestExchangeRejectsInvalidVerifier(t *testing.T) {
	s := newServer(testConfig(), &http.Client{Timeout: time.Second})
	payload := `{"code":"code-value","code_verifier":"short"}`

	request := httptest.NewRequest(
		http.MethodPost,
		"/v1/oauth/exchange",
		strings.NewReader(payload),
	)
	response := httptest.NewRecorder()

	s.routes().ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", response.Code)
	}
}

func TestSecurityHeadersDisableCaching(t *testing.T) {
	s := newServer(testConfig(), &http.Client{Timeout: time.Second})
	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	response := httptest.NewRecorder()

	s.routes().ServeHTTP(response, request)

	if response.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("expected Cache-Control: no-store")
	}
}


func TestLoadConfigRejectsCrossOriginAppCallback(t *testing.T) {
	t.Setenv("GITHUB_APP_CLIENT_ID", "Iv1.test")
	t.Setenv("GITHUB_APP_CLIENT_SECRET", "server-secret")
	t.Setenv("GITHUB_CALLBACK_URL", "https://auth.example.test/oauth/callback")
	t.Setenv("APP_CALLBACK_URI", "https://evil.example.test/oauth/android/callback")
	t.Setenv("PORT", "8080")

	if _, err := loadConfig(); err == nil {
		t.Fatal("expected cross-origin APP_CALLBACK_URI to be rejected")
	}
}

func TestLoadConfigAcceptsVerifiedAppLinkShape(t *testing.T) {
	t.Setenv("GITHUB_APP_CLIENT_ID", "Iv1.test")
	t.Setenv("GITHUB_APP_CLIENT_SECRET", "server-secret")
	t.Setenv("GITHUB_CALLBACK_URL", "https://auth.example.test/oauth/callback")
	t.Setenv("APP_CALLBACK_URI", "https://auth.example.test/oauth/android/callback")
	t.Setenv("PORT", "8080")

	cfg, err := loadConfig()
	if err != nil {
		t.Fatalf("expected valid configuration, got %v", err)
	}
	if cfg.AppCallbackURI != "https://auth.example.test/oauth/android/callback" {
		t.Fatalf("unexpected app callback URI: %s", cfg.AppCallbackURI)
	}
}
