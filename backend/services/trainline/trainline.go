package trainline

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const (
	BaseURL               = "https://api.thetrainline.com"
	SearchEndpoint        = "/gateway/search"
	CallingPointsEndpoint = "/travelserviceinformation/services/bulkcallingpoints"
	UserAgent             = "nrMobileAppAndroid/6.6.11"
)

// Hardcoded working promo codes for Northern
var WorkingPromoCodes = []string{
	"NORAPP50-NJR2KJ",
	"NORAPP50-HTZZZW",
}

// SearchRequest represents the journey search request
type SearchRequest struct {
	Origin             string      `json:"origin"`
	Destination        string      `json:"destination"`
	OutwardJourney     JourneyTime `json:"outwardJourney"`
	Passengers         []Passenger `json:"passengers"`
	NumberOfAdults     int         `json:"NumberOfAdults"`
	NumberOfChildren   int         `json:"NumberOfChildren"`
	ApplyFareDiscounts bool        `json:"applyFareDiscounts"`
	DiscountCards      []Card      `json:"discountCards,omitempty"`
	JourneyType        string      `json:"journeyType"`
	IncludeDisruptions string      `json:"includeDisruptions"`
	Vouchers           []Voucher   `json:"vouchers,omitempty"`
}

type JourneyTime struct {
	Type     string `json:"Type"`
	DateTime string `json:"DateTime"`
}

type Passenger struct {
	ID          int    `json:"id"`
	DateOfBirth string `json:"dateOfBirth"`
}

type Card struct {
	Code string `json:"code"`
}

type Voucher struct {
	Code string `json:"code"`
}

// CallingPointRequest represents a request for train calling points
type CallingPointRequest struct {
	CorrelationID         string `json:"correlationId"`
	Carrier               string `json:"carrier"`
	TimetableID           string `json:"timetableId"`
	CustomerDestination   string `json:"customerDestination"`
	CustomerDepartureDate string `json:"customerDepartureDate"`
	CustomerOrigin        string `json:"customerOrigin"`
	IncludeEarlyTimes     bool   `json:"includeEarlyTimes"`
}

// SearchResponse represents the search API response
type SearchResponse struct {
	SearchId string    `json:"searchId"`
	Journeys []Journey `json:"journeys"`
	Links    []Link    `json:"links"`
	Fares    []Fare    `json:"fares"`
	Carriers []Carrier `json:"carriers"`
}

type Journey struct {
	ID              string   `json:"id"`
	DepartureTime   string   `json:"departureTime"`
	ArrivalTime     string   `json:"arrivalTime"`
	Origin          Location `json:"origin"`
	Destination     Location `json:"destination"`
	OriginName      string   `json:"originName"`
	DestinationName string   `json:"destinationName"`
	Duration        int      `json:"duration"` // in minutes
	Legs            []Leg    `json:"legs"`
	Status          string   `json:"status"`
	Changes         int      `json:"changes"`
}

type Location struct {
	Urn  string `json:"urn"`
	Name string `json:"name"`
}

type Leg struct {
	ID            string   `json:"id"`
	Carrier       string   `json:"carrier"`
	CarrierName   string   `json:"carrierName"`
	TimetableID   string   `json:"timetableId"`
	DepartureTime string   `json:"departureTime"`
	ArrivalTime   string   `json:"arrivalTime"`
	Origin        Location `json:"origin"`
	Destination   Location `json:"destination"`
	Platform      string   `json:"platform,omitempty"`
	Status        string   `json:"status"`
}

type Link struct {
	JourneyId string  `json:"journeyId"`
	FareId    string  `json:"fareId"`
	Price     float64 `json:"price"`
}

type Fare struct {
	ID             string  `json:"id"`
	Name           string  `json:"name"`
	Price          float64 `json:"price"`
	Currency       string  `json:"currency"`
	Discounted     bool    `json:"discounted"`
	VoucherApplied bool    `json:"voucherApplied"`
	OriginalPrice  float64 `json:"originalPrice,omitempty"`
}

type Carrier struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

// CallingPointResponse represents calling points for a service
type CallingPointResponse struct {
	ServiceID     string         `json:"serviceId"`
	CallingPoints []CallingPoint `json:"callingPoints"`
}

type CallingPoint struct {
	Station       Location `json:"station"`
	ScheduledTime string   `json:"scheduledTime"`
	ExpectedTime  string   `json:"expectedTime,omitempty"`
	Platform      string   `json:"platform,omitempty"`
	Status        string   `json:"status"`
}

// Client represents a Trainline API client
type Client struct {
	httpClient *http.Client
}

// NewClient creates a new Trainline API client
func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// FormatStationURN converts station codes to Trainline URN format
func FormatStationURN(code string) string {
	// Generic format: urn:trainline:generic:loc:SHF6691gb
	// ATOC format: urn:trainline:atoc:loc:SHF6691
	return fmt.Sprintf("urn:trainline:generic:loc:%sgb", code)
}

