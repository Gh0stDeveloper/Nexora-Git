package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"
)

const githubAPIVersion = "2026-03-10"

var verifierPattern = regexp.MustCompile(`^[A-Za-z0-9._~-]{43,128}$`)

type config struct {
	ClientID          string
	ClientSecret      string
	GitHubCallbackURL string
	AppCallbackURI    string
	GitHubOAuthBase   string
	GitHubAPIBase     string
	Port              string
}

type server struct {
	cfg    config
	client *http.Client
}

type tokenResponse struct {
	AccessToken           string `json:"access_token,omitempty"`
	ExpiresIn             int64  `json:"expires_in,omitempty"`
	RefreshToken          string `json:"refresh_token,omitempty"`
	RefreshTokenExpiresIn int64  `json:"refresh_token_expires_in,omitempty"`
	TokenType             string `json:"token_type,omitempty"`
	Scope                 string `json:"scope,omitempty"`
	Error                 string `json:"error,omitempty"`
	ErrorDescription      string `json:"error_description,omitempty"`
}

func main() {
	cfg, err := loadConfig()
	if err != nil {
		log.Fatal(err)
	}

	s := newServer(cfg, &http.Client{Timeout: 15 * time.Second})

	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           s.routes(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      20 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("Nexora Git auth broker listening on :%s", cfg.Port)
	log.Fatal(httpServer.ListenAndServe())
}

func loadConfig() (config, error) {
	cfg := config{
		ClientID:          strings.TrimSpace(os.Getenv("GITHUB_APP_CLIENT_ID")),
		ClientSecret:      strings.TrimSpace(os.Getenv("GITHUB_APP_CLIENT_SECRET")),
		GitHubCallbackURL: strings.TrimSpace(os.Getenv("GITHUB_CALLBACK_URL")),
		AppCallbackURI:    strings.TrimSpace(os.Getenv("APP_CALLBACK_URI")),
		GitHubOAuthBase:   "https://github.com",
		GitHubAPIBase:     "https://api.github.com",
		Port:              strings.TrimSpace(os.Getenv("PORT")),
	}
	if cfg.Port == "" {
		cfg.Port = "8080"
	}

	if cfg.ClientID == "" || cfg.ClientSecret == "" {
		return config{}, errors.New("GITHUB_APP_CLIENT_ID and GITHUB_APP_CLIENT_SECRET are required")
	}
	githubCallback, err := url.Parse(cfg.GitHubCallbackURL)
	if err != nil ||
		!strings.EqualFold(githubCallback.Scheme, "https") ||
		githubCallback.Host == "" ||
		githubCallback.User != nil ||
		githubCallback.Path != "/oauth/callback" ||
		githubCallback.RawQuery != "" ||
		githubCallback.Fragment != "" {
		return config{}, errors.New("GITHUB_CALLBACK_URL must be an exact HTTPS /oauth/callback URL")
	}

	appURI, err := url.Parse(cfg.AppCallbackURI)
	if err != nil ||
		!strings.EqualFold(appURI.Scheme, "https") ||
		appURI.Host == "" ||
		appURI.User != nil ||
		!strings.EqualFold(appURI.Host, githubCallback.Host) ||
		appURI.Path != "/oauth/android/callback" ||
		appURI.RawQuery != "" ||
		appURI.Fragment != "" {
		return config{}, errors.New("APP_CALLBACK_URI must be an HTTPS /oauth/android/callback URL on the callback authority")
	}

	return cfg, nil
}

func newServer(cfg config, client *http.Client) *server {
	return &server{
		cfg:    cfg,
		client: client,
	}
}

func (s *server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.health)
	mux.HandleFunc("/oauth/callback", s.oauthCallback)
	mux.HandleFunc("/v1/oauth/exchange", s.exchange)
	mux.HandleFunc("/v1/oauth/refresh", s.refresh)
	mux.HandleFunc("/v1/oauth/revoke", s.revoke)

	return securityHeaders(mux)
}

func (s *server) health(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *server) oauthCallback(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w)
		return
	}

	target, err := url.Parse(s.cfg.AppCallbackURI)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "invalid_server_configuration", "")
		return
	}

	query := target.Query()
	for _, key := range []string{"code", "state", "error", "error_description"} {
		if value := strings.TrimSpace(r.URL.Query().Get(key)); value != "" {
			query.Set(key, value)
		}
	}
	target.RawQuery = query.Encode()

	http.Redirect(w, r, target.String(), http.StatusSeeOther)
}

