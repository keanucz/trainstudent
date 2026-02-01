# 🚂 TrainStudent Backend v2.0 - ELI5 Setup Guide

## What is this?
This is the backend server that your TrainStudent Android app talks to. It provides:
- **Northern 50% promo codes** via plus-addressing trick
- **Seatfrog Secretfare** cheap tickets
- **Avanti UNiDAYS** discounted fares
- **Grand Central student discount** form automation
- **Quidco/TopCashback** integration for extra savings
- **Live train times** with discounts applied

---

## 📋 Prerequisites

1. **Go installed** - Check with: `go version` (need Go 1.20+)
2. **Your Tailscale IP** - Find with: `tailscale ip -4`

---

## 🚀 How to Run the Backend (5 Simple Steps)

### Step 1: Open Terminal
```bash
cd /home/keanu/hackathons/ichack26/trainstudent/backend
```

### Step 2: Download Dependencies
```bash
go mod download
```

### Step 3: Build & Run
```bash
go build -o trainstudent-api . && ./trainstudent-api
```

You should see:
```
🚂 TrainStudent API v2.0.0 starting on port 8080
📱 Features: Northern Plus-Addressing, Seatfrog Secretfares, Avanti UNiDAYS, Cashback
```

### Step 4: Test It's Working
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "healthy",
  "version": "2.0.0",
  "features": ["northern-codes", "plus-addressing", "seatfrog-secretfares", ...]
}
```

---

## 📡 API Endpoints Reference

### 🎫 Northern Railway (50% off)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/codes/northern/promo` | Get a working 50% promo code |
| GET | `/api/codes/northern/all` | Get all working codes |
| POST | `/api/codes/northern` | Generate a new code (needs email) |
| POST | `/api/northern/plus-address` | Generate a plus-addressed email |
| POST | `/api/northern/check-codes` | Check Gmail for received codes |
| GET | `/api/northern/farm/status?userId=xxx` | Get all codes for a user |

### Plus-Addressing Trick (Email Farming)
```bash
# Generate a plus-addressed email and submit to Northern
curl -X POST http://localhost:8080/api/northern/plus-address \
  -H "Content-Type: application/json" \
  -d '{"baseEmail": "youremail@gmail.com", "firstName": "John", "lastName": "Doe"}'
```

Response:
```json
{
  "plusAddress": "youremail+northern1234567890@gmail.com",
  "tag": "northern1234567890",
  "status": "submitted",
  "codeSubmitted": true
}
```

### 🦊 Seatfrog Secretfares

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/seatfrog/search?origin=SHF&destination=STP&date=2026-02-05` | Search for deals |
| POST | `/api/seatfrog/search` | Search with JSON body |
| GET | `/api/seatfrog/stations?q=shef` | Station search |

Example:
```bash
curl "http://localhost:8080/api/seatfrog/search?origin=SHF&destination=STP"
```

Response:
```json
{
  "origin": "SHF",
  "destination": "STP",
  "cheapest": {
    "price": 17.00,
    "operator": "East Midlands Railway",
    "timeband": "morning",
    "bookingUrl": "https://secretfare.seatfrog.com/book/..."
  },
  "deals": [...]
}
```

### 🎓 Grand Central Student Discount

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/codes/grandcentral` | Submit student discount form |

Example:
```bash
curl -X POST http://localhost:8080/api/codes/grandcentral \
  -H "Content-Type: application/json" \
  -d '{"firstName": "John", "lastName": "Doe", "email": "john@university.ac.uk"}'
```

### 🎓 Avanti UNiDAYS

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/avanti/search` | Search with UNiDAYS discount |

### � Cashback (Quidco / TopCashback)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/cashback/providers` | List cashback providers |
| GET | `/api/cashback/operator/:code` | Get cashback for operator |
| POST | `/api/cashback/link` | Generate tracked affiliate link |
| POST | `/api/cashback/link-account` | Link cashback account |

Example:
```bash
curl http://localhost:8080/api/cashback/operator/NT
```

Response:
```json
{
  "operator": "Northern",
  "operatorCode": "NT",
  "quidco": {
    "available": true,
    "rate": "Up to 2%",
    "trackingUrl": "https://www.quidco.com/northern-railway/"
  },
  "topcashback": {
    "available": true,
    "rate": "Up to 2%",
    "trackingUrl": "https://www.topcashback.co.uk/northern/"
  }
}
```

### 🚆 Live Trains

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/trains/live?from=sheffield&to=manchester` | Get live trains |
| GET | `/api/trains/live/discounted` | With promo auto-applied |

### 👤 Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/me` | Get current user |
| PUT | `/api/auth/profile` | Update profile |
| POST | `/api/auth/connect/gmail` | Connect Gmail for plus-addressing |
| POST | `/api/auth/disconnect/gmail` | Disconnect Gmail |

---

## 🎫 Working Northern Promo Codes

These codes are **confirmed working** for 50% off Northern Railway advance tickets:

| Code | Status |
|------|--------|
| `NORAPP50-NJR2KJ` | ✅ Working |
| `NORAPP50-HTZZZW` | ✅ Working |

### How to Use:
1. Download the Northern Railway app
2. Search for your journey
3. Tap "No railcard or promo" box
4. Enter the code in "Promotions" field
5. Tap "Select" then "Search"
6. Discounted tickets show "NORTHERN" label and 50% off!

---

## � Gmail Plus-Addressing Trick

This trick lets you generate unlimited promo codes using a single Gmail account.

### How It Works:
1. Gmail ignores everything after `+` in your email
2. `youremail+anything@gmail.com` → goes to `youremail@gmail.com`
3. Northern sees each `+tag` as a different email
4. You get multiple promo codes sent to the same inbox!

### Supported Email Providers:
- ✅ Gmail / Googlemail
- ✅ Outlook / Hotmail / Live

### Setting Up IMAP Access:
1. Enable IMAP in Gmail settings
2. Create an App Password: https://myaccount.google.com/apppasswords
3. Use the app password (not your regular password) with the API

---

## 🌐 Accessing from Phone (Tailscale)

1. **Get your Tailscale IP**: `tailscale ip -4`
2. **Update Android app** in `NetworkModule.kt`:
   ```kotlin
   private const val DEBUG_URL = "http://YOUR_TAILSCALE_IP:8080/"
   ```
3. **Make sure Tailscale is running** on both devices

---

## 🏃 Running in Background

```bash
# Run with nohup
nohup ./trainstudent-api > backend.log 2>&1 &

# or use screen
screen -S trainstudent
./trainstudent-api
# Press Ctrl+A, D to detach
```

---

## 📂 Project Structure

```
backend/
├── main.go                     # Entry point, all routes
├── handlers/
│   ├── auth.go                 # Authentication & profile
│   ├── codes.go                # Northern/Grand Central codes
│   ├── northern_plus.go        # Plus-addressing trick
│   ├── seatfrog.go            # Secretfare search
│   ├── cashback.go            # Quidco/TopCashback
│   ├── live_trains.go         # Live train times
│   └── ...
└── services/
    ├── trainline/             # Trainline API client
    ├── seatfrog/              # Seatfrog Secretfare client
    ├── gmail/                 # Gmail IMAP for code checking
    ├── northern/              # Northern code generator
    └── grandcentral/          # Grand Central form submission
```

---

## ✅ Checklist

- [ ] Backend running: `./trainstudent-api`
- [ ] Health check works: `curl localhost:8080/health`
- [ ] Tailscale connected on computer
- [ ] Tailscale connected on phone
- [ ] Android app has correct IP

---

Happy train hacking! 🚂💨
