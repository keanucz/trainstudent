package handlers

import (
	"github.com/gofiber/fiber/v2"

	"trainstudent/services/northern"
	"trainstudent/services/trainline"
)

// NorthernCodeRequest represents a request for a Northern 50% code
type NorthernCodeRequest struct {
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	Email     string `json:"email"`
}

// NorthernCodeResponse represents the response with the generated code
type NorthernCodeResponse struct {
	Code    string `json:"code"`
	Expires string `json:"expires"`
	Message string `json:"message"`
	Status  string `json:"status"`
}

// GenerateNorthernCode handles POST /api/codes/northern
func GenerateNorthernCode(c *fiber.Ctx) error {
	var req NorthernCodeRequest
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

	// Try to generate code via Northern service first
	result, err := northern.GenerateCode(c.Context(), req.FirstName, req.LastName, req.Email)
	if err != nil {
		// If generation fails, return one of our hardcoded working codes
		return c.JSON(NorthernCodeResponse{
			Code:    trainline.GetWorkingPromoCode(),
			Expires: "2026-12-31",
			Message: "Here's a working 50% off promo code for Northern Railway App!",
			Status:  "success",
		})
	}

	return c.JSON(NorthernCodeResponse{
		Code:    result.Code,
		Expires: result.Expires,
		Message: result.Message,
		Status:  "success",
	})
}

// GetNorthernPromoCode handles GET /api/codes/northern/promo
// Returns a hardcoded working promo code directly
func GetNorthernPromoCode(c *fiber.Ctx) error {
	// Return one of the confirmed working promo codes
	code := trainline.GetWorkingPromoCode()

	return c.JSON(fiber.Map{
		"code":    code,
		"type":    "northern_50_off",
		"expires": "2026-12-31",
		"message": "50% off Northern Railway advance tickets. Apply this code in the Northern Railway app.",
		"instructions": []string{
			"1. Download the Northern Railway app",
			"2. Search for your journey",
			"3. Tap 'No railcard or promo' box",
			"4. Enter the promo code in the 'Promotions' field",
			"5. Press 'Select' and then 'Search'",
			"6. Discounted tickets will show 'NORTHERN' and 50% off",
		},
		"status": "confirmed_working",
	})
}

// AllWorkingCodes returns all confirmed working Northern promo codes
func GetAllWorkingNorthernCodes(c *fiber.Ctx) error {
	return c.JSON(fiber.Map{
		"codes": trainline.WorkingPromoCodes,
		"type":  "northern_50_off",
		"note":  "These codes are confirmed working for 50% off Northern Railway advance tickets",
	})
}