func (s *server) exchange(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w)
		return
	}

	var request struct {
		Code         string `json:"code"`
		CodeVerifier string `json:"code_verifier"`
	}
	if err := decodeJSON(w, r, &request); err != nil {
		return
	}

	request.Code = strings.TrimSpace(request.Code)
	if request.Code == "" || len(request.Code) > 1024 {
		writeError(w, http.StatusBadRequest, "invalid_code", "")
		return
	}
	if !verifierPattern.MatchString(request.CodeVerifier) {
		writeError(w, http.StatusBadRequest, "invalid_code_verifier", "")
		return
	}

	form := url.Values{}
	form.Set("client_id", s.cfg.ClientID)
	form.Set("client_secret", s.cfg.ClientSecret)
	form.Set("code", request.Code)
	form.Set("redirect_uri", s.cfg.GitHubCallbackURL)
	form.Set("code_verifier", request.CodeVerifier)

	s.forwardTokenRequest(w, r.Context(), form)
}

func (s *server) refresh(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w)
		return
	}

	var request struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := decodeJSON(w, r, &request); err != nil {
		return
	}

	request.RefreshToken = strings.TrimSpace(request.RefreshToken)
	if !strings.HasPrefix(request.RefreshToken, "ghr_") || len(request.RefreshToken) > 1024 {
		writeError(w, http.StatusBadRequest, "invalid_refresh_token", "")
		return
	}

	form := url.Values{}
	form.Set("client_id", s.cfg.ClientID)
	form.Set("client_secret", s.cfg.ClientSecret)
	form.Set("grant_type", "refresh_token")
	form.Set("refresh_token", request.RefreshToken)

	s.forwardTokenRequest(w, r.Context(), form)
}

func (s *server) revoke(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w)
		return
	}

	var request struct {
		AccessToken string `json:"access_token"`
	}
	if err := decodeJSON(w, r, &request); err != nil {
		return
	}

	request.AccessToken = strings.TrimSpace(request.AccessToken)
	if !strings.HasPrefix(request.AccessToken, "ghu_") || len(request.AccessToken) > 1024 {
		writeError(w, http.StatusBadRequest, "invalid_access_token", "")
		return
	}

	body, _ := json.Marshal(map[string]string{"access_token": request.AccessToken})
	endpoint := strings.TrimRight(s.cfg.GitHubAPIBase, "/") +
		"/applications/" + url.PathEscape(s.cfg.ClientID) + "/token"

	req, err := http.NewRequestWithContext(
		r.Context(),
		http.MethodDelete,
		endpoint,
		strings.NewReader(string(body)),
	)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "request_build_failed", "")
		return
	}

	basic := base64.StdEncoding.EncodeToString(
		[]byte(s.cfg.ClientID + ":" + s.cfg.ClientSecret),
	)
	req.Header.Set("Authorization", "Basic "+basic)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-GitHub-Api-Version", githubAPIVersion)
	req.Header.Set("User-Agent", "Nexora-Git-Auth-Broker")

	response, err := s.client.Do(req)
	if err != nil {
		writeError(w, http.StatusBadGateway, "github_unavailable", "")
		return
	}
	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 32<<10))

	if response.StatusCode != http.StatusNoContent {
		writeError(w, http.StatusBadGateway, "github_revoke_failed", "")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (s *server) forwardTokenRequest(w http.ResponseWriter, requestContext context.Context, form url.Values) {
	endpoint := strings.TrimRight(s.cfg.GitHubOAuthBase, "/") + "/login/oauth/access_token"

	ctx, cancel := context.WithTimeout(requestContext, 15*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		endpoint,
		strings.NewReader(form.Encode()),
	)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "request_build_failed", "")
		return
	}

	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("User-Agent", "Nexora-Git-Auth-Broker")

	response, err := s.client.Do(req)
	if err != nil {
		writeError(w, http.StatusBadGateway, "github_unavailable", "")
		return
	}
	defer response.Body.Close()

	var token tokenResponse
	decoder := json.NewDecoder(io.LimitReader(response.Body, 64<<10))
	if err := decoder.Decode(&token); err != nil {
		writeError(w, http.StatusBadGateway, "invalid_github_response", "")
		return
	}

	if response.StatusCode < 200 || response.StatusCode >= 300 || token.Error != "" {
		description := token.ErrorDescription
		if description == "" {
			description = token.Error
		}
		writeError(w, http.StatusBadRequest, "github_oauth_error", description)
		return
	}

	if token.AccessToken == "" {
		writeError(w, http.StatusBadGateway, "missing_access_token", "")
		return
	}

	writeJSON(w, http.StatusOK, token)
}

func decodeJSON(w http.ResponseWriter, r *http.Request, destination any) error {
	r.Body = http.MaxBytesReader(w, r.Body, 8<<10)
	defer r.Body.Close()

	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()

	if err := decoder.Decode(destination); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", "")
		return err
	}
	return nil
}

func writeError(w http.ResponseWriter, status int, code, description string) {
	payload := map[string]string{"error": code}
	if description != "" {
		payload["error_description"] = description
	}
	writeJSON(w, status, payload)
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func methodNotAllowed(w http.ResponseWriter) {
	writeError(w, http.StatusMethodNotAllowed, "method_not_allowed", "")
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Pragma", "no-cache")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		next.ServeHTTP(w, r)
	})
}

