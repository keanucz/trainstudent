package handlers

import (
	"github.com/gofiber/fiber/v2"

	"trainstudent/services/avanti"
)

// AvantiSearchRequest represents a UNiDAYS search request
type AvantiSearchRequest struct {
	DepartureLocation     string `json:"departureLocation"`     // e.g., "London Euston (EUS)"
	ArrivalLocation       string `json:"arrivalLocation"`       // e.g., "Liverpool Lime Street (LIV)"
	DepartureLocationCode int    `json:"departureLocationCode"` // NLC code, e.g., 700010000
	ArrivalLocationCode   int    `json:"arrivalLocationCode"`   // NLC code
	DepartureTime         string `json:"departureTime"`         // ISO 8601
	Adults                int    `json:"adults"`
	Children              int    `json:"children"`
	UseUnidays            bool   `json:"useUnidays"`
}

// SearchAvantiUnidays handles POST /api/avanti/search
func SearchAvantiUnidays(c *fiber.Ctx) error {
	var req AvantiSearchRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Set defaults
	if req.Adults == 0 {
		req.Adults = 1
	}

	// Build search request
	searchReq := avanti.SearchRequest{
		DepartureLocationName: req.DepartureLocation,
		ArrivalLocationName:   req.ArrivalLocation,
		DepartureLocation:     req.DepartureLocationCode,
		ArrivalLocation:       req.ArrivalLocationCode,
		DepartureTimesStart:   req.DepartureTime,
		Adult:                 req.Adults,
		Child:                 req.Children,
		UseUnidays:            req.UseUnidays,
	}

	// Execute search with context
	result, err := avanti.SearchWithUnidays(c.Context(), searchReq)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to search Avanti fares",
			"details": err.Error(),
		})
	}

	return c.JSON(result)
}
