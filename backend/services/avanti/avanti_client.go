package avanti

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"
)

// TokenPair holds the extracted Avanti API tokens
type TokenPair struct {
	Clientstamp string `json:"clientstamp"`
	Noncestamp  string `json:"noncestamp"`
	SessionID   string `json:"sessionId"`
	FetchedAt   time.Time `json:"fetchedAt"`
}

// SearchRequest represents an Avanti fare search
type SearchRequest struct {
	DepartureLocationName string `json:"departureLocationName"`
	ArrivalLocationName   string `json:"arrivalLocationName"`
	DepartureLocation     int    `json:"departureLocation"` // NLC code
	ArrivalLocation       int    `json:"arrivalLocation"`   // NLC code
	DepartureTimesStart   string `json:"departureTimesStart"`
	Adult                 int    `json:"adult"`
	Child                 int    `json:"child"`
	UseUnidays            bool   `json:"useUnidays"`
}

// SearchResponse represents the Avanti API response
type SearchResponse struct {
	Data struct {
		SingleTravel struct {
			TravelSolutions []TravelSolution `json:"TravelSolutions"`
		} `json:"SingleTravel"`
	} `json:"Data"`
	Success bool   `json:"Success"`
	Message string `json:"Message"`
}

// TravelSolution represents a single travel option
type TravelSolution struct {
	Operator       int     `json:"Operator"`
	TravelSolId    int     `json:"TravelSolId"`
	SingleFare     float64 `json:"SingleFare"`
	Currency       string  `json:"Currency"`
	Changes        int     `json:"Changes"`
	DepartureTime  string  `json:"DepartureTime"`
	ArrivalTime    string  `json:"ArrivalTime"`
	Duration       string  `json:"Duration"`
	DurationMinute int     `json:"DurationMinute"`
	FareList       []Fare  `json:"FareList"`
}

// Fare represents a fare option
type Fare struct {
	Price          float64 `json:"Price"`
	TicketType     string  `json:"TicketType"`
	TicketClass    string  `json:"TicketClass"`
	MinPrice       bool    `json:"MinPrice"`
	OfferId        int     `json:"OfferId"`
	TicketTypeCode string  `json:"TicketTypeCode"`
}

var (
	cachedTokens *TokenPair
	tokensMutex  sync.RWMutex
	httpClient   = &http.Client{Timeout: 30 * time.Second}
)

// Pre-compiled regex patterns
var (
	noncestampPattern  = regexp.MustCompile(`"LoginNoncestamp"\s*:\s*"([^"]+)"`)
	clientstampPattern = regexp.MustCompile(`"LoginClientstamp"\s*:\s*"([^"]+)"`)
)

// FetchTokens scrapes the Avanti booking page to extract server-rendered tokens
// Based on HAR analysis: tokens are embedded in entryDataContext JavaScript object
func FetchTokens(ctx context.Context) (*TokenPair, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", "https://www.avantiwestcoast.co.uk/travel-information/train-times", nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
	req.Header.Set("Accept-Language", "en-GB,en;q=0.9")
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	html := string(body)

	// Extract LoginNoncestamp and LoginClientstamp from entryDataContext
	noncestampMatch := noncestampPattern.FindStringSubmatch(html)
	clientstampMatch := clientstampPattern.FindStringSubmatch(html)

	if noncestampMatch == nil || clientstampMatch == nil {
		return nil, fmt.Errorf("could not find tokens in page HTML")
	}

	// Extract TAsessionID cookie
	var sessionID string
	for _, cookie := range resp.Cookies() {
		if cookie.Name == "TAsessionID" {
			sessionID = cookie.Value
			break
		}
	}

	tokens := &TokenPair{
		Noncestamp:  noncestampMatch[1],
		Clientstamp: clientstampMatch[1],
		SessionID:   sessionID,
		FetchedAt:   time.Now(),
	}

	// Update cache with mutex protection
	tokensMutex.Lock()
	cachedTokens = tokens
	tokensMutex.Unlock()

	return tokens, nil
}

// GetTokens returns cached tokens or fetches new ones (thread-safe)
func GetTokens(ctx context.Context) (*TokenPair, error) {
	tokensMutex.RLock()
	cached := cachedTokens
	tokensMutex.RUnlock()

	if cached != nil {
		// Check if tokens are still fresh (less than 30 minutes old)
		if time.Since(cached.FetchedAt) < 30*time.Minute {
			return cached, nil
		}
	}
	return FetchTokens(ctx)
}

// SearchWithUnidays searches for fares using UNiDAYS discount
func SearchWithUnidays(ctx context.Context, req SearchRequest) (*SearchResponse, error) {
	tokens, err := GetTokens(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get tokens: %w", err)
	}

	// Build request body
	body := map[string]interface{}{
		"RailCardList":            []interface{}{},
		"DepartureLocationName":   req.DepartureLocationName,
		"ArrivalLocationName":     req.ArrivalLocationName,
		"DepartureLocation":       req.DepartureLocation,
		"ArrivalLocation":         req.ArrivalLocation,
		"PathConstraintLocation":  0,
		"JourneySearchType":       "NEW",
		"Adult":                   req.Adult,
		"Child":                   req.Child,
		"Traveltype":              "DEPARTAFTER",
		"TicketClassFilter":       "",
		"ChangesFilter":           1,
		"OperaterFilter":          0,
		"TravelSolutionDirection": "ONE_WAY",
		"IsReturnRequest":         false,
		"DepartureTimesStart":     req.DepartureTimesStart,
		"ReturnTimesStart":        nil,
		"PromotionCode":           "",
	}

	// Add UNiDAYS promo code if requested
	if req.UseUnidays {
		body["PromotionCode"] = "UNIDAYS"
	}

	jsonBody, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request body: %w", err)
	}

	// Create request with context
	httpReq, err := http.NewRequestWithContext(ctx, "POST", "https://picoapi.avantiwestcoast.co.uk/TravelSolution/NewSearch", strings.NewReader(string(jsonBody)))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers from HAR capture
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json, text/plain, */*")
	httpReq.Header.Set("Accept-Language", "en-GB,en;q=0.9")
	httpReq.Header.Set("Origin", "https://www.avantiwestcoast.co.uk")
	httpReq.Header.Set("Referer", "https://www.avantiwestcoast.co.uk/")
	httpReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

	// Set the extracted tokens
	httpReq.Header.Set("clientstamp", tokens.Clientstamp)
	httpReq.Header.Set("noncestamp", tokens.Noncestamp)

	// Set session cookie
	if tokens.SessionID != "" {
		httpReq.AddCookie(&http.Cookie{
			Name:  "TAsessionID",
			Value: tokens.SessionID,
		})
	}

	resp, err := httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	// Check for auth errors - need to refresh tokens
	if resp.StatusCode == 401 || resp.StatusCode == 403 {
		tokensMutex.Lock()
		cachedTokens = nil // Clear cache
		tokensMutex.Unlock()
		return nil, fmt.Errorf("token expired, please retry")
	}

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var searchResp SearchResponse
	if err := json.Unmarshal(respBody, &searchResp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &searchResp, nil
}
