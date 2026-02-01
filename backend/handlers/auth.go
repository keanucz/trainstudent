package handlers

import (
	"fmt"
	"strings"
	"time"

	"trainstudent/services/convex"
	"trainstudent/services/gmail"

	"golang.org/x/crypto/bcrypt"

	"github.com/gofiber/fiber/v2"
)

// User represents an authenticated user
type User struct {
	ID                string `json:"id"`
	Email             string `json:"email"`
	FirstName         string `json:"firstName,omitempty"`
	LastName          string `json:"lastName,omitempty"`
	GmailConnected    bool   `json:"gmailConnected"`
	GmailEmail        string `json:"gmailEmail,omitempty"`
	QuidcoLinked      bool   `json:"quidcoLinked"`
	TopCashbackLinked bool   `json:"topcashbackLinked"`
	HasRailcard1625   bool   `json:"hasRailcard1625"`
	StudentVerified   bool   `json:"studentVerified"`
	UniEmail          string `json:"uniEmail,omitempty"`
	CreatedAt         string `json:"createdAt"`
}

// AuthSession represents a session
type AuthSession struct {
	UserID    string `json:"userId"`
	Token     string `json:"token"`
	ExpiresAt string `json:"expiresAt"`
}

// LoginRequest represents login credentials
type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

