package handlers

import (
	"fmt"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"

	"trainstudent/services/convex"
	"trainstudent/services/gmail"
	"trainstudent/services/northern"
)

// NorthernPlusAddressRequest represents a request to generate a plus-addressed email
type NorthernPlusAddressRequest struct {
	BaseEmail string `json:"baseEmail"` // e.g., user@gmail.com
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}

// NorthernPlusAddressResponse represents the response with the generated plus address
type NorthernPlusAddressResponse struct {
	PlusAddress   string `json:"plusAddress"` // e.g., user+northern1234@gmail.com
	Tag           string `json:"tag"`         // e.g., "northern1234"
	Status        string `json:"status"`
	Message       string `json:"message"`
	CodeSubmitted bool   `json:"codeSubmitted"` // Whether we submitted to Northern
}

// GenerateNorthernPlusAddress handles POST /api/northern/plus-address
// Generates a unique plus-addressed email for Northern code farming
func GenerateNorthernPlusAddress(c *fiber.Ctx) error {
	var req NorthernPlusAddressRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Validate email
	if req.BaseEmail == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "baseEmail is required",
		})
	}

	// Check if email provider supports plus addressing
	if !gmail.IsGmailCompatible(req.BaseEmail) {
		return c.Status(400).JSON(fiber.Map{
			"error":     "Email provider does not support plus addressing",
			"supported": []string{"gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com"},
		})
	}

	// Generate unique plus address
	plusAddress, tag := gmail.GeneratePlusAddress(req.BaseEmail)

	// Record in Convex
	// We might not have userId if unauthenticated, but let's assume this is mostly for authenticated users
	// or we pass a visitor ID. For now, try to get userId from context.
	userId := ""
	if c.Locals("authenticated") == true {
		userId = c.Locals("userId").(string)

		convexClient := convex.NewClient()
		_ = convexClient.Mutation("users", "recordPlusAddress", map[string]string{
			"userId":      userId,
			"baseEmail":   req.BaseEmail,
			"plusAddress": plusAddress,
			"tag":         tag,
		})
	}

	response := NorthernPlusAddressResponse{
		PlusAddress:   plusAddress,
		Tag:           tag,
		Status:        "generated",
		Message:       fmt.Sprintf("Use this email to sign up for Northern 50%% code: %s", plusAddress),
		CodeSubmitted: false,
	}

	// If firstName provided, auto-submit to Northern
	if req.FirstName != "" {
		_, err := northern.GenerateCode(c.Context(), req.FirstName, req.LastName, plusAddress)
		if err == nil {
			response.CodeSubmitted = true
			response.Status = "submitted"
			response.Message = fmt.Sprintf("Code request submitted to Northern using %s. Check your inbox!", plusAddress)
		} else {
			response.Message = fmt.Sprintf("Plus address generated but submission failed: %s", err.Error())
		}
	}

	return c.JSON(response)
}

// CheckNorthernCodes handles POST /api/northern/check-codes
// Checks Gmail IMAP for Northern promo code emails
func CheckNorthernCodes(c *fiber.Ctx) error {
	type CheckRequest struct {
		Email       string `json:"email"`
		AppPassword string `json:"appPassword,omitempty"` // Optional if user is authenticated and has stored creds
		PlusTag     string `json:"plusTag,omitempty"`     // Optional: filter by plus tag
	}

	var req CheckRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Resolve credentials
	email := req.Email
	password := req.AppPassword

	if email == "" && c.Locals("authenticated") == true {
		// Try to fetch from Convex
		userId := c.Locals("userId").(string)
		convexClient := convex.NewClient()

		// Re-use the struct from auth handler, ideally should be shared
		type ConvexUser struct {
			Email             string  `json:"email"`
			GmailConnected    bool    `json:"gmailConnected"`
			GmailEmail        *string `json:"gmailEmail,omitempty"`
			GmailRefreshToken *string `json:"gmailRefreshToken,omitempty"` // Currently storing app password here
		}
		var user ConvexUser
		err := convexClient.Query("users", "getUser", map[string]string{"userId": userId}, &user)
		if err == nil && user.GmailConnected && user.GmailEmail != nil && user.GmailRefreshToken != nil {
			email = *user.GmailEmail
			password = *user.GmailRefreshToken
		}
	}

	if email == "" || password == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "email and appPassword are required (or connect Gmail account)",
		})
	}

	// Create IMAP client
	client := gmail.NewIMAPClient(email, password)

	// Connect
	if err := client.Connect(); err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to connect to Gmail",
			"details": err.Error(),
		})
	}
	defer client.Close()

	// Build plus address filter if tag provided
	var filterAddress string
	if req.PlusTag != "" {
		parts := strings.Split(email, "@")
		if len(parts) == 2 {
			filterAddress = fmt.Sprintf("%s+%s@%s", parts[0], req.PlusTag, parts[1])
		}
	}

	// Check for codes
	codes, err := client.CheckForNorthernCodes(c.Context(), filterAddress)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to check for codes",
			"details": err.Error(),
		})
	}

	// Store found codes in Convex
	if len(codes) > 0 {
		convexClient := convex.NewClient()
		for _, code := range codes {
			// Update the record if we know the plus address
			// We iterate and fire mutations without waiting to speed up
			go func(c gmail.NorthernCodeEmail) {
				_ = convexClient.Mutation("users", "updatePlusAddressCode", map[string]string{
					"plusAddress": c.To,
					"code":        c.PromoCode,
					// approximate expiry 1 month from now
					"expiry": c.ReceivedAt.AddDate(0, 1, 0).Format(time.RFC3339),
				})
			}(code)
		}
	}

	// Format response
	type CodeResult struct {
		Code       string `json:"code"`
		ReceivedAt string `json:"receivedAt"`
		ToAddress  string `json:"toAddress"`
		Subject    string `json:"subject"`
	}

	results := make([]CodeResult, 0, len(codes))
	for _, code := range codes {
		results = append(results, CodeResult{
			Code:       code.PromoCode,
			ReceivedAt: code.ReceivedAt.Format(time.RFC3339),
			ToAddress:  code.To,
			Subject:    code.Subject,
		})
	}

	return c.JSON(fiber.Map{
		"email":      email,
		"codesFound": len(results),
		"codes":      results,
	})
}

// NorthernCodeFarmStatus handles GET /api/northern/farm/status
// Returns status of all plus-addressed codes for a user
func GetNorthernCodeFarmStatus(c *fiber.Ctx) error {
	userId := c.Query("userId")
	if userId == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "userId is required",
		})
	}

	// TODO: Query from Convex database
	// For now, return mock data

	return c.JSON(fiber.Map{
		"userId": userId,
		"plusAddresses": []fiber.Map{
			{
				"plusAddress":  "user+northern1706783400@gmail.com",
				"tag":          "northern1706783400",
				"codeReceived": true,
				"code":         "NORAPP50-NJR2KJ",
				"expiry":       "2026-02-01T23:59:59Z",
				"used":         false,
			},
			{
				"plusAddress":  "user+northern1706869800@gmail.com",
				"tag":          "northern1706869800",
				"codeReceived": false,
				"code":         nil,
				"status":       "pending",
			},
		},
		"totalAddresses": 2,
		"availableCodes": 1,
	})
}
