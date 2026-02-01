package main

import (
	"log"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"github.com/joho/godotenv"

	"trainstudent/handlers"
)

func main() {
	// Load .env file if present
	_ = godotenv.Load()

	app := fiber.New(fiber.Config{
		AppName:      "TrainStudent API v2.0.0",
		ServerHeader: "TrainStudent",
	})

	// Middleware
	app.Use(logger.New())
	app.Use(recover.New())

	// CORS - restrict origins in production
	allowedOrigins := os.Getenv("ALLOWED_ORIGINS")
	if allowedOrigins == "" {
		// Development default - allow all for mobile testing
		allowedOrigins = "*"
	}
	app.Use(cors.New(cors.Config{
		AllowOrigins: allowedOrigins,
		AllowMethods: "GET,POST,PUT,DELETE,OPTIONS",
		AllowHeaders: "Origin, Content-Type, Accept, Authorization",
	}))

	// Convex Auth middleware (optional - allows unauthenticated requests)
	app.Use(handlers.ConvexAuthMiddleware())

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"status":  "healthy",
			"version": "2.0.0",
			"features": []string{
				"northern-codes",
				"plus-addressing",
				"seatfrog-secretfares",
				"avanti-unidays",
				"grandcentral-student",
				"cashback-quidco",
				"cashback-topcashback",
				"live-trains",
			},
		})
	})

	// API routes
	api := app.Group("/api")

	// ============================================
	// AUTH ROUTES
	// ============================================
	auth := api.Group("/auth")
	auth.Post("/register", handlers.Register)
	auth.Post("/login", handlers.Login)
	auth.Post("/verify", handlers.VerifyEmail)
	auth.Get("/me", handlers.GetCurrentUser)
	auth.Put("/profile", handlers.UpdateUserProfile)
	auth.Post("/connect/gmail", handlers.ConnectGmail)
	auth.Post("/disconnect/gmail", handlers.DisconnectGmail)

	// ============================================
	// DISCOUNT CODE ROUTES
	// ============================================
	codes := api.Group("/codes")

	// Northern Railway 50% off
	codes.Post("/northern", handlers.GenerateNorthernCode)
	codes.Get("/northern/promo", handlers.GetNorthernPromoCode)
	codes.Get("/northern/all", handlers.GetAllWorkingNorthernCodes)

	// Grand Central student discount
	codes.Post("/grandcentral", handlers.GenerateGrandCentralCode)

	// ============================================
	// NORTHERN PLUS-ADDRESSING (Email Farming)
	// ============================================
	northern := api.Group("/northern")
	northern.Post("/plus-address", handlers.GenerateNorthernPlusAddress)
	northern.Post("/check-codes", handlers.CheckNorthernCodes)
	northern.Get("/farm/status", handlers.GetNorthernCodeFarmStatus)

	// ============================================
	// SEATFROG SECRETFARES
	// ============================================
	seatfrog := api.Group("/seatfrog")
	seatfrog.Get("/search", handlers.SearchSeatfrogSecretfares)
	seatfrog.Post("/search", handlers.SearchSeatfrogSecretfaresPost)
	seatfrog.Get("/stations", handlers.GetSeatfrogStations)

	// ============================================
	// AVANTI UNIDAYS
	// ============================================
	api.Post("/avanti/search", handlers.SearchAvantiUnidays)

	// ============================================
	// CASHBACK (Quidco / TopCashback)
	// ============================================
	cashback := api.Group("/cashback")
	cashback.Get("/providers", handlers.GetCashbackProviders)
	cashback.Get("/operator/:code", handlers.GetCashbackForOperator)
	cashback.Post("/link", handlers.GenerateCashbackLink)
	cashback.Post("/link-account", handlers.LinkCashbackAccount)
	cashback.Get("/santander/offer", handlers.GetSantanderOffer)

	// ============================================
	// OFFERS
	// ============================================
	api.Get("/offers", handlers.GetOffers)

	// ============================================
	// LIVE TRAINS
	// ============================================
	trains := api.Group("/trains")
	trains.Get("/live", handlers.GetLiveTrains)
	trains.Get("/live/discounted", handlers.GetLiveTrainsWithPromo)

	// ============================================
	// SEARCH (Combined discount search)
	// ============================================
	api.Post("/search", handlers.SearchDiscounts)

	// Get port from env or default
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("🚂 TrainStudent API v2.0.0 starting on port %s", port)
	log.Printf("📱 Features: Northern Plus-Addressing, Seatfrog Secretfares, Avanti UNiDAYS, Cashback")
	log.Fatal(app.Listen(":" + port))
}