// RegisterRequest represents registration data
type RegisterRequest struct {
	Email     string `json:"email"`
	Password  string `json:"password"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}

// Register handles POST /api/auth/register
func Register(c *fiber.Ctx) error {
	var req RegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid request"})
	}

	if req.Email == "" || req.Password == "" {
		return c.Status(400).JSON(fiber.Map{"error": "Email and password required"})
	}

	// Hash password
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "Internal error"})
	}

	// Generate verification code (simple 4 digit)
	code := fmt.Sprintf("%04d", time.Now().Nanosecond()%10000)

	// Call Convex registerUser
	convexClient := convex.NewClient()

	// Note: result is userId
	err = convexClient.Mutation("users", "registerUser", map[string]string{
		"email":            req.Email,
		"passwordHash":     string(hash),
		"firstName":        req.FirstName,
		"lastName":         req.LastName,
		"verificationCode": code,
	})

	// registerUser usually returns string, but Mutation helper returns error.
	// The return value of mutation is not captured by generic Mutation helper easily in this client.
	// We assume it succeeded if no error.

	if err != nil {
		if strings.Contains(err.Error(), "Email already registered") {
			return c.Status(400).JSON(fiber.Map{"error": "Email already registered"})
		}
		return c.Status(500).JSON(fiber.Map{"error": "Registration failed", "details": err.Error()})
	}

	// LOG THE VERIFICATION CODE (to simulate sending email)
	fmt.Printf("\n\n========================================\n")
	fmt.Printf("EMAIL SENT TO %s\n", req.Email)
	fmt.Printf("VERIFICATION CODE: %s\n", code)
	fmt.Printf("========================================\n\n")

	return c.JSON(fiber.Map{
		"status":  "pending_verification",
		"message": "Verification code sent to email",
		"email":   req.Email,
		// "devCode": code, // Can expose for easier testing if user wants
	})
}

// VerifyEmail handles POST /api/auth/verify
func VerifyEmail(c *fiber.Ctx) error {
	type VerifyRequest struct {
		Email string `json:"email"`
		Code  string `json:"code"`
	}
	var req VerifyRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid request"})
	}

	convexClient := convex.NewClient()
	err := convexClient.Mutation("users", "verifyUserEmail", map[string]string{
		"email": req.Email,
		"code":  req.Code,
	})

	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Verification failed", "details": err.Error()})
	}

	// Auto-login? Or ask to login.
	// Return a simulated token.
	userId := "user_" // In real flow we'd fetch the ID.

	return c.JSON(fiber.Map{
		"status": "verified",
		"token":  "convex_" + userId, // Simplified token
		"userId": userId,
	})
}

// Login handles POST /api/auth/login
func Login(c *fiber.Ctx) error {
	var req LoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid request"})
	}

	convexClient := convex.NewClient()

	// Fetch user by email to get password hash
	// We need a helper struct to capture password
	type UserWithPass struct {
		ID            string `json:"_id"`
		ClerkId       string `json:"clerkId"`
		Password      string `json:"password"`
		EmailVerified bool   `json:"emailVerified"`
	}

	var user UserWithPass
	err := convexClient.Query("users", "getUserByEmail", map[string]string{
		"email": req.Email,
	}, &user)

	if err != nil || user.ID == "" {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid credentials"})
	}

	// Check password
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(req.Password)); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid credentials"})
	}

	if !user.EmailVerified {
		return c.Status(403).JSON(fiber.Map{"error": "Email not verified"})
	}

	// Generate Token (simple for hackathon)
	token := "convex_" + user.ClerkId // In prod use simple-jwt

	return c.JSON(fiber.Map{
		"token":     token,
		"userId":    user.ClerkId,
		"expiresAt": time.Now().Add(24 * time.Hour).Format(time.RFC3339),
	})
}

// ConvexAuthMiddleware validates tokens
func ConvexAuthMiddleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		auth := c.Get("Authorization")
		if auth == "" {
			c.Locals("authenticated", false)
			return c.Next()
		}

		parts := strings.Split(auth, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			return c.Status(401).JSON(fiber.Map{"error": "Invalid auth header"})
		}

		token := parts[1]

		// Hackathon token validation
		// Format: convex_user_xxxx
		if strings.HasPrefix(token, "convex_") {
			userId := strings.TrimPrefix(token, "convex_")
			c.Locals("authenticated", true)
			c.Locals("userId", userId)
			return c.Next()
		}

		// Support previous numeric tokens
		c.Locals("authenticated", true)
		c.Locals("userId", "user_"+token) // Legacy fallback

		return c.Next()
	}
}

// validateConvexToken is deprecated
func validateConvexToken(token string) (string, error) {
	return "user_" + token, nil
}

// GetCurrentUser handles GET /api/auth/me
func GetCurrentUser(c *fiber.Ctx) error {
	authenticated := c.Locals("authenticated")
	if authenticated == nil || !authenticated.(bool) {
		return c.Status(401).JSON(fiber.Map{
			"error": "Not authenticated",
		})
	}

	userId := c.Locals("userId").(string)

	// TODO: Fetch user from Convex
	// For now, return mock data
	user := User{
		ID:                userId,
		Email:             "user@example.com",
		FirstName:         "Student",
		LastName:          "User",
		GmailConnected:    false,
		QuidcoLinked:      false,
		TopCashbackLinked: false,
		HasRailcard1625:   true,
		StudentVerified:   false,
		CreatedAt:         time.Now().Format(time.RFC3339),
	}

	return c.JSON(user)
}

// UpdateUserProfile handles PUT /api/auth/profile
func UpdateUserProfile(c *fiber.Ctx) error {
	authenticated := c.Locals("authenticated")
	if authenticated == nil || !authenticated.(bool) {
		return c.Status(401).JSON(fiber.Map{
			"error": "Not authenticated",
		})
	}

	type UpdateRequest struct {
		FirstName       string `json:"firstName,omitempty"`
		LastName        string `json:"lastName,omitempty"`
		HasRailcard1625 *bool  `json:"hasRailcard1625,omitempty"`
		RailcardNumber  string `json:"railcardNumber,omitempty"`
		UniEmail        string `json:"uniEmail,omitempty"`
	}

	var req UpdateRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	// Validate uni email if provided
	if req.UniEmail != "" && !strings.HasSuffix(strings.ToLower(req.UniEmail), ".ac.uk") {
		return c.Status(400).JSON(fiber.Map{
			"error": "University email must end with .ac.uk",
		})
	}

	// TODO: Update user in Convex

	return c.JSON(fiber.Map{
		"status":  "updated",
		"message": "Profile updated successfully",
	})
}

// ConnectGmail handles POST /api/auth/connect/gmail
func ConnectGmail(c *fiber.Ctx) error {
	authenticated := c.Locals("authenticated")
	if authenticated == nil || !authenticated.(bool) {
		return c.Status(401).JSON(fiber.Map{
			"error": "Not authenticated",
		})
	}

	userId := c.Locals("userId").(string)

	type ConnectRequest struct {
		Email       string `json:"email"`
		AppPassword string `json:"appPassword"` // Gmail app-specific password
	}

	var req ConnectRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if req.Email == "" || req.AppPassword == "" {
		return c.Status(400).JSON(fiber.Map{
			"error": "email and appPassword are required",
		})
	}

	// Validate credentials with Gmail
	client := gmail.NewIMAPClient(req.Email, req.AppPassword)
	if err := client.Connect(); err != nil {
		return c.Status(400).JSON(fiber.Map{
			"error":   "Invalid Gmail credentials. Make sure to use an App Password.",
			"details": err.Error(),
		})
	}
	// Close connection immediately as we just wanted to verify
	client.Close()

	// Store securely in Convex
	convexClient := convex.NewClient()

	// Call users:saveGmailCredentials mutation
	err := convexClient.Mutation("users", "saveGmailCredentials", map[string]string{
		"userId":      userId,
		"email":       req.Email,
		"appPassword": req.AppPassword,
	})

	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to save credentials",
			"details": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"status":  "connected",
		"email":   req.Email,
		"message": "Gmail connected successfully. You can now use the Northern plus-addressing trick.",
	})
}

// DisconnectGmail handles POST /api/auth/disconnect/gmail
func DisconnectGmail(c *fiber.Ctx) error {
	authenticated := c.Locals("authenticated")
	if authenticated == nil || !authenticated.(bool) {
		return c.Status(401).JSON(fiber.Map{
			"error": "Not authenticated",
		})
	}

	userId := c.Locals("userId").(string)

	convexClient := convex.NewClient()
	err := convexClient.Mutation("users", "removeGmailCredentials", map[string]string{
		"userId": userId,
	})

	if err != nil {
		return c.Status(500).JSON(fiber.Map{
			"error":   "Failed to remove credentials",
			"details": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"status":  "disconnected",
		"message": "Gmail disconnected successfully",
	})
}

// RequireAuth is a middleware that ensures the user is authenticated
func RequireAuth() fiber.Handler {
	return func(c *fiber.Ctx) error {
		authenticated := c.Locals("authenticated")
		if authenticated == nil || !authenticated.(bool) {
			return c.Status(401).JSON(fiber.Map{
				"error": "Authentication required",
			})
		}
		return c.Next()
	}
}
