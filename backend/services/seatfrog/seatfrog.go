package seatfrog

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const (
	BaseURL   = "https://api.seatfrog.com"
	UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
)

// Client is the Seatfrog API client
type Client struct {
	httpClient *http.Client
}

// SearchResponse is the response from Seatfrog search
type SearchResponse struct {
	Session  string `json:"session"`
	SearchID string `json:"searchId"`
	Bands    []Band `json:"bands"`
	Outbound Day    `json:"outbound"`
}

type Band struct {
	ID          string   `json:"id"`
	Description []string `json:"description"`
	Sequence    int      `json:"sequence"`
}

type Day struct {
	Origin          string    `json:"origin"`
	Destination     string    `json:"destination"`
	AverageDuration string    `json:"averageDuration"`
	Days            []DayInfo `json:"days"`
}

type DayInfo struct {
	Date             string            `json:"date"`
	AverageDuration  string            `json:"averageDuration"`
	AverageDurations map[string]string `json:"averageDurations"`
	Timebands        []Timeband        `json:"timebands"`
}

type Timeband struct {
	ID          string  `json:"id"`
	IsAvailable bool    `json:"isAvailable"`
	Price       *Price  `json:"price,omitempty"`
	Prices      []Price `json:"prices,omitempty"`
}

type Price struct {
	Fare            string      `json:"fare"`
	IndividualPrice MoneyAmount `json:"individualPrice"`
	Quantity        int         `json:"quantity"`
	Total           MoneyAmount `json:"total"`
	TOC             string      `json:"toc"` // Train Operating Company code
}

type MoneyAmount struct {
	Currency  string `json:"currency"`
	Value     int    `json:"value"` // In pence
	Formatted string `json:"formatted"`
}

// SecretfareDeal represents a parsed Seatfrog deal
type SecretfareDeal struct {
	Origin          string  `json:"origin"`
	Destination     string  `json:"destination"`
	Date            string  `json:"date"`
	Timeband        string  `json:"timeband"` // "morning", "afternoon", "evening"
	TimebandDesc    string  `json:"timebandDescription"`
	Operator        string  `json:"operator"`
	OperatorCode    string  `json:"operatorCode"`
	Price           float64 `json:"price"` // In pounds
	PriceFormatted  string  `json:"priceFormatted"`
	FareCode        string  `json:"fareCode"`
	AverageDuration string  `json:"averageDuration"`
	BookingURL      string  `json:"bookingUrl"`
}

// TOC codes to names
var TOCNames = map[string]string{
	"EM": "East Midlands Railway",
	"VT": "Avanti West Coast",
	"XC": "CrossCountry",
	"GC": "Grand Central",
	"LN": "LNER",
	"TP": "TransPennine Express",
	"NT": "Northern",
	"GW": "Great Western Railway",
	"SR": "ScotRail",
}

// NewClient creates a new Seatfrog client
func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// SearchSecretfares searches for Seatfrog Superfare deals
func (c *Client) SearchSecretfares(ctx context.Context, origin, destination, date string, numPassengers int) (*SearchResponse, error) {
	url := fmt.Sprintf("%s/flex/seatfrogweb/search/%s/%s/%s?numPassengers=%d",
		BaseURL, date, origin, destination, numPassengers)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Origin", "https://secretfare.seatfrog.com")
	req.Header.Set("Referer", "https://secretfare.seatfrog.com/")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API returned %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var searchResp SearchResponse
	if err := json.Unmarshal(body, &searchResp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	return &searchResp, nil
}

// GetStations searches for station suggestions
func (c *Client) GetStations(ctx context.Context, query string) ([]string, error) {
	url := fmt.Sprintf("%s/stations/flex/SecretfareWeb?origin=%s", BaseURL, query)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var stations []struct {
		Code string `json:"code"`
		Name string `json:"name"`
	}
	if err := json.Unmarshal(body, &stations); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	var result []string
	for _, s := range stations {
		result = append(result, s.Code)
	}
	return result, nil
}

// ParseDeals extracts deals from a search response
func ParseDeals(resp *SearchResponse) []SecretfareDeal {
	var deals []SecretfareDeal

	bandDescs := make(map[string]string)
	for _, band := range resp.Bands {
		if len(band.Description) >= 2 {
			bandDescs[band.ID] = fmt.Sprintf("%s (%s)", band.Description[0], band.Description[1])
		} else if len(band.Description) >= 1 {
			bandDescs[band.ID] = band.Description[0]
		}
	}

	for _, dayInfo := range resp.Outbound.Days {
		for _, tb := range dayInfo.Timebands {
			if !tb.IsAvailable {
				continue
			}

			// Check both Price and Prices fields
			var prices []Price
			if tb.Price != nil {
				prices = append(prices, *tb.Price)
			}
			prices = append(prices, tb.Prices...)

			for _, price := range prices {
				deal := SecretfareDeal{
					Origin:          resp.Outbound.Origin,
					Destination:     resp.Outbound.Destination,
					Date:            dayInfo.Date,
					Timeband:        tb.ID,
					TimebandDesc:    bandDescs[tb.ID],
					OperatorCode:    price.TOC,
					Operator:        TOCNames[price.TOC],
					Price:           float64(price.Total.Value) / 100.0,
					PriceFormatted:  price.Total.Formatted,
					FareCode:        price.Fare,
					AverageDuration: dayInfo.AverageDuration,
					BookingURL:      fmt.Sprintf("https://secretfare.seatfrog.com/book/%s", price.Fare),
				}
				if deal.Operator == "" {
					deal.Operator = price.TOC
				}
				deals = append(deals, deal)
			}
		}
	}

	return deals
}

// FindCheapestDeal returns the cheapest deal from the list
func FindCheapestDeal(deals []SecretfareDeal) *SecretfareDeal {
	if len(deals) == 0 {
		return nil
	}

	cheapest := &deals[0]
	for i := 1; i < len(deals); i++ {
		if deals[i].Price < cheapest.Price {
			cheapest = &deals[i]
		}
	}
	return cheapest
}
