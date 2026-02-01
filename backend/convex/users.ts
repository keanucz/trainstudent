import { mutation, query } from "./_generated/server";
import { v } from "convex/values";

// Simplified getUser that works with either clerkId OR just by ID/email logic
export const getUser = query({
  args: { userId: v.string() },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("users")
      .filter((q) => q.eq(q.field("clerkId"), args.userId))
      .first();
  },
});

export const getUserByEmail = query({
  args: { email: v.string() },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .first();
  },
});

export const registerUser = mutation({
  args: {
    email: v.string(),
    passwordHash: v.string(), // We store the hash, hashed by backend
    firstName: v.string(),
    lastName: v.string(),
    verificationCode: v.string(),
  },
  handler: async (ctx, args) => {
    const existing = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .first();

    if (existing) {
      if (existing.emailVerified) {
        throw new Error("Email already registered");
      }
      // If not verified, update (resend logic essentially)
      return await ctx.db.patch(existing._id, {
        password: args.passwordHash,
        firstName: args.firstName,
        lastName: args.lastName,
        emailVerificationCode: args.verificationCode,
        updatedAt: new Date().toISOString(),
      });
    }

    const userId = "user_" + Math.random().toString(36).substr(2, 9);

    const id = await ctx.db.insert("users", {
      clerkId: userId, // Internal ID
      email: args.email,
      password: args.passwordHash,
      firstName: args.firstName,
      lastName: args.lastName,
      emailVerified: false,
      emailVerificationCode: args.verificationCode,

      gmailConnected: false,
      quidcoLinked: false,
      topcashbackLinked: false,
      hasRailcard1625: false,
      studentVerified: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    return userId;
  },
});

export const verifyUserEmail = mutation({
  args: { email: v.string(), code: v.string() },
  handler: async (ctx, args) => {
    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .first();

    if (!user) throw new Error("User not found");

    if (user.emailVerificationCode !== args.code) {
      throw new Error("Invalid code");
    }

    await ctx.db.patch(user._id, {
      emailVerified: true,
      emailVerificationCode: undefined,
      updatedAt: new Date().toISOString(),
    });

    return user.clerkId;
  },
});

export const saveGmailCredentials = mutation({
  args: {
    userId: v.string(), // In real app, derived from auth
    email: v.string(),
    appPassword: v.string(), // Note: In prod, encrypt this!
  },
  handler: async (ctx, args) => {
    // Find user
    // Note: In a real app we'd query by auth identity. 
    // Here we'll just upsert based on the userId passed in for simplicity of the hackathon
    // or we assume userId is the storage ID if passing from backend.

    // For this prototype, we'll try to find by email first, or just update if we have ID
    // specific logic depends on how we manage users.

    // Let's assume userId is the Clerk/Auth ID.
    const user = await ctx.db
      .query("users")
      .filter((q) => q.eq(q.field("clerkId"), args.userId))
      .first();

    if (user) {
      await ctx.db.patch(user._id, {
        gmailConnected: true,
        gmailEmail: args.email,
        gmailRefreshToken: args.appPassword, // Storing raw for hackathon speed, ENCRYPT IN PROD
        updatedAt: new Date().toISOString(),
      });
    } else {
      // Create new user if not exists
      await ctx.db.insert("users", {
        clerkId: args.userId,
        email: args.email,
        gmailConnected: true,
        gmailEmail: args.email,
        gmailRefreshToken: args.appPassword,

        // Auth defaults
        emailVerified: false,

        quidcoLinked: false,
        topcashbackLinked: false,
        hasRailcard1625: false,
        studentVerified: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });
    }
  },
});

export const saveCashbackSession = mutation({
  args: {
    userId: v.string(),
    provider: v.string(), // "quidco" or "topcashback"
    email: v.string(),
    cookies: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await ctx.db
      .query("users")
      .filter((q) => q.eq(q.field("clerkId"), args.userId))
      .first();

    if (user) {
      const update: any = { updatedAt: new Date().toISOString() };
      if (args.provider === "quidco") {
        update.quidcoLinked = true;
        update.quidcoEmail = args.email;
        update.quidcoCookies = args.cookies;
      } else if (args.provider === "topcashback") {
        update.topcashbackLinked = true;
        update.topcashbackEmail = args.email;
        update.topcashbackCookies = args.cookies;
      }
      await ctx.db.patch(user._id, update);
    }
  },
});

export const removeGmailCredentials = mutation({
  args: { userId: v.string() },
  handler: async (ctx, args) => {
    const user = await ctx.db
      .query("users")
      .filter((q) => q.eq(q.field("clerkId"), args.userId))
      .first();

    if (user) {
      await ctx.db.patch(user._id, {
        gmailConnected: false,
        gmailEmail: undefined,
        gmailRefreshToken: undefined,
        updatedAt: new Date().toISOString(),
      });
    }
  },
});

export const recordPlusAddress = mutation({
  args: {
    userId: v.string(),
    baseEmail: v.string(),
    plusAddress: v.string(),
    tag: v.string(),
  },
  handler: async (ctx, args) => {
    await ctx.db.insert("northernPlusEmails", {
      userId: args.userId,
      baseEmail: args.baseEmail,
      plusAddress: args.plusAddress,
      plusTag: args.tag,
      codeReceived: false,
      codeUsed: false,
      createdAt: new Date().toISOString(),
    });
  },
});

export const updatePlusAddressCode = mutation({
  args: {
    plusAddress: v.string(),
    code: v.string(),
    expiry: v.optional(v.string())
  },
  handler: async (ctx, args) => {
    const record = await ctx.db
      .query("northernPlusEmails")
      .withIndex("by_plus_address", (q) => q.eq("plusAddress", args.plusAddress))
      .first();

    if (record) {
      await ctx.db.patch(record._id, {
        promoCode: args.code,
        codeReceived: true,
        codeReceivedAt: new Date().toISOString(),
        codeExpiry: args.expiry,
      });
    }
  },
});
