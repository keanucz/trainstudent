# TrainStudent

A production-ready Android + Go backend application for finding student train discounts across UK train operators.

## Project Structure

```
trainstudent/
├── android/                          # Android app (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── build.gradle.kts          # Dependencies: Compose, Hilt, Room, Retrofit
│   │   └── src/main/
│   │       ├── AndroidManifest.xml   # Permissions, activities
│   │       └── java/com/trainstudent/
│   │           ├── TrainStudentApp.kt        # @HiltAndroidApp
│   │           ├── di/
│   │           │   ├── AppModule.kt          # Room, Voice handlers
│   │           │   └── NetworkModule.kt      # Retrofit, OkHttp
│   │           ├── model/
│   │           │   ├── BookingPlan.kt        # NetCost, BookingAction
│   │           │   ├── TrainService.kt       # Live train data
│   │           │   ├── TravelIntent.kt       # ParsedStation, ParsedDateTime
│   │           │   └── UserProfile.kt        # Railcard, verification
│   │           ├── network/
│   │           │   └── TrainStudentApi.kt    # Retrofit interface
│   │           ├── repository/
│   │           │   └── TrainStudentRepository.kt
│   │           ├── data/
│   │           │   └── VoiceMetricsDatabase.kt # Room DB for metrics
│   │           ├── voice/
│   │           │   ├── VoiceInputHandler.kt  # Interface
│   │           │   ├── SpeechRecognizerHandler.kt  # Android STT
│   │           │   ├── CactusLlmHandler.kt   # Cactus 7B/13B
│   │           │   ├── CactusModelDownloader.kt    # DownloadManager
│   │           │   ├── DownloadCompleteReceiver.kt
│   │           │   ├── StationMatcher.kt     # Fuzzy + Soundex
│   │           │   ├── TimeParser.kt         # NLU time parsing
│   │           │   └── VoiceMetricsCollector.kt
│   │           └── ui/
│   │               ├── MainActivity.kt       # Navigation host
│   │               ├── theme/
│   │               │   ├── Theme.kt
│   │               │   └── Type.kt
│   │               ├── save/
│   │               │   ├── SaveMoneyScreen.kt
│   │               │   └── SaveMoneyViewModel.kt
│   │               ├── live/
│   │               │   ├── LiveTrainsScreen.kt
│   │               │   └── LiveTrainsViewModel.kt
│   │               ├── offers/
│   │               │   ├── OffersScreen.kt
│   │               │   └── OffersViewModel.kt
│   │               └── components/
│   │                   ├── AnimatedInputChip.kt
│   │                   ├── BookingPlanCard.kt
│   │                   ├── TimePickerBottomSheet.kt
│   │                   ├── StationPickerDialog.kt
│   │                   └── SettingsBottomSheet.kt
│   ├── build.gradle.kts              # Root project config
│   ├── settings.gradle.kts
│   └── gradle/wrapper/
│       └── gradle-wrapper.properties
│
├── backend/                          # Go backend (Fiber)
│   ├── main.go                       # Server entry, routes
│   ├── go.mod                        # Module definition
│   ├── handlers/
│   │   ├── discount.go               # POST /api/search
│   │   ├── codes.go                  # POST /api/codes/northern
│   │   ├── grandcentral.go           # POST /api/codes/grandcentral
│   │   ├── offers.go                 # GET /api/offers
│   │   ├── avanti.go                 # POST /api/avanti/search
│   │   └── live_trains.go            # GET /api/trains/live
│   ├── services/
│   │   ├── northern/
│   │   │   └── code_generator.go     # Salesforce Aura flow
│   │   ├── avanti/
│   │   │   └── avanti_client.go      # Token scraper + UNiDAYS search
│   │   ├── grandcentral/
│   │   │   └── student_discount.go   # .ac.uk email verification
│   │   └── gmail/
│   │       └── oauth_poller.go       # Gmail API code extraction
│   └── convex/
│       ├── schema.ts                 # Convex tables
│       └── queries.ts                # Convex functions
│
└── README.md                         # This file
```

## Features

### Voice Input
- **Dual Provider Support**: Switch between Android SpeechRecognizer and Cactus LLM (7B/13B models)
- **Live Correction**: Tappable chips for accent tolerance with 300ms debounce
- **Metrics Collection**: Room DB tracks latency, correction count, battery drain

### Operator Integrations
1. **Northern Railway (50% Discount)**
   - Salesforce Aura flow submission
   - Gmail API polling for code extraction
   - Code expires at 23:59 same day

2. **Avanti West Coast (UNiDAYS)**
   - Server-rendered token extraction (clientstamp/noncestamp)
   - `/TravelSolution/NewSearch` with `PromotionCode: "UNIDAYS"`

3. **Grand Central (Student Discount)**
   - .ac.uk email validation
   - Multipart form submission to atreemosurvey.com

### UI Components
- **96dp Mic Button** with pulsing animation ring
- **AnimatedInputChip** with infinite transition (1→1.05 scale)
- **Material 3 TimePicker/DatePicker** bottom sheet with quick select chips
- **BookingPlanCard** with CHEAPEST badge and proof chips

## Getting Started

### Android
```bash
cd android
./gradlew assembleDebug
```

### Backend
```bash
cd backend
go mod tidy
go run main.go
```

The server runs on `http://localhost:8080`

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/search` | Search discounts for route |
| POST | `/api/codes/northern` | Generate Northern 50% code |
| POST | `/api/codes/grandcentral` | Submit Grand Central student form |
| POST | `/api/avanti/search` | Search Avanti with UNiDAYS |
| GET | `/api/offers` | List available offers |
| GET | `/api/trains/live` | Get live departures |

## Tech Stack

### Android
- Kotlin 1.9.22
- Jetpack Compose (BOM 2024.01.00)
- Hilt 2.50 (DI)
- Room 2.6.1 (Local DB)
- Retrofit 2.9 + OkHttp 4.12
- kotlinx-serialization 1.6.3
- kotlinx-datetime 0.5.0
- Cactus LLM SDK

### Backend
- Go 1.21
- Fiber v2.52.0
- Google Gmail API
- Convex (optional persistence)

## License

MIT
