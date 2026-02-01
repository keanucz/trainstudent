package handlers

import (
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"

	"trainstudent/services/trainline"
)

// TrainService represents a live train service
type TrainService struct {
	ServiceID          string  `json:"serviceId"`
	Operator           string  `json:"operator"`
	OperatorCode       string  `json:"operatorCode"`
	Origin             string  `json:"origin"`
	Destination        string  `json:"destination"`
	ScheduledDeparture string  `json:"scheduledDeparture"`
	ExpectedDeparture  string  `json:"expectedDeparture"`
	ScheduledArrival   string  `json:"scheduledArrival"`
	ExpectedArrival    string  `json:"expectedArrival"`
	Platform           string  `json:"platform,omitempty"`
	Status             string  `json:"status"`
	DelayMinutes       int     `json:"delayMinutes"`
	Duration           int     `json:"duration"` // in minutes
	Changes            int     `json:"changes"`
	Price              float64 `json:"price,omitempty"`
	DiscountedPrice    float64 `json:"discountedPrice,omitempty"`
	HasDiscount        bool    `json:"hasDiscount"`
}

// OperatorCodes maps carrier URNs to friendly names
var OperatorNames = map[string]string{
	"NT": "Northern",
	"TP": "TransPennine Express",
	"EM": "East Midlands Railway",
	"VT": "Avanti West Coast",
	"XC": "CrossCountry",
	"GC": "Grand Central",
	"LM": "London Northwestern",
	"GN": "Great Northern",
	"TL": "Thameslink",
	"SE": "Southeastern",
	"SW": "South Western Railway",
	"GW": "Great Western Railway",
	"LN": "LNER",
	"SR": "ScotRail",
}

// GetLiveTrains handles GET /api/trains/live
func GetLiveTrains(c *fiber.Ctx) error {
	from := c.Query("from", "sheffield")
	to := c.Query("to", "manchester")
	promoCode := c.Query("promoCode", "")

	// Normalize station names
	from = strings.ToLower(from)
	to = strings.ToLower(to)

	// Get current time
	now := time.Now()
	dateTime := now.Format("2006-01-02T15:04:05")

	// Create Trainline client
	client := trainline.NewClient()

	// Build search request
	req := trainline.BuildSearchRequest(from, to, dateTime, promoCode, "YNG") // YNG = 16-25 Railcard

	// Perform search
	resp, err := client.Search(c.Context(), req)
	if err != nil {
		// Return mock data if API fails
		return c.JSON(fiber.Map{
			"services":   getMockServices(from, to, now),
			"from":       from,
			"to":         to,
			"lastUpdate": now.Format(time.RFC3339),
			"source":     "mock",
			"error":      err.Error(),
		})
	}

	// Convert Trainline response to our format
	services := make([]TrainService, 0)
	for _, journey := range resp.Journeys {
		service := TrainService{
			ServiceID:          journey.ID,
			Origin:             from,
			Destination:        to,
			ScheduledDeparture: formatTime(journey.DepartureTime),
			ExpectedDeparture:  formatTime(journey.DepartureTime), // Update with live data if available
			ScheduledArrival:   formatTime(journey.ArrivalTime),
			ExpectedArrival:    formatTime(journey.ArrivalTime),
			Status:             journey.Status,
			DelayMinutes:       0,
			Duration:           journey.Duration,
			Changes:            journey.Changes,
		}

		// Extract operator from first leg
		if len(journey.Legs) > 0 {
			leg := journey.Legs[0]
			// Extract operator code from URN like "urn:trainline:atoc:carrier:NT"
			parts := strings.Split(leg.Carrier, ":")
			if len(parts) > 0 {
				code := parts[len(parts)-1]
				service.OperatorCode = code
				if name, ok := OperatorNames[code]; ok {
					service.Operator = name
				} else {
					service.Operator = code
				}
			}
			service.Platform = leg.Platform
		}

		// Look up price from fares
		for _, link := range resp.Links {
			if link.JourneyId == journey.ID {
				service.Price = link.Price
				break
			}
		}

		// Check if any fares have discounts applied
		for _, fare := range resp.Fares {
			if fare.VoucherApplied {
				service.HasDiscount = true
				service.DiscountedPrice = fare.Price
				if fare.OriginalPrice > 0 {
					service.Price = fare.OriginalPrice
				}
				break
			}
		}

		services = append(services, service)
	}

	return c.JSON(fiber.Map{
		"services":   services,
		"from":       from,
		"to":         to,
		"lastUpdate": now.Format(time.RFC3339),
		"source":     "trainline",
		"promoCode":  promoCode,
	})
}

// GetLiveTrainsWithPromo handles GET /api/trains/live/discounted
// Automatically applies the best available promo code
func GetLiveTrainsWithPromo(c *fiber.Ctx) error {
	// Use our hardcoded working promo code
	promoCode := trainline.GetWorkingPromoCode()

	// Set the promo code in query and forward to main handler
	c.Request().URI().QueryArgs().Set("promoCode", promoCode)

	return GetLiveTrains(c)
}

// formatTime extracts just the time portion from an ISO datetime
func formatTime(isoDateTime string) string {
	t, err := time.Parse(time.RFC3339, isoDateTime)
	if err != nil {
		// Try alternative format
		t, err = time.Parse("2006-01-02T15:04:05", isoDateTime)
		if err != nil {
			return isoDateTime
		}
	}
	return t.Format("15:04")
}

// getMockServices returns mock train services for testing
func getMockServices(from, to string, now time.Time) []TrainService {
	return []TrainService{
		{
			ServiceID:          "1A01",
			Operator:           "Northern",
			OperatorCode:       "NT",
			Origin:             from,
			Destination:        to,
			ScheduledDeparture: now.Add(15 * time.Minute).Format("15:04"),
			ExpectedDeparture:  now.Add(15 * time.Minute).Format("15:04"),
			ScheduledArrival:   now.Add(75 * time.Minute).Format("15:04"),
			ExpectedArrival:    now.Add(75 * time.Minute).Format("15:04"),
			Platform:           "2",
			Status:             "ON_TIME",
			DelayMinutes:       0,
			Duration:           60,
			Changes:            0,
			Price:              12.50,
			DiscountedPrice:    6.25,
			HasDiscount:        true,
		},
		{
			ServiceID:          "1B02",
			Operator:           "TransPennine Express",
			OperatorCode:       "TP",
			Origin:             from,
			Destination:        to,
			ScheduledDeparture: now.Add(30 * time.Minute).Format("15:04"),
			ExpectedDeparture:  now.Add(35 * time.Minute).Format("15:04"),
			ScheduledArrival:   now.Add(80 * time.Minute).Format("15:04"),
			ExpectedArrival:    now.Add(85 * time.Minute).Format("15:04"),
			Platform:           "",
			Status:             "DELAYED",
			DelayMinutes:       5,
			Duration:           50,
			Changes:            0,
			Price:              18.00,
		},
		{
			ServiceID:          "1C03",
			Operator:           "Northern",
			OperatorCode:       "NT",
			Origin:             from,
			Destination:        to,
			ScheduledDeparture: now.Add(60 * time.Minute).Format("15:04"),
			ExpectedDeparture:  now.Add(60 * time.Minute).Format("15:04"),
			ScheduledArrival:   now.Add(120 * time.Minute).Format("15:04"),
			ExpectedArrival:    now.Add(120 * time.Minute).Format("15:04"),
			Platform:           "",
			Status:             "ON_TIME",
			DelayMinutes:       0,
			Duration:           60,
			Changes:            0,
			Price:              10.00,
			DiscountedPrice:    5.00,
			HasDiscount:        true,
		},
	}
}
