package convex

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"
)

// Client interacts with the Convex API
type Client struct {
	URL        string
	httpClient *http.Client
}

// NewClient creates a new Convex client
func NewClient() *Client {
	url := os.Getenv("CONVEX_URL")
	if url == "" {
		// Default or log warning
		url = "https://your-deployment.convex.cloud"
	}
	// Ensure URL ends with /api
	if url[len(url)-1] == '/' {
		url = url[:len(url)-1]
	}

	return &Client{
		URL:        url,
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

// MutationRequest represents the body of a mutation request
type MutationRequest struct {
	Path string      `json:"path"`
	Args interface{} `json:"args"`
}

// Mutation performs a mutation on the Convex backend
func (c *Client) Mutation(module string, function string, args interface{}) error {
	path := fmt.Sprintf("%s:%s", module, function)

	reqBody := MutationRequest{
		Path: path,
		Args: args,
	}

	jsonBody, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("failed to marshal args: %w", err)
	}

	apiURL := fmt.Sprintf("%s/api/mutation", c.URL)
	req, err := http.NewRequest("POST", apiURL, bytes.NewBuffer(jsonBody))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != 200 {
		return fmt.Errorf("convex error (%d): %s", resp.StatusCode, string(body))
	}

	return nil
}

// Query performs a query on the Convex backend
// result should be a pointer to the struct where data will be unmarshaled
func (c *Client) Query(module string, function string, args interface{}, result interface{}) error {
	path := fmt.Sprintf("%s:%s", module, function)

	reqBody := MutationRequest{ // Query uses same struct format
		Path: path,
		Args: args,
	}

	jsonBody, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("failed to marshal args: %w", err)
	}

	apiURL := fmt.Sprintf("%s/api/query", c.URL)
	req, err := http.NewRequest("POST", apiURL, bytes.NewBuffer(jsonBody))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("convex error (%d): %s", resp.StatusCode, string(body))
	}

	// Convex returns { "status": "success", "value": ... }
	type ConvexResponse struct {
		Status string          `json:"status"`
		Value  json.RawMessage `json:"value"`
		Error  string          `json:"errorMessage"`
	}

	var convexResp ConvexResponse
	if err := json.NewDecoder(resp.Body).Decode(&convexResp); err != nil {
		return fmt.Errorf("failed to decode response: %w", err)
	}

	if convexResp.Status != "success" {
		return fmt.Errorf("convex application error: %s", convexResp.Error)
	}

	if err := json.Unmarshal(convexResp.Value, result); err != nil {
		return fmt.Errorf("failed to unmarshal value: %w", err)
	}

	return nil
}
