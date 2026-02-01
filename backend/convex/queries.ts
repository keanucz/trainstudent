import { query, mutation } from "./_generated/server";
import { v } from "convex/values";

// Get all active offers
export const getOffers = query({
  args: {
    operatorCode: v.optional(v.string()),
    discountType: v.optional(v.string()),
    requiresRailcard: v.optional(v.boolean()),
  },
  handler: async (ctx, args) => {
    let offers;

    if (args.operatorCode) {
      offers = await ctx.db
        .query("offers")
        .withIndex("by_operator", (q) => q.eq("operatorCode", args.operatorCode!))
        .collect();
    } else {
      offers = await ctx.db.query("offers").collect();
    }

    // Filter out stale offers
    offers = offers.filter((o) => !o.stale);

    // Apply additional filters
    if (args.discountType) {
      offers = offers.filter((o) => o.discountType === args.discountType);
    }
    if (args.requiresRailcard !== undefined) {
      offers = offers.filter((o) => o.requiresRailcard === args.requiresRailcard);
    }

    return offers;
  },
});

// Get offers for a specific route
export const getOffersForRoute = query({
  args: {
    origin: v.string(),
    destination: v.string(),
  },
  handler: async (ctx, args) => {
    const route = `${args.origin}-${args.destination}`;
    const reverseRoute = `${args.destination}-${args.origin}`;

    const offers = await ctx.db.query("offers").collect();

    return offers.filter((o) => {
      if (o.stale) return false;
      if (!o.validRoutes || o.validRoutes.length === 0) return true; // No route restriction
      return o.validRoutes.includes(route) || o.validRoutes.includes(reverseRoute);
    });
  },
});

// Record a booking
export const recordBooking = mutation({
  args: {
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
  },
  handler: async (ctx, args) => {
    return await ctx.db.insert("bookingHistory", {
      ...args,
      timestamp: new Date().toISOString(),
    });
  },
});

// Record a generated code
export const recordGeneratedCode = mutation({
  args: {
    operator: v.string(),
    code: v.string(),
    email: v.string(),
    expiresAt: v.string(),
  },
  handler: async (ctx, args) => {
    return await ctx.db.insert("generatedCodes", {
      ...args,
      generatedAt: new Date().toISOString(),
      used: false,
    });
  },
});

// Mark a code as used
export const markCodeUsed = mutation({
  args: {
    code: v.string(),
  },
  handler: async (ctx, args) => {
    const codeRecord = await ctx.db
      .query("generatedCodes")
      .filter((q) => q.eq(q.field("code"), args.code))
      .first();

    if (codeRecord) {
      await ctx.db.patch(codeRecord._id, {
        used: true,
        usedAt: new Date().toISOString(),
      });
    }
  },
});

// Record voice metrics
export const recordVoiceMetric = mutation({
  args: {
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
  },
  handler: async (ctx, args) => {
    const totalFields = 3; // origin, destination, time
    const accuracy = 1.0 - args.correctionCount / totalFields;

    return await ctx.db.insert("voiceMetrics", {
      ...args,
      accuracy: Math.max(0, accuracy),
      timestamp: new Date().toISOString(),
    });
  },
});

// Get voice metrics summary by provider
export const getVoiceMetricsSummary = query({
  args: {},
  handler: async (ctx) => {
    const metrics = await ctx.db.query("voiceMetrics").collect();

    const summary: Record<string, { count: number; avgLatency: number; avgAccuracy: number; avgBattery: number }> = {};

    for (const m of metrics) {
      if (!summary[m.provider]) {
        summary[m.provider] = { count: 0, avgLatency: 0, avgAccuracy: 0, avgBattery: 0 };
      }
      summary[m.provider].count++;
      summary[m.provider].avgLatency += m.latencyMs;
      summary[m.provider].avgAccuracy += m.accuracy;
      summary[m.provider].avgBattery += m.batteryDrainPercent;
    }

    // Calculate averages
    for (const provider of Object.keys(summary)) {
      const s = summary[provider];
      s.avgLatency = s.avgLatency / s.count;
      s.avgAccuracy = s.avgAccuracy / s.count;
      s.avgBattery = s.avgBattery / s.count;
    }

    return summary;
  },
});

// Cache API token
export const cacheApiToken = mutation({
  args: {
    operator: v.string(),
    tokenType: v.string(),
    tokenValue: v.string(),
    sessionId: v.string(),
    expiresAt: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    // Remove old tokens for this operator/type
    const existing = await ctx.db
      .query("apiTokens")
      .withIndex("by_operator", (q) => q.eq("operator", args.operator))
      .filter((q) => q.eq(q.field("tokenType"), args.tokenType))
      .collect();

    for (const token of existing) {
      await ctx.db.delete(token._id);
    }

    return await ctx.db.insert("apiTokens", {
      ...args,
      fetchedAt: new Date().toISOString(),
    });
  },
});

// Get cached API token
export const getCachedToken = query({
  args: {
    operator: v.string(),
    tokenType: v.string(),
  },
  handler: async (ctx, args) => {
    const token = await ctx.db
      .query("apiTokens")
      .withIndex("by_operator", (q) => q.eq("operator", args.operator))
      .filter((q) => q.eq(q.field("tokenType"), args.tokenType))
      .first();

    if (!token) return null;

    // Check if expired
    if (token.expiresAt && new Date(token.expiresAt) < new Date()) {
      await ctx.db.delete(token._id);
      return null;
    }

    return token;
  },
});
