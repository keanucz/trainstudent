package handlers

import (
	"fmt"
	"time"

	"github.com/gofiber/fiber/v2"
)

// SearchRequest represents the search parameters from the Android app
// Supports both Android format (from/to/datetime) and API format (originCrs/destCrs/departureTime)
type SearchRequest struct {
	// Android app format
	From     string `json:"from"`
	To       string `json:"to"`
	Datetime string `json:"datetime"`
	Railcard string `json:"railcard"`

	// API format (also accepted)
	OriginCrs        string `json:"originCrs"`
	DestCrs          string `json:"destCrs"`
	DepartureTime    string `json:"departureTime"`
	HasRailcard1625  bool   `json:"hasRailcard1625"`
	CanVerifyStudent bool   `json:"canVerifyStudent"`
}

// Normalize converts different field formats to a common format
func (r *SearchRequest) Normalize() {
	// If Android format is used, copy to API format
	if r.From != "" && r.OriginCrs == "" {
		r.OriginCrs = r.From
	}
	if r.To != "" && r.DestCrs == "" {
		r.DestCrs = r.To
	}
	if r.Datetime != "" && r.DepartureTime == "" {
		r.DepartureTime = r.Datetime
	}
	// Handle railcard field
	if r.Railcard == "16-25" || r.Railcard == "1625" {
		r.HasRailcard1625 = true
	}
}

// BookingPlan represents a single booking option
type BookingPlan struct {
	ID              string        `json:"id"`
	NetCost         float64       `json:"netCost"`
	OriginalCost    float64       `json:"originalCost"`
	OperatorCode    string        `json:"operatorCode"`
	OperatorName    string        `json:"operatorName"`
	DepartureTime   string        `json:"departureTime"`
	ArrivalTime     string        `json:"arrivalTime"`
	DurationMinutes int           `json:"durationMinutes"`
	Origin          string        `json:"origin"`
	OriginCrs       string        `json:"originCrs"`
	Destination     string        `json:"destination"`
	DestinationCrs  string        `json:"destinationCrs"`
	RequiredProof   []string      `json:"requiredProof"`
	PromoCode       *string       `json:"promoCode,omitempty"`
	CashbackNetwork *string       `json:"cashbackNetwork,omitempty"`
	CashbackPercent *float64      `json:"cashbackPercent,omitempty"`
	BookingAction   BookingAction `json:"bookingAction"`
	Summary         string        `json:"summary"`
	RiskLevel       string        `json:"riskLevel"`
	DiscountType    string        `json:"discountType"`
	SavingsPercent  float64       `json:"savingsPercent"`
}

type BookingAction struct {
	Type      string  `json:"type"` // "deeplink", "webview", "native", "northern_app_automation"
	URL       string  `json:"url,omitempty"`
	PromoCode *string `json:"promoCode,omitempty"`
	FlowID    string  `json:"flowId,omitempty"`
	// Northern App Automation specific fields
	Origin         string `json:"origin,omitempty"`
	OriginCrs      string `json:"originCrs,omitempty"`
	Destination    string `json:"destination,omitempty"`
	DestinationCrs string `json:"destinationCrs,omitempty"`
	DepartureTime  string `json:"departureTime,omitempty"`
}

// SearchDiscounts handles POST /api/search
func SearchDiscounts(c *fiber.Ctx) error {
	var req SearchRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error":   "Invalid request body",
			"details": err.Error(),
		})
	}

	// Normalize request format
	req.Normalize()

	// Validate required fields
	if req.OriginCrs == "" || req.DestCrs == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "origin and destination are required",
			"hint":  "Use 'from'/'to' or 'originCrs'/'destCrs' fields",
			"received": fiber.Map{
				"from":      req.From,
				"to":        req.To,
				"originCrs": req.OriginCrs,
				"destCrs":   req.DestCrs,
			},
		})
	}

	// Parse departure time
	departureTime := time.Now()
	if req.DepartureTime != "" {
		if parsed, err := time.Parse(time.RFC3339, req.DepartureTime); err == nil {
			departureTime = parsed
		}
	}

	// Build booking plans based on user profile and route
	plans := buildBookingPlans(req, departureTime)

	// Return in Android app expected format
	return c.JSON(fiber.Map{
		"bookingPlans":  plans,
		"plans":         plans, // Also include for API compatibility
		"cheapestIndex": 0,     // First is cheapest
		"searchTime":    time.Now().Format(time.RFC3339),
		"origin":        req.OriginCrs,
		"destination":   req.DestCrs,
	})
}

