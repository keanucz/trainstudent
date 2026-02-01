package northern

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

var (
	emailRegex  = regexp.MustCompile(`^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`)
	httpClient  = &http.Client{Timeout: 30 * time.Second}
)

// CodeResult represents the result of a Northern 50% code generation
type CodeResult struct {
	Code    string `json:"code"`
	Expires string `json:"expires"`
	Message string `json:"message"`
}

// ValidateEmail checks if email is valid format
func ValidateEmail(email string) bool {
	return emailRegex.MatchString(email) && len(email) <= 254
}

// GenerateCode submits the Northern 50% code generation form
// This replicates the Salesforce Aura flow captured in northern.har
func GenerateCode(ctx context.Context, firstName, lastName, email string) (*CodeResult, error) {
	// Input validation
	if !ValidateEmail(email) {
		return nil, fmt.Errorf("invalid email format")
	}
	if len(firstName) == 0 || len(firstName) > 100 {
		return nil, fmt.Errorf("firstName must be 1-100 characters")
	}
	if len(lastName) > 100 {
		return nil, fmt.Errorf("lastName must be 0-100 characters")
	}

	// Build the Aura action message
	auraMessage, err := buildAuraMessage(firstName, lastName, email)
	if err != nil {
		return nil, fmt.Errorf("failed to build aura message: %w", err)
	}

	// Build Aura context
	auraContext, err := buildAuraContext()
	if err != nil {
		return nil, fmt.Errorf("failed to build aura context: %w", err)
	}
	
	// Build form data
	formData := url.Values{}
	formData.Set("message", auraMessage)
	formData.Set("aura.context", auraContext)
	formData.Set("aura.pageURI", "/s/app-50")
	formData.Set("aura.token", "null")

	// Create request with context
	reqURL := "https://help.northernrailway.co.uk/s/sfsites/aura?r=6&aura.FlowRuntimeConnect.navigateFlow=1"
	req, err := http.NewRequestWithContext(ctx, "POST", reqURL, strings.NewReader(formData.Encode()))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers matching the HAR capture
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-GB,en;q=0.9")
	req.Header.Set("Origin", "https://help.northernrailway.co.uk")
	req.Header.Set("Referer", "https://help.northernrailway.co.uk/s/app-50")
	req.Header.Set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
	
	// X-SFDC headers for Salesforce
	req.Header.Set("X-SFDC-Page-Scope-Id", generateScopeId())
	req.Header.Set("X-SFDC-Request-Id", generateRequestId())

	// Execute request using shared client
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	// Read response
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// Check for success
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(body))
	}

	// Parse response - look for success indicator
	if strings.Contains(string(body), "successfully registered") {
		// Success - code will be emailed
		// Calculate expiry (23:59 today)
		now := time.Now()
		expiry := time.Date(now.Year(), now.Month(), now.Day(), 23, 59, 59, 0, now.Location())

		return &CodeResult{
			Code:    "PENDING_EMAIL", // Code is sent via email, need to poll Gmail
			Expires: expiry.Format(time.RFC3339),
			Message: "Code has been sent to " + email + ". Check your inbox.",
		}, nil
	}

	// Check for error in response
	if strings.Contains(string(body), "ERROR") {
		return nil, fmt.Errorf("Salesforce returned an error: %s", extractError(body))
	}

	return nil, fmt.Errorf("unexpected response: %s", string(body))
}

func buildAuraMessage(firstName, lastName, email string) (string, error) {
	// Build the Aura action payload
	action := map[string]interface{}{
		"actions": []map[string]interface{}{
			{
				"id":                "217;a",
				"descriptor":        "aura://FlowRuntimeConnectController/ACTION$navigateFlow",
				"callingDescriptor": "UNKNOWN",
				"params": map[string]interface{}{
					"request": map[string]interface{}{
						"action": "NEXT",
						"fields": []map[string]interface{}{
							{
								"field":     "FirstName",
								"value":     firstName,
								"isVisible": true,
							},
							{
								"field":     "LastName",
								"value":     lastName,
								"isVisible": true,
							},
							{
								"field":     "Email_Address",
								"value":     email,
								"isVisible": true,
							},
							{
								"field":     "Marketing_Consent",
								"value":     false,
								"isVisible": true,
							},
						},
					},
				},
			},
		},
	}

	jsonBytes, err := json.Marshal(action)
	if err != nil {
		return "", fmt.Errorf("failed to marshal action: %w", err)
	}
	return string(jsonBytes), nil
}

func buildAuraContext() (string, error) {
	context := map[string]interface{}{
		"mode":    "PROD",
		"fwuid":   "MTEtMjAyNC0wMS0yNlQxNjowNTowMy4wMDBa",
		"app":     "siteforce:communityApp",
		"loaded": map[string]string{
			"APPLICATION@markup://siteforce:communityApp": "YoVv7wLKqx7W2M7rrE3tOg",
		},
		"dn": []string{},
		"globals": map[string]interface{}{
			"srcdoc": true,
		},
		"uad": true,
	}

	jsonBytes, err := json.Marshal(context)
	if err != nil {
		return "", fmt.Errorf("failed to marshal context: %w", err)
	}
	return string(jsonBytes), nil
}

func generateScopeId() string {
	// Generate a UUID-like scope ID
	return fmt.Sprintf("%s-%s-%s-%s-%s",
		randomHex(8), randomHex(4), randomHex(4), randomHex(4), randomHex(12))
}

func generateRequestId() string {
	// Generate request ID in Salesforce format
	return fmt.Sprintf("%d;%s", time.Now().UnixNano()/1000000, randomHex(8))
}

func randomHex(length int) string {
	bytes := make([]byte, (length+1)/2)
	if _, err := rand.Read(bytes); err != nil {
		// Fallback to less secure method if crypto/rand fails
		for i := range bytes {
			bytes[i] = byte(time.Now().UnixNano() % 256)
		}
	}
	return hex.EncodeToString(bytes)[:length]
}

func extractError(body []byte) string {
	// Try to extract error message from Aura response
	var response map[string]interface{}
	if err := json.Unmarshal(body, &response); err == nil {
		if actions, ok := response["actions"].([]interface{}); ok && len(actions) > 0 {
			if action, ok := actions[0].(map[string]interface{}); ok {
				if state, ok := action["state"].(string); ok && state == "ERROR" {
					if errors, ok := action["error"].([]interface{}); ok && len(errors) > 0 {
						if errObj, ok := errors[0].(map[string]interface{}); ok {
							if msg, ok := errObj["message"].(string); ok {
								return msg
							}
						}
					}
				}
			}
		}
	}
	return "Unknown error"
}
