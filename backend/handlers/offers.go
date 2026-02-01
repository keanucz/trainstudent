package handlers

import (
	"sort"

	"github.com/gofiber/fiber/v2"
)

// Offer represents a discount offer from a train operator
type Offer struct {
	ID              string  `json:"id"`
	Operator        string  `json:"operator"`
	Title           string  `json:"title"`
	Description     string  `json:"description"`
	DiscountPercent int     `json:"discountPercent"`
	DiscountType    string  `json:"discountType"`
	RequiredRailcard *string `json:"requiredRailcard,omitempty"`
	ValidFrom       string  `json:"validFrom"`
	ValidTo         string  `json:"validTo"`
	Link            string  `json:"link"`
}

// Static offers database
var offersDB = []Offer{
	{
		ID:              "northern-50",
		Operator:        "Northern",
		Title:           "50% Off Peak Tickets",
		Description:     "Get 50% off peak tickets when you travel on Northern services. Valid for same-day travel only.",
		DiscountPercent: 50,
		DiscountType:    "flash-sale",
		RequiredRailcard: nil,
		ValidFrom:       "2025-01-01",
		ValidTo:         "2025-12-31",
		Link:            "https://help.northernrailway.co.uk/s/app-50",
	},
	{
		ID:              "avanti-unidays",
		Operator:        "Avanti West Coast",
		Title:           "UNiDAYS Student Discount",
		Description:     "Exclusive student discount on Avanti West Coast services via UNiDAYS.",
		DiscountPercent: 25,
		DiscountType:    "student",
		RequiredRailcard: nil,
		ValidFrom:       "2025-01-01",
		ValidTo:         "2025-12-31",
		Link:            "https://www.avantiwestcoast.co.uk/",
	},
	{
		ID:              "grandcentral-student",
		Operator:        "Grand Central",
		Title:           "Student Discount",
		Description:     "Student discount for .ac.uk email holders on Grand Central routes.",
		DiscountPercent: 15,
		DiscountType:    "student",
		RequiredRailcard: nil,
		ValidFrom:       "2025-01-01",
		ValidTo:         "2025-12-31",
		Link:            "https://www.grandcentralrail.com/",
	},
	{
		ID:              "tpe-student",
		Operator:        "TransPennine Express",
		Title:           "16-25 Railcard Discount",
		Description:     "1/3 off with 16-25 Railcard on TransPennine Express services.",
		DiscountPercent: 33,
		DiscountType:    "railcard",
		RequiredRailcard: stringPtr("16-25 Railcard"),
		ValidFrom:       "2025-01-01",
		ValidTo:         "2025-12-31",
		Link:            "https://www.tpexpress.co.uk/",
	},
	{
		ID:              "lner-advance",
		Operator:        "LNER",
		Title:           "Advance Tickets",
		Description:     "Book in advance for the best prices on LNER services.",
		DiscountPercent: 40,
		DiscountType:    "advance",
		RequiredRailcard: nil,
		ValidFrom:       "2025-01-01",
		ValidTo:         "2025-12-31",
		Link:            "https://www.lner.co.uk/",
	},
}

func stringPtr(s string) *string {
	return &s
}

// GetOffers handles GET /api/offers
func GetOffers(c *fiber.Ctx) error {
	// Query parameters for filtering
	operatorFilter := c.Query("operator")
	railcardFilter := c.Query("railcard")
	discountTypeFilter := c.Query("discountType")

	// Filter offers
	filtered := make([]Offer, 0, len(offersDB))
	for _, offer := range offersDB {
		// Apply filters
		if operatorFilter != "" && offer.Operator != operatorFilter {
			continue
		}
		if discountTypeFilter != "" && offer.DiscountType != discountTypeFilter {
			continue
		}
		if railcardFilter != "" {
			if offer.RequiredRailcard == nil || *offer.RequiredRailcard != railcardFilter {
				continue
			}
		}
		filtered = append(filtered, offer)
	}

	// Sort by discount percent descending
	sort.Slice(filtered, func(i, j int) bool {
		return filtered[i].DiscountPercent > filtered[j].DiscountPercent
	})

	return c.JSON(fiber.Map{
		"offers": filtered,
		"count":  len(filtered),
	})
}