func buildBookingPlans(req SearchRequest, departureTime time.Time) []BookingPlan {
	var plans []BookingPlan

	// Example: Sheffield (SHF) to Manchester Piccadilly (MAN)
	basePrice := 32.50

	// TPE with TPESTUDENT + Railcard (50% off)
	if req.HasRailcard1625 {
		tpePromo := "TPESTUDENT"
		tcbNetwork := "TOPCASHBACK"
		tcbPercent := 3.4
		tpePrice := basePrice * 0.5 // 50% off
		tpeNetCost := tpePrice * (1 - tcbPercent/100)

		plans = append(plans, BookingPlan{
			ID:              "tpe-student-" + departureTime.Format("150405"),
			NetCost:         tpeNetCost,
			OriginalCost:    basePrice,
			OperatorCode:    "TPE",
			OperatorName:    "TransPennine Express",
			DepartureTime:   departureTime.Format(time.RFC3339),
			ArrivalTime:     departureTime.Add(50 * time.Minute).Format(time.RFC3339),
			DurationMinutes: 50,
			Origin:          "Sheffield",
			OriginCrs:       req.OriginCrs,
			Destination:     "Manchester Piccadilly",
			DestinationCrs:  req.DestCrs,
			RequiredProof:   []string{"16-25 Railcard", "TPESTUDENT code"},
			PromoCode:       &tpePromo,
			CashbackNetwork: &tcbNetwork,
			CashbackPercent: &tcbPercent,
			BookingAction: BookingAction{
				Type:      "webview",
				URL:       "https://www.tpexpress.co.uk/tickets",
				PromoCode: &tpePromo,
			},
			Summary:        "£" + formatPrice(tpeNetCost) + " TPE Advance + TPESTUDENT + 3.4% TCB",
			RiskLevel:      "LOW",
			DiscountType:   "TPESTUDENT",
			SavingsPercent: 50 + tcbPercent,
		})
	}

	// Get a working Northern promo code
	northernPromoCode := "NORAPP50-NJR2KJ" // Hardcoded working code

	// Northern 50% code with app automation
	northern50Price := basePrice * 0.5
	plans = append(plans, BookingPlan{
		ID:              "northern-50-" + departureTime.Format("150405"),
		NetCost:         northern50Price,
		OriginalCost:    basePrice,
		OperatorCode:    "NORTHERN",
		OperatorName:    "Northern",
		DepartureTime:   departureTime.Add(15 * time.Minute).Format(time.RFC3339),
		ArrivalTime:     departureTime.Add(75 * time.Minute).Format(time.RFC3339),
		DurationMinutes: 60,
		Origin:          "Sheffield",
		OriginCrs:       req.OriginCrs,
		Destination:     "Manchester Piccadilly",
		DestinationCrs:  req.DestCrs,
		RequiredProof:   []string{"Northern 50% code (auto-applied)"},
		PromoCode:       &northernPromoCode,
		BookingAction: BookingAction{
			Type:           "northern_app_automation",
			PromoCode:      &northernPromoCode,
			Origin:         "Sheffield",
			OriginCrs:      req.OriginCrs,
			Destination:    "Manchester Piccadilly",
			DestinationCrs: req.DestCrs,
			DepartureTime:  departureTime.Add(15 * time.Minute).Format(time.RFC3339),
		},
		Summary:        "£" + formatPrice(northern50Price) + " Northern 50% code (auto-applied in app)",
		RiskLevel:      "MEDIUM",
		DiscountType:   "NORTHERN50",
		SavingsPercent: 50,
	})

	// Avanti UNiDAYS (if student verified)
	if req.CanVerifyStudent {
		avantiPrice := basePrice * 0.75 // 25% off via UNiDAYS
		plans = append(plans, BookingPlan{
			ID:              "avanti-unidays-" + departureTime.Format("150405"),
			NetCost:         avantiPrice,
			OriginalCost:    basePrice,
			OperatorCode:    "AVANTI",
			OperatorName:    "Avanti West Coast",
			DepartureTime:   departureTime.Add(30 * time.Minute).Format(time.RFC3339),
			ArrivalTime:     departureTime.Add(85 * time.Minute).Format(time.RFC3339),
			DurationMinutes: 55,
			Origin:          "Sheffield",
			OriginCrs:       req.OriginCrs,
			Destination:     "Manchester Piccadilly",
			DestinationCrs:  req.DestCrs,
			RequiredProof:   []string{"UNiDAYS verification"},
			BookingAction: BookingAction{
				Type: "webview",
				URL:  "https://www.myunidays.com/GB/en-GB/partners/avantiwest/access/online",
			},
			Summary:        "£" + formatPrice(avantiPrice) + " Avanti UNiDAYS (25% off)",
			RiskLevel:      "LOW",
			DiscountType:   "UNIDAYS",
			SavingsPercent: 25,
		})
	}

	// Sort by net cost (cheapest first)
	for i := 0; i < len(plans)-1; i++ {
		for j := i + 1; j < len(plans); j++ {
			if plans[j].NetCost < plans[i].NetCost {
				plans[i], plans[j] = plans[j], plans[i]
			}
		}
	}

	// Limit to 3 results
	if len(plans) > 3 {
		plans = plans[:3]
	}

	return plans
}

func formatPrice(price float64) string {
	return fmt.Sprintf("%.2f", price)
}
