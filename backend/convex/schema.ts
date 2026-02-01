import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export default defineSchema({
  // Users with authentication
  users: defineTable({
    // Convex auth fields
    clerkId: v.optional(v.string()),
    email: v.string(),
    password: v.optional(v.string()),
    emailVerified: v.boolean(),
    emailVerificationCode: v.optional(v.string()),
    firstName: v.optional(v.string()),
    lastName: v.optional(v.string()),

    // Gmail connection for Northern plus addressing
    gmailConnected: v.boolean(),
    gmailEmail: v.optional(v.string()), // Base Gmail address
    gmailRefreshToken: v.optional(v.string()), // Encrypted refresh token

    // Cashback linking
    quidcoLinked: v.boolean(),
    quidcoEmail: v.optional(v.string()),
    quidcoCookies: v.optional(v.string()), // JSON string of cookies
    topcashbackLinked: v.boolean(),
    topcashbackEmail: v.optional(v.string()),
    topcashbackCookies: v.optional(v.string()), // JSON string of cookies

    // Railcard info
    hasRailcard1625: v.boolean(),
    railcardNumber: v.optional(v.string()),

    // Student verification
    uniEmail: v.optional(v.string()), // For .ac.uk verification
    studentVerified: v.boolean(),

    createdAt: v.string(),
    updatedAt: v.string(),
  })
    .index("by_clerk_id", ["clerkId"])
    .index("by_email", ["email"])
    .index("by_gmail", ["gmailEmail"]),

  // Northern plus-addressed emails tracking
  northernPlusEmails: defineTable({
    userId: v.string(), // Owner of this plus address
    baseEmail: v.string(), // e.g., user@gmail.com
    plusAddress: v.string(), // e.g., user+northern1738@gmail.com
    plusTag: v.string(), // e.g., "northern1738"
    promoCode: v.optional(v.string()), // The code received
    codeReceived: v.boolean(),
    codeReceivedAt: v.optional(v.string()),
    codeExpiry: v.optional(v.string()),
    codeUsed: v.boolean(),
    createdAt: v.string(),
  })
    .index("by_user", ["userId"])
    .index("by_plus_address", ["plusAddress"])
    .index("by_base_email", ["baseEmail"]),

  // Discount offers from train operators
  offers: defineTable({
    operator: v.string(),
    operatorCode: v.string(),
    discountType: v.union(
      v.literal("TPESTUDENT"),
      v.literal("RAILCARD"),
      v.literal("UNIDAYS"),
      v.literal("NORTHERN50"),
      v.literal("STUDENT"),
      v.literal("STUDENTBEANS"),
      v.literal("SEATFROG"),
      v.literal("GRANDCENTRAL")
    ),
    percentOff: v.number(),
    requiresRailcard: v.boolean(),
    requiresAcUkEmail: v.boolean(),
    requiresUnidays: v.boolean(),
    requiresStudentBeans: v.boolean(),
    stackable: v.boolean(),
    promoCode: v.optional(v.string()),
    description: v.string(),
    terms: v.string(),
    validRoutes: v.optional(v.array(v.string())),
    lastVerified: v.string(),
    stale: v.optional(v.boolean()),
  })
    .index("by_operator", ["operatorCode"])
    .index("by_discount_type", ["discountType"])
    .index("by_stale", ["stale"]),

  // User booking history
  bookingHistory: defineTable({
    visitorId: v.string(),
    userId: v.optional(v.string()),
    searchOrigin: v.string(),
    searchDestination: v.string(),
    searchTime: v.string(),
    selectedPlan: v.object({
      operatorCode: v.string(),
      discountType: v.string(),
      netCost: v.number(),
      originalCost: v.number(),
    }),
    bookingCompleted: v.boolean(),
    cashbackProvider: v.optional(v.string()), // "quidco", "topcashback"
    cashbackAmount: v.optional(v.number()),
    timestamp: v.string(),
  })
    .index("by_visitor", ["visitorId"])
    .index("by_user", ["userId"])
    .index("by_timestamp", ["timestamp"]),

  // Generated discount codes (for tracking)
  generatedCodes: defineTable({
    operator: v.string(),
    code: v.string(),
    email: v.string(),
    userId: v.optional(v.string()),
    plusAddress: v.optional(v.string()), // If generated via plus addressing
    generatedAt: v.string(),
    expiresAt: v.string(),
    used: v.boolean(),
    usedAt: v.optional(v.string()),
  })
    .index("by_operator", ["operator"])
    .index("by_email", ["email"])
    .index("by_user", ["userId"])
    .index("by_expiry", ["expiresAt"]),

  // Seatfrog Secretfare searches
  seatfrogSearches: defineTable({
    userId: v.optional(v.string()),
    origin: v.string(),
    destination: v.string(),
    date: v.string(),
    numPassengers: v.number(),
    session: v.string(),
    searchId: v.string(),
    cheapestPrice: v.optional(v.number()),
    cheapestToc: v.optional(v.string()),
    timestamp: v.string(),
  })
    .index("by_user", ["userId"])
    .index("by_route", ["origin", "destination"]),

  // Avanti UNiDAYS ticket searches
  avantiSearches: defineTable({
    userId: v.optional(v.string()),
    origin: v.string(),
    destination: v.string(),
    date: v.string(),
    unidaysVerified: v.boolean(),
    cheapestPrice: v.optional(v.number()),
    originalPrice: v.optional(v.number()),
    bookingUrl: v.optional(v.string()),
    timestamp: v.string(),
  })
    .index("by_user", ["userId"]),

  // Grand Central form submissions
  grandCentralSubmissions: defineTable({
    userId: v.optional(v.string()),
    firstName: v.string(),
    lastName: v.string(),
    uniEmail: v.string(),
    submitted: v.boolean(),
    submittedAt: v.optional(v.string()),
    codeReceived: v.optional(v.string()),
    timestamp: v.string(),
  })
    .index("by_user", ["userId"])
    .index("by_email", ["uniEmail"]),

  // Voice metrics for analytics
  voiceMetrics: defineTable({
    visitorId: v.string(),
    provider: v.union(
      v.literal("SPEECH_RECOGNIZER"),
      v.literal("CACTUS_7B"),
      v.literal("CACTUS_13B")
    ),
    latencyMs: v.number(),
    correctionCount: v.number(),
    fieldsCorrected: v.string(),
    batteryDrainPercent: v.number(),
    accuracy: v.number(),
    timestamp: v.string(),
  })
    .index("by_provider", ["provider"])
    .index("by_visitor", ["visitorId"])
    .index("by_timestamp", ["timestamp"]),

  // Cached API tokens
  apiTokens: defineTable({
    operator: v.string(),
    tokenType: v.string(),
    tokenValue: v.string(),
    sessionId: v.string(),
    fetchedAt: v.string(),
    expiresAt: v.optional(v.string()),
  })
    .index("by_operator", ["operator"])
    .index("by_session", ["sessionId"]),
});
