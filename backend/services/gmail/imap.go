package gmail

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net/mail"
	"regexp"
	"strings"
	"time"

	"github.com/emersion/go-imap"
	"github.com/emersion/go-imap/client"
)

// IMAPConfig holds Gmail IMAP configuration
type IMAPConfig struct {
	Email    string
	Password string // App-specific password
}

// NorthernCodeEmail represents a parsed Northern promo code email
type NorthernCodeEmail struct {
	From       string
	To         string // The plus-addressed email
	Subject    string
	PromoCode  string
	ReceivedAt time.Time
	MessageID  string
}

// IMAPClient wraps the IMAP client for Gmail
type IMAPClient struct {
	config IMAPConfig
	client *client.Client
}

// Constants
const (
	GmailIMAPServer = "imap.gmail.com:993"
	NorthernSender  = "mynorthern@m.northernrailway.co.uk"
)

// Regex to extract promo code from Northern emails
var northernCodeRegex = regexp.MustCompile(`NORAPP50-[A-Z0-9]{6}`)

// NewIMAPClient creates a new Gmail IMAP client
func NewIMAPClient(email, appPassword string) *IMAPClient {
	return &IMAPClient{
		config: IMAPConfig{
			Email:    email,
			Password: appPassword,
		},
	}
}

// Connect establishes connection to Gmail IMAP
func (c *IMAPClient) Connect() error {
	// Connect with TLS
	tlsConfig := &tls.Config{
		ServerName: "imap.gmail.com",
	}

	imapClient, err := client.DialTLS(GmailIMAPServer, tlsConfig)
	if err != nil {
		return fmt.Errorf("failed to connect to Gmail IMAP: %w", err)
	}

	// Login
	if err := imapClient.Login(c.config.Email, c.config.Password); err != nil {
		imapClient.Close()
		return fmt.Errorf("failed to login: %w", err)
	}

	c.client = imapClient
	return nil
}

// Close closes the IMAP connection
func (c *IMAPClient) Close() error {
	if c.client != nil {
		return c.client.Logout()
	}
	return nil
}

// GeneratePlusAddress creates a unique plus-addressed email for Northern signup
func GeneratePlusAddress(baseEmail string) (string, string) {
	// Split email into local and domain parts
	parts := strings.Split(baseEmail, "@")
	if len(parts) != 2 {
		return baseEmail, ""
	}

	// Generate unique tag using timestamp
	tag := fmt.Sprintf("northern%d", time.Now().UnixMilli())
	plusAddress := fmt.Sprintf("%s+%s@%s", parts[0], tag, parts[1])

	return plusAddress, tag
}

// CheckForNorthernCodes searches inbox for Northern promo code emails
func (c *IMAPClient) CheckForNorthernCodes(ctx context.Context, plusAddress string) ([]NorthernCodeEmail, error) {
	if c.client == nil {
		return nil, fmt.Errorf("not connected")
	}

	// Select INBOX
	mbox, err := c.client.Select("INBOX", false)
	if err != nil {
		return nil, fmt.Errorf("failed to select inbox: %w", err)
	}

	if mbox.Messages == 0 {
		return nil, nil
	}

	// Search for emails from Northern sent to our plus address
	criteria := imap.NewSearchCriteria()
	criteria.Header.Add("From", NorthernSender)
	if plusAddress != "" {
		criteria.Header.Add("To", plusAddress)
	}

	// Only look at recent messages (last 24 hours)
	yesterday := time.Now().Add(-24 * time.Hour)
	criteria.Since = yesterday

	uids, err := c.client.Search(criteria)
	if err != nil {
		return nil, fmt.Errorf("search failed: %w", err)
	}

	if len(uids) == 0 {
		return nil, nil
	}

	// Fetch messages
	seqSet := new(imap.SeqSet)
	seqSet.AddNum(uids...)

	section := &imap.BodySectionName{}
	items := []imap.FetchItem{section.FetchItem(), imap.FetchEnvelope}

	messages := make(chan *imap.Message, len(uids))
	if err := c.client.Fetch(seqSet, items, messages); err != nil {
		return nil, fmt.Errorf("fetch failed: %w", err)
	}

	var codes []NorthernCodeEmail
	for msg := range messages {
		if msg == nil || msg.Envelope == nil {
			continue
		}

		// Get body
		var body string
		for _, literal := range msg.Body {
			if literal != nil {
				bodyBytes, err := io.ReadAll(literal)
				if err == nil {
					body = string(bodyBytes)
				}
			}
		}

		// Extract promo code from body
		promoCode := extractPromoCode(body)
		if promoCode == "" {
			continue
		}

		// Get recipient (To address)
		var toAddr string
		if len(msg.Envelope.To) > 0 {
			toAddr = msg.Envelope.To[0].Address()
		}

		codes = append(codes, NorthernCodeEmail{
			From:       NorthernSender,
			To:         toAddr,
			Subject:    msg.Envelope.Subject,
			PromoCode:  promoCode,
			ReceivedAt: msg.Envelope.Date,
			MessageID:  msg.Envelope.MessageId,
		})
	}

	return codes, nil
}

// extractPromoCode finds the NORAPP50-XXXXXX code in email body
func extractPromoCode(body string) string {
	matches := northernCodeRegex.FindStringSubmatch(body)
	if len(matches) > 0 {
		return matches[0]
	}
	return ""
}

// ParseEmailAddress parses an email address and returns local and domain parts
func ParseEmailAddress(email string) (local, domain string, err error) {
	addr, err := mail.ParseAddress(email)
	if err != nil {
		return "", "", err
	}

	parts := strings.Split(addr.Address, "@")
	if len(parts) != 2 {
		return "", "", fmt.Errorf("invalid email format")
	}

	return parts[0], parts[1], nil
}

// IsGmailCompatible checks if an email supports plus addressing (Gmail, Outlook, etc.)
func IsGmailCompatible(email string) bool {
	email = strings.ToLower(email)
	return strings.HasSuffix(email, "@gmail.com") ||
		strings.HasSuffix(email, "@googlemail.com") ||
		strings.HasSuffix(email, "@outlook.com") ||
		strings.HasSuffix(email, "@hotmail.com") ||
		strings.HasSuffix(email, "@live.com")
}
