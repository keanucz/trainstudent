package gmail

import (
	"context"
	"encoding/base64"
	"fmt"
	"regexp"
	"strings"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
	"google.golang.org/api/gmail/v1"
	"google.golang.org/api/option"
)

// Pre-compiled regex patterns
var (
	htmlTagsRe = regexp.MustCompile(`<[^>]*>`)
	
	northernPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)SAVE50[-_]?[A-Z0-9]{4,8}`),
		regexp.MustCompile(`(?i)NORTHERN50[-_]?[A-Z0-9]{4,8}`),
		regexp.MustCompile(`(?i)NR50[-_]?[A-Z0-9]{4,8}`),
		regexp.MustCompile(`(?i)(?:code|voucher|discount)[\s:]+([A-Z0-9]{6,12})`),
	}
	
	avantiPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)AVANTI[-_]?[A-Z0-9]{4,8}`),
		regexp.MustCompile(`(?i)AVANTICLUB[-_]?[A-Z0-9]{4,8}`),
		regexp.MustCompile(`(?i)(?:code|voucher)[\s:]+([A-Z0-9]{6,12})`),
	}
)

// OAuthConfig holds Gmail OAuth2 configuration
type OAuthConfig struct {
	ClientID     string
	ClientSecret string
	RedirectURL  string
	RefreshToken string
}

// PollerResult represents an extracted discount code
type PollerResult struct {
	Code       string `json:"code"`
	Operator   string `json:"operator"`
	Subject    string `json:"subject"`
	ReceivedAt string `json:"receivedAt"`
	ExpiresAt  string `json:"expiresAt"`
}

// Poller polls Gmail for discount codes
type Poller struct {
	service *gmail.Service
}

// NewPoller creates a new Gmail poller with OAuth2
func NewPoller(config *OAuthConfig) (*Poller, error) {
	ctx := context.Background()

	oauthConfig := &oauth2.Config{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		RedirectURL:  config.RedirectURL,
		Scopes:       []string{gmail.GmailReadonlyScope},
		Endpoint:     google.Endpoint,
	}

	tokenSource := oauthConfig.TokenSource(ctx, &oauth2.Token{RefreshToken: config.RefreshToken})
	service, err := gmail.NewService(ctx, option.WithTokenSource(tokenSource))
	if err != nil {
		return nil, fmt.Errorf("failed to create Gmail service: %w", err)
	}

	return &Poller{service: service}, nil
}

// PollForNorthernCode polls for Northern Railway 50% discount codes
func (p *Poller) PollForNorthernCode(ctx context.Context, email string, maxWaitSeconds int) (*PollerResult, error) {
	query := fmt.Sprintf("from:northernrailway.co.uk to:%s subject:(50%% OR discount OR code) newer_than:1h", email)
	expiry := todayEndOfDay()
	return p.pollForCode(ctx, query, "NORTHERN", 1*time.Hour, 5*time.Second, maxWaitSeconds, expiry, northernPatterns)
}

// PollForAvantiCode polls for Avanti Club discount codes
func (p *Poller) PollForAvantiCode(ctx context.Context, email string, maxWaitSeconds int) (*PollerResult, error) {
	query := fmt.Sprintf("from:avantiwestcoast.co.uk to:%s subject:(avanti club OR discount OR code) newer_than:24h", email)
	return p.pollForCode(ctx, query, "AVANTI", 24*time.Hour, 10*time.Second, maxWaitSeconds, "", avantiPatterns)
}

func (p *Poller) pollForCode(ctx context.Context, query, operator string, maxAge, interval time.Duration, maxWaitSec int, expiresAt string, patterns []*regexp.Regexp) (*PollerResult, error) {
	deadline := time.Now().Add(time.Duration(maxWaitSec) * time.Second)

	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		default:
		}

		msgs, err := p.service.Users.Messages.List("me").Q(query).MaxResults(5).Do()
		if err != nil {
			return nil, fmt.Errorf("failed to list messages: %w", err)
		}

		for _, msg := range msgs.Messages {
			full, err := p.service.Users.Messages.Get("me", msg.Id).Do()
			if err != nil {
				continue
			}

			msgTime := time.Unix(full.InternalDate/1000, 0)
			if time.Since(msgTime) > maxAge {
				continue
			}

			subject := extractHeader(full, "Subject")
			body := extractMessageBody(full)
			code := extractCode(body, patterns)
			
			if code != "" {
				return &PollerResult{
					Code:       code,
					Operator:   operator,
					Subject:    subject,
					ReceivedAt: msgTime.Format(time.RFC3339),
					ExpiresAt:  expiresAt,
				}, nil
			}
		}

		time.Sleep(interval)
	}

	return nil, fmt.Errorf("no discount code found within %d seconds", maxWaitSec)
}

func extractHeader(msg *gmail.Message, name string) string {
	for _, h := range msg.Payload.Headers {
		if h.Name == name {
			return h.Value
		}
	}
	return ""
}

func extractMessageBody(msg *gmail.Message) string {
	var b strings.Builder

	if msg.Payload.Body != nil && msg.Payload.Body.Data != "" {
		if decoded, err := base64.URLEncoding.DecodeString(msg.Payload.Body.Data); err == nil {
			b.Write(decoded)
		}
	}

	for _, part := range msg.Payload.Parts {
		if part.Body == nil || part.Body.Data == "" {
			continue
		}
		decoded, err := base64.URLEncoding.DecodeString(part.Body.Data)
		if err != nil {
			continue
		}
		if part.MimeType == "text/html" {
			b.WriteString(htmlTagsRe.ReplaceAllString(string(decoded), " "))
		} else {
			b.Write(decoded)
		}
	}

	return b.String()
}

func extractCode(body string, patterns []*regexp.Regexp) string {
	for _, p := range patterns {
		if m := p.FindString(body); m != "" {
			return strings.ToUpper(m)
		}
	}
	return ""
}

func todayEndOfDay() string {
	now := time.Now()
	return time.Date(now.Year(), now.Month(), now.Day(), 23, 59, 59, 0, now.Location()).Format(time.RFC3339)
}
