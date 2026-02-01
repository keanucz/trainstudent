package handlers

import (
	"time"

	"github.com/gofiber/fiber/v2"

	"trainstudent/services/seatfrog"
)

// SeatfrogSearchRequest represents a Seatfrog Secretfare search request
type SeatfrogSearchRequest struct {
	Origin        string `json:"origin"`        // Station code e.g., "SHF"
	Destination   string `json:"destination"`   // Station code e.g., "STP"
	Date          string `json:"date"`          // YYYY-MM-DD
	NumPassengers int    `json:"numPassengers"` // Default 1
}

// SearchSeatfrogSecretfares handles GET /api/seatfrog/search
func SearchSeatfrogSecretfares(c *fiber.Ctx) error {
	origin := c.Query("origin", "")
	destination := c.Query("destination", "")
	date := c.Query("date", "")
	numPassengers := c.QueryInt("numPassengers", 1)

	// Validate required params
	if origin == "" || destination == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "origin and destination are required",
		})
	}

	// Default to tomorrow if no date provided
	if date == "" {
		date = time.Now().AddDate(0, 0, 1).Format("2006-01-02")
	}

	// Create client and search
	client := seatfrog.NewClient()
	resp, err := client.SearchSecretfares(c.Context(), origin, destination, date, numPassengers)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to search Seatfrog",
			"details": err.Error(),
		})
	}

	// Parse deals
	deals := seatfrog.ParseDeals(resp)
	cheapest := seatfrog.FindCheapestDeal(deals)

	return c.JSON(fiber.Map{
		"origin":      origin,
		"destination": destination,
		"date":        date,
		"session":     resp.Session,
		"searchId":    resp.SearchID,
		"deals":       deals,
		"cheapest":    cheapest,
		"totalDeals":  len(deals),
	})
}

// SearchSeatfrogSecretfaresPost handles POST /api/seatfrog/search
func SearchSeatfrogSecretfaresPost(c *fiber.Ctx) error {
	var req SeatfrogSearchRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Set defaults
	if req.NumPassengers == 0 {
		req.NumPassengers = 1
	}
	if req.Date == "" {
		req.Date = time.Now().AddDate(0, 0, 1).Format("2006-01-02")
	}

	// Create client and search
	client := seatfrog.NewClient()
	resp, err := client.SearchSecretfares(c.Context(), req.Origin, req.Destination, req.Date, req.NumPassengers)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to search Seatfrog",
			"details": err.Error(),
		})
	}

	// Parse deals
	deals := seatfrog.ParseDeals(resp)
	cheapest := seatfrog.FindCheapestDeal(deals)

	return c.JSON(fiber.Map{
		"origin":      req.Origin,
		"destination": req.Destination,
		"date":        req.Date,
		"session":     resp.Session,
		"searchId":    resp.SearchID,
		"deals":       deals,
		"cheapest":    cheapest,
		"totalDeals":  len(deals),
	})
}

// GetSeatfrogStations handles GET /api/seatfrog/stations
func GetSeatfrogStations(c *fiber.Ctx) error {
	query := c.Query("q", "")
	if query == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "query parameter 'q' is required",
		})
	}

	client := seatfrog.NewClient()
	stations, err := client.GetStations(c.Context(), query)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to fetch stations",
			"details": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"query":    query,
		"stations": stations,
	})
}