// FormatStationURNAtoc converts station codes to ATOC URN format
func FormatStationURNAtoc(code string) string {
	return fmt.Sprintf("urn:trainline:atoc:loc:%s", code)
}

// Common UK station codes
var StationCodes = map[string]string{
	"manchester piccadilly": "MAN2968",
	"manchester":            "MAN2968",
	"sheffield":             "SHF6691",
	"leeds":                 "LDS4619",
	"london euston":         "EUS6324",
	"london kings cross":    "KGX4515",
	"london paddington":     "PAD5696",
	"london st pancras":     "STP6430",
	"birmingham new street": "BHM5050",
	"birmingham":            "BHM5050",
	"liverpool lime street": "LIV6555",
	"liverpool":             "LIV6555",
	"edinburgh":             "EDB4517",
	"glasgow central":       "GLC4423",
	"glasgow":               "GLC4423",
	"york":                  "YRK8041",
	"newcastle":             "NCL4874",
	"bristol temple meads":  "BRI4395",
	"bristol":               "BRI4395",
	"cardiff central":       "CDF1010",
	"cardiff":               "CDF1010",
}

// GetStationCode looks up a station code by name
func GetStationCode(name string) string {
	// Normalize the name
	normalized := name
	if code, ok := StationCodes[normalized]; ok {
		return code
	}
	// Return as-is if not found (might already be a code)
	return name
}

// Search performs a journey search
func (c *Client) Search(ctx context.Context, req *SearchRequest) (*SearchResponse, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", BaseURL+SearchEndpoint, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	httpReq.Header.Set("Accept-Encoding", "gzip")
	httpReq.Header.Set("Accept-Language", "en-gb")
	httpReq.Header.Set("User-Agent", UserAgent)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API returned status %d: %s", resp.StatusCode, string(respBody))
	}

	// Handle gzip response
	var reader io.Reader = resp.Body
	if resp.Header.Get("Content-Encoding") == "gzip" {
		gzReader, err := gzip.NewReader(resp.Body)
		if err != nil {
			return nil, fmt.Errorf("failed to create gzip reader: %w", err)
		}
		defer gzReader.Close()
		reader = gzReader
	}

	respBody, err := io.ReadAll(reader)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var searchResp SearchResponse
	if err := json.Unmarshal(respBody, &searchResp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &searchResp, nil
}

// GetCallingPoints retrieves calling points for a service
func (c *Client) GetCallingPoints(ctx context.Context, req []CallingPointRequest) ([]CallingPointResponse, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", BaseURL+CallingPointsEndpoint, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	httpReq.Header.Set("Accept-Encoding", "gzip")
	httpReq.Header.Set("Accept-Language", "en-gb")
	httpReq.Header.Set("User-Agent", UserAgent)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API returned status %d: %s", resp.StatusCode, string(respBody))
	}

	// Handle gzip response
	var reader io.Reader = resp.Body
	if resp.Header.Get("Content-Encoding") == "gzip" {
		gzReader, err := gzip.NewReader(resp.Body)
		if err != nil {
			return nil, fmt.Errorf("failed to create gzip reader: %w", err)
		}
		defer gzReader.Close()
		reader = gzReader
	}

	respBody, err := io.ReadAll(reader)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var cpResp []CallingPointResponse
	if err := json.Unmarshal(respBody, &cpResp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return cpResp, nil
}

// BuildSearchRequest creates a search request for a journey
func BuildSearchRequest(origin, destination, dateTime string, promoCode string, railcardCode string) *SearchRequest {
	req := &SearchRequest{
		Origin:      FormatStationURN(GetStationCode(origin)),
		Destination: FormatStationURN(GetStationCode(destination)),
		OutwardJourney: JourneyTime{
			Type:     "LeaveAfter",
			DateTime: dateTime,
		},
		Passengers: []Passenger{
			{
				ID:          1,
				DateOfBirth: "2000-02-01", // Default student age
			},
		},
		NumberOfAdults:     1,
		NumberOfChildren:   0,
		ApplyFareDiscounts: true,
		JourneyType:        "Single",
		IncludeDisruptions: "true",
	}

	// Add railcard if specified
	if railcardCode != "" {
		req.DiscountCards = []Card{
			{Code: fmt.Sprintf("urn:trainline:atoc:card:%s", railcardCode)},
		}
	}

	// Add promo code if specified
	if promoCode != "" {
		req.Vouchers = []Voucher{
			{Code: promoCode},
		}
	}

	return req
}

// GetWorkingPromoCode returns one of the hardcoded working promo codes
func GetWorkingPromoCode() string {
	// Return the first working code (can rotate if needed)
	return WorkingPromoCodes[0]
}
