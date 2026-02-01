package handlers

import (
	"github.com/gofiber/fiber/v2"

	"trainstudent/services/grandcentral"
)

// GrandCentralCodeRequest represents a request for Grand Central student discount
type GrandCentralCodeRequest struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	Email     string `json:"email"` // Must be .ac.uk
}

// GenerateGrandCentralCode handles POST /api/codes/grandcentral
func GenerateGrandCentralCode(c *fiber.Ctx) error {
	var req GrandCentralCodeRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Validate required fields
	if req.FirstName == "" || req.Email == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "firstName and email are required",
		})
	}

	// Verify .ac.uk email
	if !grandcentral.IsAcUkEmail(req.Email) {
		return c.Status(400).JSON(fiber.Map{
			"error": "Email must be a .ac.uk address for student verification",
		})
	}

	// Submit to Grand Central
	result, err := grandcentral.SubmitStudentDiscount(c.Context(), req.FirstName, req.LastName, req.Email)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to submit student verification",
			"details": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"status":  result.Status,
		"message": result.Message,
	})
}
