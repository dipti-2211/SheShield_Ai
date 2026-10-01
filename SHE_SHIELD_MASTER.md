# SHE_SHIELD_MASTER.md

> **Technical source of truth for the SheShield hackathon project.**
> Last updated: 2026-10-01

---

## A. Project Overview

### What SheShield does

SheShield is a personal safety journey assistant for Android. It helps users plan and monitor journeys through areas with elevated crime-risk by:

1. Presenting multiple route alternatives with **reported-risk exposure scores** computed from real crime-incident data.
2. Letting the user pick a route based on transparent evidence.
3. Monitoring location while the trip is active using a foreground service.
4. Issuing a **safety check-in prompt** when the user enters an elevated-risk segment.
5. Escalating through a trusted-contact call chain via Twilio if the check-in is missed or SOS is triggered.
6. Sharing the user's last known location (reverse-geocoded via Nominatim/OSM) with trusted contacts during escalation.

### End-to-end user journey

```
1. Open app → permissions granted
2. Add trusted contacts (stored locally, transmitted at trip-start)
3. Enter destination → backend fetches ORS route alternatives
4. Risk engine scores each route against crime-incident data from Pinecone
5. App shows route comparison (duration, distance, risk level, evidence)
6. User selects route → presses Start Trip
7. Foreground service begins GPS updates (30s interval)
8. Heartbeat POSTed to n8n every 30s
9. n8n detects risk zone → returns CHECK_IN_REQUIRED event
10. App shows countdown (5 min) + notification with "I'm Safe" / "SOS" actions
11a. User taps "I'm Safe" → check-in sent → monitoring continues
11b. Countdown expires → SOS auto-triggered → Twilio calls Contact 1
12. Twilio status callback → if no-answer, note in log (Contact 2 extension ready)
13. User reaches destination → taps End Trip → service stops
```

### Hackathon story

SheShield demonstrates that personal safety tooling can be built on completely free infrastructure (OSM routing, open crime data, local n8n, free Twilio trial) and that risk scoring can be **explainable and data-driven** rather than relying on black-box AI.

### What the system does NOT claim

- Crime data is not real-time; it reflects historical reported incidents.
- Route scores do not guarantee safety — they indicate **reported-risk exposure**.
- The system cannot access emergency services directly via Twilio in India. It dials trusted personal contacts.
- Pinecone semantic search is not equivalent to precise geographic incident lookup. The risk engine uses coordinates from metadata, not text similarity.
- Demo-mode fixture data is clearly labelled and is NOT real crime data.

---

## B. Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Android App (Kotlin)                           │
│                                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │  Home    │  │  Plan    │  │   Route      │  │   Active Trip     │  │
│  │  Screen  │→ │  Trip    │→ │  Comparison  │→ │   + Check-In      │  │
│  │          │  │  Screen  │  │   Screen     │  │   + SOS Screen    │  │
│  └──────────┘  └──────────┘  └──────────────┘  └───────────────────┘  │
│        │            │                │                    │             │
│        └────────────┼────────────────┘                    │             │
│                     ▼                                      ▼             │
│            ┌──────────────────┐              ┌──────────────────────┐  │
│            │  TripViewModel   │              │  TripTrackingService │  │
│            │  (AndroidVM)     │              │  (Foreground, GPS)   │  │
│            └────────┬─────────┘              └──────────┬───────────┘  │
│                     │                                    │             │
│            ┌────────▼─────────┐                         │             │
│            │  TripRepository  │◄────────────────────────┘             │
│            │  Room DB (local) │                                        │
│            └────────┬─────────┘                                        │
│                     │ Retrofit HTTP                                     │
└─────────────────────┼───────────────────────────────────────────────────┘
                      │  (HTTP via ADB reverse tunnel: adb reverse tcp:5678 tcp:5678)
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    n8n Community Edition (local laptop)                 │
│                                                                         │
│  ┌─────────────────────────────┐                                        │
│  │  Workflow 1: plan-trip      │  POST /webhook/plan-trip               │
│  │  ├─ Validate input          │                                        │
│  │  ├─ Demo mode branch        │                                        │
│  │  ├─ ORS: route alternatives │──→ ORS API (free)                     │
│  │  ├─ Pinecone: crime data    │──→ Pinecone hackolution index          │
│  │  └─ Risk Engine (JS code)   │  Deterministic scoring                 │
│  └─────────────────────────────┘                                        │
│                                                                         │
│  ┌─────────────────────────────┐                                        │
│  │  Workflow 2: active-trip    │  POST /webhook/start-trip              │
│  │  ├─ start-trip              │  POST /webhook/location-update         │
│  │  ├─ location-update         │  POST /webhook/check-in               │
│  │  ├─ check-in                │  POST /webhook/end-trip               │
│  │  └─ end-trip                │                                        │
│  └─────────────────────────────┘                                        │
│                                                                         │
│  ┌─────────────────────────────┐                                        │
│  │  Workflow 3: sos             │  POST /webhook/sos                    │
│  │  ├─ Validate SOS            │  POST /webhook/twilio-status           │
│  │  ├─ Reverse geocode (OSM)   │──→ Nominatim (free)                   │
│  │  ├─ Twilio: call contact    │──→ Twilio (paid, trial available)     │
│  │  └─ Twilio status callback  │                                        │
│  └─────────────────────────────┘                                        │
│                                                                         │
│  GET /webhook/health                                                    │
└─────────────────────────────────────────────────────────────────────────┘

External services:
  OpenRouteService  - free routing API, no billing for reasonable use
  Pinecone          - vector DB hosting crime incident data
  Nominatim/OSM     - free reverse geocoding, no key required
  Twilio            - voice calls (trial credit, pay-as-you-go)
```

**Data flow summary:**
- Android app ↔ n8n webhooks over HTTP (local tunnel for Twilio callbacks)
- n8n ↔ ORS for route geometry
- n8n ↔ Pinecone for crime data retrieval
- n8n ↔ Nominatim for reverse geocoding (SOS only)
- n8n ↔ Twilio for voice escalation

---

## C. Repository Map

```
SheShield_Ai/
├── SheShield.json                    Original workflow (reference/backup - DO NOT DELETE)
├── SheShield_Master_Agent_Prompt.md  Coding agent prompt
├── SHE_SHIELD_MASTER.md              ← this file
│
├── android/                          Android Studio project root
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle.properties
│   ├── local.properties.template     → copy to local.properties with your keys
│   ├── app/
│   │   ├── build.gradle
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── assets/
│   │   │   ├── java/com/sheshield/app/
│   │   │   │   ├── SheShieldApp.kt                Application class
│   │   │   │   ├── data/model/
│   │   │   │   │   ├── TripModels.kt              All data models + API DTOs
│   │   │   │   │   ├── AppDatabase.kt             Room database
│   │   │   │   │   ├── TripDao.kt                 Room DAO
│   │   │   │   │   └── Converters.kt              Room type converters
│   │   │   │   ├── data/network/
│   │   │   │   │   ├── SheShieldApi.kt            Retrofit API interface
│   │   │   │   │   └── NetworkClient.kt           OkHttp + Retrofit setup
│   │   │   │   ├── data/repository/
│   │   │   │   │   └── TripRepository.kt          Data layer orchestrator
│   │   │   │   ├── service/
│   │   │   │   │   ├── TripTrackingService.kt     Foreground GPS service
│   │   │   │   │   └── BootReceiver.kt            Trip restoration after reboot
│   │   │   │   ├── ui/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── viewmodel/TripViewModel.kt
│   │   │   │   │   └── screens/
│   │   │   │   │       ├── HomeFragment.kt
│   │   │   │   │       ├── TripPlanningFragment.kt
│   │   │   │   │       ├── RouteComparisonFragment.kt
│   │   │   │   │       ├── RouteAdapter.kt
│   │   │   │   │       ├── ActiveTripFragment.kt
│   │   │   │   │       ├── SosFragment.kt
│   │   │   │   │       └── ContactsFragment.kt
│   │   │   │   └── util/NotificationHelper.kt
│   │   │   └── res/
│   │   │       ├── layout/                        XML layouts (all screens)
│   │   │       ├── values/                        strings, colors, themes
│   │   │       ├── drawable/                      vector icons
│   │   │       └── navigation/nav_graph.xml       Navigation component graph
│   │   └── src/test/java/com/sheshield/app/
│   │       └── RiskEngineTest.kt                  Unit tests for risk algorithm
│
├── n8n/
│   ├── workflows/
│   │   ├── 01_plan_trip_risk.json    Workflow 1: routing + risk scoring
│   │   ├── 02_active_trip.json       Workflow 2: trip lifecycle + heartbeat
│   │   └── 03_sos_escalation.json    Workflow 3: SOS + Twilio + geocoding
│   ├── risk_engine.js                Standalone risk engine (reference / test)
│   └── n8n_env.template              Environment variable documentation
│
└── demo/
    └── demo_crime_fixtures.json      FICTIONAL demo data for Pinecone seeding
```

---

## D. Android Implementation

### Framework choice
**Native Android with Kotlin + Jetpack.** Chosen because:
- No extra build complexity vs. cross-platform (React Native, Flutter)
- Direct access to FusedLocationProviderClient and foreground service APIs
- Jetpack Navigation, Room, WorkManager, and ViewModel are mature and well-documented
- The one developer who maintains this can read official Android docs directly

### Major dependencies
| Library | Purpose |
|---|---|
| FusedLocationProviderClient | Accurate GPS updates, battery-efficient |
| MapLibre Android SDK | Open-source map rendering (no Google billing) |
| Retrofit + OkHttp | Type-safe HTTP client for n8n API |
| Room | Local SQLite persistence for active trip state |
| Kotlin Coroutines | Async/non-blocking IO |
| Jetpack Navigation | Fragment navigation with back-stack management |
| WorkManager | (Available) Background job scheduling |
| Gson | JSON serialization |

### Screen / navigation structure
```
HomeFragment
├── → TripPlanningFragment
│     └── → RouteComparisonFragment
│           └── → ActiveTripFragment
│                 └── → SosFragment
├── → ContactsFragment
└── → SosFragment (emergency tap)
```

### Location architecture
`TripTrackingService` is a **foreground service** with `foregroundServiceType="location"`.

- On Android 10+, background location requires `ACCESS_BACKGROUND_LOCATION` permission and foreground service type declaration. Both are present in the manifest.
- Location updates use `FusedLocationProviderClient.requestLocationUpdates` with a `LocationRequest` set to `PRIORITY_HIGH_ACCURACY`, 30s interval.
- Updates with accuracy > 50m are discarded to avoid sending stale GPS to the backend.
- The service posts to the n8n backend on every valid location update.

### Active-trip lifecycle
```
User taps Start Trip
  → ViewModel calls startTrip()
  → Repository calls POST /webhook/start-trip → gets session_token
  → ActiveTrip record written to Room DB
  → TripTrackingService started as foreground service with trip_id + session_token
  → Service requests GPS updates
  → Every valid GPS update → POST /webhook/location-update
  → If backend returns risk_event → NotificationHelper shows check-in notification
  → User taps "I'm Safe" (notification action) → Service sends ACTION_CHECK_IN_SAFE
  → Service POSTs /webhook/check-in with status=SAFE
  → If countdown expires → Service sends ACTION_SOS → POSTs /webhook/sos
  → User taps End Trip → Service sends ACTION_STOP → POSTs /webhook/end-trip
```

### State model
`TripState` enum: `IDLE → ACTIVE → CHECK_IN_PENDING → SAFE (back to ACTIVE) | SOS_ACTIVE`

States are stored in Room so they survive process death. `BootReceiver` restores the foreground service after device reboot if a trip is active.

### Notifications
Three channels:
1. `sheshield_trip_active` (LOW importance) — ongoing silent notification while trip runs
2. `sheshield_safety` (HIGH) — check-in request with action buttons
3. `sheshield_sos` (MAX) — SOS state, visible on lock screen

### SOS flow
1. User taps SOS button (or countdown expires in `ActiveTripFragment`)
2. `TripViewModel.triggerSos()` → `TripTrackingService` ACTION_SOS handler
3. Service gets last known location, POSTs to `/webhook/sos`
4. n8n reverse-geocodes, calls Twilio with trusted contact phone
5. Twilio calls contact with spoken message including geocoded location
6. Twilio POSTs status callback to `/webhook/twilio-status`
7. If call not answered → logged, escalation to next contact is ready

### Local persistence
Room database `sheshield.db` with single `active_trip` table. Stores trip ID, session token, state, last GPS, check-in deadline, and trusted contacts (JSON column).

### Network layer
Retrofit with Gson converter. Base URL is `BuildConfig.BACKEND_BASE_URL` (configured via `local.properties`). Default for emulator: `http://10.0.2.2:5678` (Android emulator loopback to host port 5678).

---

## E. Risk Engine Implementation

### Data sources
- **Primary:** Pinecone vector DB index `hackolution` — crime incident data with metadata including `lat`, `lng`, `category`, `incident_date`.
- **Fallback/Demo:** `demo/demo_crime_fixtures.json` — 7 fictional incidents near Kolkata coordinates.

### Data schema (expected Pinecone metadata)
```json
{
  "lat": 22.5731,
  "lng": 88.3641,
  "category": "harassment",
  "incident_date": "2026-07-15",
  "description": "...",
  "severity": "medium"
}
```

If the actual Pinecone data has different field names (e.g. `latitude`/`longitude`), the risk engine code handles both variants.

### Geographic logic
1. Retrieve up to 20 candidate incidents from Pinecone (topK=20).
2. For each incident, compute the **minimum Haversine distance** to any point on the route geometry.
3. Incidents with `min_distance > 0.5 km` (corridor threshold) are excluded.
4. Remaining incidents are scored individually.

### Scoring formula
```
incident_weight = severity_weight(category)
                × recency_weight(days_old)
                × distance_decay(min_distance_km)

recency_weight(d)       = 0.5^(d / 60)       // half-life of 60 days
distance_decay(d)       = exp(-d² / (2×0.3²)) // Gaussian, σ=0.3km
severity_weight(cat)    = lookup table (0.20–1.00)

route_score = min(Σ incident_weight / 10, 1.0)  // normalised to [0,1]
```

### Thresholds
| Score | Level |
|---|---|
| < 0.3 | LOW |
| 0.3 – 0.6 | MEDIUM |
| ≥ 0.6 | HIGH |

### Confidence / limitations
- Pinecone semantic search retrieves by text similarity, not spatial proximity. The risk engine uses **coordinates from metadata**, not text matching.
- If Pinecone records lack lat/lng metadata, the risk engine will find zero corridor incidents and return LOW risk regardless of actual conditions.
- The dataset in `hackolution` index has unknown recency and geographic coverage — audit it before a real demo.
- The score is relative to the dataset, not absolute danger.

### LLM usage
- **LLM is NOT used** to compute the risk score.
- LLM (Gemini) was used in the original workflow for this purpose — this has been removed.
- The LLM could optionally be added back in a separate node to generate human-readable explanations of the already-computed score, using only the structured evidence as input.

---

## F. Routing Implementation

### Provider
**OpenRouteService (ORS)** — free, open-source, no billing for reasonable usage. Uses foot-walking profile by default (appropriate for personal safety). Car/transit profiles also available.

### Alternative routes
ORS `alternative_routes` option requests up to 3 alternatives in a single API call.

### Route geometry
ORS returns GeoJSON-encoded polyline as `geometry.coordinates` — array of `[longitude, latitude]` pairs. The risk engine iterates over these coordinates for corridor checking.

### Route-risk calculation
After ORS returns geometries, the risk engine in n8n Code node:
1. Pulls up to 20 incidents from Pinecone.
2. Scores each route geometry independently.
3. Sorts routes by `risk_score` ascending (safest first).
4. Returns all routes with their scores, evidence summaries, and flags.

### UI display
`RouteComparisonFragment` shows each route as a card with:
- Label and travel time/distance
- Colour-coded risk level badge (green/amber/red)
- Animated LinearProgressIndicator showing risk score (0-100%)
- Incident count and risk summary text
- DEMO DATA badge when using fixture data

---

## G. n8n Changes (Audit of Original → New)

### Original workflow audit findings

| Node | Original purpose | Problem | Action |
|---|---|---|---|
| Telegram Trigger | Entry point — receives user location via Telegram | Wrong channel for an Android app; exposes Telegram to every location update | **REMOVED** |
| Wait2 (1s) | Rate-limit before location check | Unnecessary for webhook-based flow | **REMOVED** |
| If2 (location exists?) | Check if Telegram message has location | Telegram-specific logic | **REMOVED** |
| AI Agent (Gemini) | Risk assessment via LLM | LLM as risk score source — hallucination risk, no real geographic data lookup | **REPLACED** with deterministic risk engine |
| Google Gemini Chat Model | LLM model for agent | Part of LLM-only risk approach | **REMOVED from risk path** |
| Pinecone Vector Store (topK:2) | RAG retrieval | topK=2 is insufficient for geographic analysis; semantic search ≠ geographic search | **RETAINED** but topK raised to 20, used for metadata-based geographic scoring |
| Embeddings Google Gemini | Embeddings for Pinecone | Still needed for Pinecone retrieval | **RETAINED** |
| Simple Memory (buffer) | Agent conversation memory | Not needed for stateless webhook | **REMOVED** |
| Code in JavaScript | Parse LLM JSON output | LLM output parsing is fragile | **REPLACED** with structured risk engine output |
| If (MEDIUM check) | Branch on risk_level | Checked only for MEDIUM while message said "high-risk area" — logic bug | **CORRECTED** — new workflow checks HIGH OR MEDIUM correctly |
| Send a text message | Telegram safety alert | Telegram as safety acknowledgement channel | **REMOVED** — Android notification used instead |
| Wait (20s) | Wait for Telegram reply | Polling with sleep — not event-driven | **REMOVED** |
| HTTP Request (Telegram getUpdates) | Poll for user reply | Exposed bot token in URL, not session-bound | **REMOVED** — bot token rotated |
| If1 (reply = "Yes") | Check Telegram reply text | Not bound to specific user/session | **REMOVED** |
| HTTP Request1 (Nominatim) | Reverse geocode | Good component, moved to SOS workflow | **MOVED** to Workflow 3 |
| Make a call (Twilio) | Call user (hardcoded +91 7488971265) | Hardcoded personal phone number | **REPLACED** — phone from trusted_contacts |
| Wait1 (20s) | Wait before escalation | Timer-based, not status-driven | **REPLACED** with Twilio status callback |
| Make a call1 (Twilio) | Call parent/guardian | Hardcoded personal phone number | **REPLACED** — phone from trusted_contacts |

### Exposed secrets in original workflow
- **Telegram bot token:** `8608000445:AAHn7d70QkFZKVnZ3PBNpOV8YIAH3bdyp9o` — **⚠️ ROTATE IMMEDIATELY**
- **Twilio FROM number:** `+17077541636` — **⚠️ VERIFY and rotate credentials**
- **Hardcoded phone:** `+91 7488971265` — **REMOVED** from all new workflows
- **n8n Cloud URL:** `hacksatya.app.n8n.cloud` — replaced with local n8n

### New workflow layout

**Workflow 1: `01_plan_trip_risk.json`** — Trip Planning & Risk
- Trigger: `POST /webhook/plan-trip`
- Nodes: Webhook → Validate → Demo branch → [ORS routes | Demo fixtures] → Risk Engine → Respond
- Risk engine: pure JavaScript Code node, no LLM

**Workflow 2: `02_active_trip.json`** — Active Trip Management
- Triggers: 4 webhooks (`start-trip`, `location-update`, `check-in`, `end-trip`)
- Nodes: Each webhook → validation Code node → respond
- State: stored in n8n workflow static data (in-memory; survives workflow execution)

**Workflow 3: `03_sos_escalation.json`** — SOS + Health
- Triggers: `POST /webhook/sos`, `POST /webhook/twilio-status`, `GET /webhook/health`
- SOS path: Validate → Acknowledge immediately → Reverse geocode → Call trusted contact 1
- Twilio callback: actual call status drives further escalation (no timer guessing)

---

## H. API Contract

All endpoints: `POST` with JSON body (except health which is `GET`). Base URL: `http://localhost:5678` (local n8n).

### `GET /webhook/health`
**Response:**
```json
{"status": "ok", "version": "1.0", "service": "SheShield n8n"}
```

---

### `POST /webhook/plan-trip`
**Request:**
```json
{
  "origin_lat": 22.5726,
  "origin_lng": 88.3639,
  "dest_lat": 22.5900,
  "dest_lng": 88.3800,
  "dest_label": "Victoria Memorial",
  "trusted_contacts": [{"name": "Mum", "phone": "+919876543210"}],
  "demo_mode": false
}
```
**Response:**
```json
{
  "trip_id": "TRIP-1727789000000-ABC12",
  "routes": [
    {
      "route_id": "TRIP-...-R1",
      "label": "Recommended – Lower reported-risk",
      "duration_seconds": 1320,
      "distance_meters": 3100,
      "risk_level": "MEDIUM",
      "risk_score": 0.38,
      "risk_summary": "3 incidents found near this route (harassment, theft, robbery).",
      "incident_count": 3,
      "geometry": [[88.3639, 22.5726], [88.3700, 22.5760], [88.3800, 22.5900]],
      "is_demo_data": false
    }
  ]
}
```

---

### `POST /webhook/start-trip`
**Request:**
```json
{
  "trip_id": "TRIP-...",
  "route_id": "TRIP-...-R1",
  "trusted_contacts": [{"name": "Mum", "phone": "+919876543210"}]
}
```
**Response:**
```json
{"trip_id": "TRIP-...", "session_token": "TOK-...-XYZ123", "status": "ACTIVE"}
```

---

### `POST /webhook/location-update`
**Request:**
```json
{
  "trip_id": "TRIP-...",
  "session_token": "TOK-...",
  "latitude": 22.5750,
  "longitude": 88.3680,
  "accuracy_meters": 12.5,
  "timestamp_ms": 1727789001000,
  "demo_trigger": false
}
```
**Response (normal):**
```json
{"trip_id": "TRIP-...", "risk_event": null}
```
**Response (check-in required):**
```json
{
  "trip_id": "TRIP-...",
  "risk_event": {
    "type": "CHECK_IN_REQUIRED",
    "risk_level": "HIGH",
    "message": "You have entered a reported-risk area. Are you safe?",
    "check_in_deadline_ms": 1727789301000
  }
}
```

---

### `POST /webhook/check-in`
**Request:**
```json
{"trip_id": "TRIP-...", "session_token": "TOK-...", "status": "SAFE"}
```
`status`: `"SAFE"` or `"SOS"`

**Response:**
```json
{"trip_id": "TRIP-...", "new_state": "ACTIVE", "message": "Check-in confirmed."}
```

---

### `POST /webhook/sos`
**Request:**
```json
{
  "trip_id": "TRIP-...",
  "session_token": "TOK-...",
  "latitude": 22.5750,
  "longitude": 88.3680,
  "trigger": "MANUAL"
}
```
`trigger`: `"MANUAL"` | `"TIMEOUT"`

**Response:**
```json
{"trip_id": "TRIP-...", "status": "SOS_RECEIVED", "message": "Escalation initiated."}
```

---

### `POST /webhook/end-trip`
**Request:**
```json
{"trip_id": "TRIP-...", "session_token": "TOK-..."}
```
**Response:** HTTP 200

---

### `POST /webhook/twilio-status` (Twilio → n8n callback)
Standard Twilio `CallStatus` POST body. Key fields: `CallSid`, `CallStatus`, `To`.

---

## I. Environment Variables and Secrets

| Variable | Purpose | Location | Configured where |
|---|---|---|---|
| `ORS_API_KEY` | OpenRouteService API key | `local.properties` (Android) + n8n Variable | Both n8n and Android |
| `TWILIO_ACCOUNT_SID` | Twilio account identifier | n8n Credentials (Twilio type) | n8n only |
| `TWILIO_AUTH_TOKEN` | Twilio auth token | n8n Credentials (Twilio type) | n8n only |
| `TWILIO_FROM_NUMBER` | Twilio sender number (E.164) | n8n Variable | n8n only |
| `N8N_WEBHOOK_BASE_URL` | Public n8n URL for Twilio callbacks | n8n Variable | n8n only |
| `PINECONE_API_KEY` | Pinecone API key | n8n Credentials (Pinecone type) | n8n only |
| `GOOGLE_GEMINI_API_KEY` | Gemini API (if LLM explanation used) | n8n Credentials (Google Gemini type) | n8n only |
| `BACKEND_BASE_URL` | n8n base URL for Android app | `local.properties` | Android only |

> **⚠️ Never put actual values in source files, documentation, or commit history.**
> The `SheShield.json` original workflow contains an exposed bot token (`8608000445:...`) and Twilio number (`+17077541636`). **Rotate both immediately.**

---

## J. Setup Instructions

### Prerequisites
- Windows with WSL2 (Arch Linux)
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34 (installed via Android Studio)
- JDK 17 (bundled with Android Studio)
- Docker Desktop (for n8n)
- `adb` in PATH
- ngrok (or cloudflared) — free account for Twilio callbacks

### Step 1: Clone and configure Android

```bash
# In WSL or Windows terminal
cd ~/projects/SheShield_Ai/android

# Copy local.properties template
cp local.properties.template local.properties

# Edit local.properties:
#   sdk.dir=<path to Android SDK, e.g. C:\Users\rohit\AppData\Local\Android\Sdk>
#   ORS_API_KEY=<your ORS key from openrouteservice.org>
#   BACKEND_BASE_URL=http://10.0.2.2:5678
```

Get a free ORS API key at [openrouteservice.org/dev](https://openrouteservice.org/dev/#/signup).

### Step 2: Start n8n locally

```powershell
# Windows PowerShell
docker run -d --restart unless-stopped `
  -p 5678:5678 `
  -e N8N_BASIC_AUTH_ACTIVE=false `
  -v n8n_data:/home/node/.n8n `
  --name n8n `
  n8nio/n8n

# Wait ~30 seconds, then open http://localhost:5678
```

### Step 3: Import workflows into n8n

1. Open http://localhost:5678 in your browser
2. Go to **Workflows** → **Import from file**
3. Import these three files in order:
   - `n8n/workflows/01_plan_trip_risk.json`
   - `n8n/workflows/02_active_trip.json`
   - `n8n/workflows/03_sos_escalation.json`
4. Activate all three workflows (toggle at top-right of each)

### Step 4: Configure n8n credentials

In n8n → **Credentials** → **Add new**:

1. **Pinecone account**: Enter your Pinecone API key
2. **Twilio account**: Enter Account SID and Auth Token
3. **Google Gemini API** (optional): if using LLM explanation

In n8n → **Variables**:
- `ORS_API_KEY` = your ORS key
- `TWILIO_FROM_NUMBER` = your Twilio number (E.164)
- `N8N_WEBHOOK_BASE_URL` = your ngrok URL (see Step 6)

### Step 5: Connect Pinecone to Workflow 1

In Workflow 1 (`plan_trip_risk`), open the **Pinecone: Crime Data Lookup** node:
- Select your Pinecone credential
- Confirm index name is `hackolution` (or your index)

### Step 6: Set up tunnel for Twilio callbacks

```bash
# Install ngrok: https://ngrok.com/download
ngrok http 5678

# Copy the HTTPS URL (e.g. https://abc123.ngrok.io)
# Set N8N_WEBHOOK_BASE_URL = https://abc123.ngrok.io in n8n Variables
```

### Step 7: Run the Android app

```bash
# Option A: Emulator
# Open Android Studio → AVD Manager → Create Virtual Device → Pixel 6 → API 34 → Start

# Option B: Physical device
# Enable Developer Options + USB Debugging on your Android device
# Connect via USB

# Forward n8n port to emulator/device
adb reverse tcp:5678 tcp:5678

# Build and run in Android Studio: Run → Run 'app'
```

### Step 8: Seed demo crime data (optional)

```bash
# If your Pinecone index lacks lat/lng metadata, seed with demo fixtures
# for a demonstrable risk calculation.
# Use the Pinecone SDK or console to upsert demo/demo_crime_fixtures.json
# Field names must match: lat, lng, category, incident_date, description
```

---

## K. Running the App

```bash
# Clean build
cd ~/projects/SheShield_Ai/android
./gradlew clean assembleDebug

# Install on connected device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio: Run → Run 'app' (F10)

# Forward n8n port every time you reconnect
adb reverse tcp:5678 tcp:5678

# View logs
adb logcat -s TripTrackingService:D TripViewModel:D TripRepository:D
```

---

## L. Testing

### Automated unit tests (Kotlin)
```bash
cd ~/projects/SheShield_Ai/android
./gradlew test

# Expected output: RiskEngineTest - 9 tests, all pass
# Tests cover: haversine distance, recency decay, distance decay,
#              empty incidents → LOW, single violent crime → HIGH,
#              distant incident → LOW, 7 incidents → HIGH,
#              recency weighting, determinism, threshold boundaries
```

### Manual test procedure (emulator)

**Setup:**
1. Start emulator (Pixel 6, API 34)
2. Ensure `adb reverse tcp:5678 tcp:5678` is active
3. n8n running on localhost:5678 with all 3 workflows active

**Test: Normal trip flow**
1. Launch app → grant Location + Notification permissions
2. Tap **Trusted Contacts** → add your phone in E.164 format → save
3. Tap **Plan a Safe Journey** → type "Victoria Memorial, Kolkata" → tap Find Routes
4. Observe route cards with risk scores (if Pinecone data is present) or DEMO DATA labels
5. Select the first route → tap **Start Trip**
6. Observe foreground notification: "SheShield – Trip Active"
7. Open **Extended Controls** in Android Studio emulator → **Location** tab
8. Set latitude 22.5750, longitude 88.3680 → tap **Send** (simulates movement)
9. Verify heartbeat reaches n8n (check n8n execution log)
10. Tap **End Trip** → verify trip completed, notification dismissed

**Test: Check-in flow (demo trigger)**
1. Start a trip as above
2. Send a location update with `demo_trigger: true` via n8n test (or modify the location-update code node temporarily)
3. Observe check-in notification appears with "I'm Safe" and "SOS" buttons
4. Tap **I'm Safe** → verify notification clears, monitoring continues
5. Restart from step 2 — this time let the 5-minute countdown expire
6. Verify SOS is auto-triggered and n8n SOS workflow executes

**Test: Manual SOS**
1. From any screen, tap the large **SOS** button
2. Verify SOS screen appears with last location and emergency dial button
3. Verify n8n SOS workflow execution log shows: geocode call + Twilio call attempt

### Twilio test procedure
- Ensure a valid Twilio account with at least one verified number
- SOS test: trigger SOS → check n8n execution → confirm Twilio called your contact number
- Check n8n execution log for `twilio-status` callback response
- If no answer: CallStatus = "no-answer" will appear in the log

### Emulator GPS procedure
```
Android Studio → Extended Controls (⋯ icon in emulator) → Location
→ Enter: Latitude 22.5730, Longitude 88.3641
→ Click "Send"
→ Observe TripTrackingService log: location received and posted to backend
```

### Demo mode procedure
1. Contacts screen → enable **Demo mode** toggle
2. Plan a trip → results will include `is_demo_data: true` routes
3. All demo routes are clearly labelled ⚠️ DEMO DATA in the UI
4. Backend will skip ORS/Pinecone calls and return fixture data

---

## M. Complete Hackathon Demo Script

**Duration: ~8 minutes**

### Pre-demo setup (do before judges arrive)
- [ ] n8n running, all 3 workflows active and green
- [ ] ngrok tunnel running, `N8N_WEBHOOK_BASE_URL` set
- [ ] Android emulator running (Pixel 6, API 34), app installed
- [ ] `adb reverse tcp:5678 tcp:5678` confirmed
- [ ] Demo mode: **OFF** (for real data) or **ON** (for reliable demo)
- [ ] One trusted contact added (your own number or a test number)
- [ ] Pinecone has data OR demo mode is on

### Demo script

**[0:00] Opening**
> "SheShield helps you plan safer journeys. Instead of just giving you one route and hoping for the best, it shows you how much reported-crime exposure each route carries and lets you decide."

**[0:30] Plan a trip**
1. Tap **Plan a Safe Journey**
2. Type: `Victoria Memorial, Kolkata`
3. Tap **Find Routes**
4. Wait ~2s for routes to appear
> "The app has asked our backend to fetch route alternatives from OpenRouteService and score each one using actual crime incident data from our Pinecone database."

**[1:30] Show route comparison**
> "Here we have two routes. The recommended one has a MEDIUM risk score of 38 — 3 incidents reported nearby. The shorter route is faster but carries a HIGH score of 72 — 7 incidents."

**[2:00] Show risk evidence**
> "Tap a route. You can see exactly which incident categories were found, how old they are, and how close they are to the route. This is not AI guessing — it's computed from actual data points."

**[2:30] Start the trip**
1. Select Route 1
2. Tap **Start Trip**
3. Show foreground notification in status bar
> "The app has started a foreground service — it will keep tracking location even if you switch apps."

**[3:00] Simulate location movement**
1. In emulator: Extended Controls → Location → 22.5730, 88.3641 → Send
> "As the user moves, the app sends heartbeats to our n8n backend every 30 seconds."

**[3:30] Trigger check-in (demo)**
> "Now let's say the user enters a high-risk area."
1. (In n8n: temporarily set demo_trigger=true in location-update node and execute manually, OR explain that the backend detected a risk zone)
2. Show check-in notification: "⚠️ Safety Check-In Required — I'm Safe / SOS"
> "The user gets 5 minutes to confirm they're safe. There's a visible countdown."

**[4:00] Miss the check-in**
> "Watch what happens if they don't respond..."
1. Tap **Trigger SOS** (for demo speed)
2. Show SOS screen

**[4:30] SOS escalation**
> "SOS has been sent to our backend. Let's look at n8n."
1. Switch to n8n execution log — show SOS workflow executing
2. Show Nominatim geocoding node result
3. Show Twilio call node executing
> "The backend reverse-geocoded the last location using OpenStreetMap and called the trusted contact via Twilio, telling them the address."

**[5:30] Twilio status**
> "n8n is waiting for a real call-status callback from Twilio, not just a timer. If the call is answered, we stop. If it's unanswered, the log shows exactly why and we can escalate to the next contact."

**[6:00] Emergency dial**
> "If the user wants to call emergency services directly, one tap dials 112."
1. Tap **Call 112** button — show phone dialer opening (no actual call)

**[6:30] End trip**
1. Tap **Cancel SOS & End Trip**
2. Show home screen with no active trip
> "When the trip ends, the service stops, the notification clears, and all state is cleaned up."

**[7:00] Architecture close**
> "Everything here: the routing, the risk scoring, the geocoding — all free. OpenRouteService, OpenStreetMap, local n8n Community Edition. The only paid service is Twilio for the actual phone call."

---

## N. Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| "Missing field: origin_lat" in n8n | Android sent wrong request body | Check Retrofit URL / base URL in local.properties |
| Routes not loading | n8n not reachable from emulator | Run `adb reverse tcp:5678 tcp:5678` |
| Risk scores all 0 | Pinecone data has no lat/lng metadata | Enable demo mode OR verify Pinecone record metadata fields |
| ORS returns 403 | Invalid API key | Check ORS_API_KEY in n8n Variables |
| Twilio not calling | N8N_WEBHOOK_BASE_URL not set / ngrok expired | Restart ngrok, update variable, re-activate workflows |
| App crashes on launch | Missing notification channel or Room migration | `adb logcat` for stack trace; clean install (`adb uninstall com.sheshield.app.debug`) |
| GPS not updating in emulator | Extended Controls not sending correctly | Use Extended Controls → Location → click Send again |
| "Invalid session token" from backend | App restarted but n8n lost in-memory state | End trip and start a new one (in-memory state resets on n8n restart) |
| Notification not appearing | Permission denied | Settings → Apps → SheShield → Notifications → Enable |
| Background location not working | Missing permission | Grant "Allow all the time" location permission in system settings |

---

## O. Security / Privacy

### Secret handling
- No secrets are stored in Android source code or the APK.
- ORS API key is in `local.properties` (git-ignored).
- Twilio credentials are in n8n Credentials (encrypted by n8n, not in workflow JSON).
- `SheShield.json` (original) contains an exposed bot token and phone number — treat as compromised.

### Credentials to rotate immediately
1. **Telegram bot token** `8608000445:AAHn7d70QkFZKVnZ3PBNpOV8YIAH3bdyp9o` — revoke via BotFather
2. **Twilio credentials** associated with `+17077541636` — reset in Twilio Console

### Location data handling
- GPS is transmitted to n8n only during an active trip.
- n8n stores last location in workflow static data (in-memory, cleared on n8n restart).
- Location is shared with trusted contacts only during SOS.
- No analytics or external logging of precise GPS.

### Session security
- Each trip gets a unique `session_token` generated server-side.
- Token is validated on every heartbeat and check-in request.
- Tokens are stored in the local Room database (protected by device encryption if enabled).

---

## P. Known Limitations

| Limitation | Details |
|---|---|
| **Data freshness** | Pinecone data is static; incidents are not refreshed in real-time |
| **Spatial precision** | Pinecone semantic search is not a spatial database; geographic accuracy depends entirely on coordinate metadata quality |
| **False positives** | A route near a high-density reporting area may score HIGH even if the specific path is low-risk |
| **False negatives** | Underreported areas (rural, low-reporting communities) will show LOW risk incorrectly |
| **Connectivity** | App degrades gracefully but cannot escalate without network |
| **GPS accuracy** | Updates with >50m accuracy are discarded; indoors GPS may be unreliable |
| **n8n state** | Trip state is in-memory in n8n static data — lost if n8n restarts mid-trip |
| **Twilio India** | Twilio cannot call Indian emergency services (112) directly; only trusted personal contacts |
| **Demo vs real data** | Demo fixture data (7 fictional incidents) produces predictable risk scores not representative of any real area |
| **ORS alternatives** | ORS foot-walking profile may not always return 3 alternatives on short routes |
| **Background permission** | Android 10+ requires "Allow all the time" location permission — many users may deny this |

---

## Q. Decisions and Rationale

### Why native Android + Kotlin, not Flutter or React Native?
Direct access to Android foreground service APIs and FusedLocationProviderClient without bridge layers. For a single developer building a safety app where location reliability is critical, the complexity trade-off of cross-platform isn't worth it.

### Why MapLibre, not Google Maps?
Google Maps requires billing setup even for development. MapLibre is fully open-source, uses OpenStreetMap tiles, and has no billing at any usage level. For a hackathon prototype, this eliminates one dependency entirely.

### Why n8n for orchestration, not a custom Python/Node backend?
n8n provides visual debugging, built-in webhook support, and no deployment overhead. The workflow JSON is the artifact. A custom backend would require deployment, monitoring, and more setup for identical functionality at this scale.

### Why deterministic risk scoring, not LLM?
LLMs cannot be constrained from hallucinating crime coordinates or incident counts. A deterministic formula over real data is reproducible, explainable, and testable — which the prompt explicitly requires.

### Why Pinecone retained despite its geographic limitations?
The existing data is in Pinecone. Rather than rebuilding the data pipeline for a hackathon, we work with the coordinate metadata that Pinecone stores. If the data has coordinates, the algorithm works correctly.

### Why demo mode?
A hackathon demo cannot depend on live external data producing an interesting scenario in front of judges. Demo mode provides a reliable, predictable path while being clearly labelled so no one mistakes it for real crime data.

---

## R. Future Improvements

1. **Real-time crime data API** — integrate with police APIs (e.g. data.gov.in crime stats) for fresher data
2. **Spatial database** — replace Pinecone with PostGIS for true geographic queries; would eliminate the semantic-search geographic mismatch
3. **Route segment risk overlay** — render risk level as colour on the route polyline, not just a card badge
4. **Multiple contact escalation** — n8n workflow already has the structure; extend to iterate through all trusted contacts on no-answer
5. **Offline safety mode** — cache route risk data locally so the app can issue check-in prompts without network
6. **Trip history** — Room DB already stores trips; surface history screen
7. **Share trip live link** — generate a short URL that trusted contacts can open to see live location
8. **Background location accuracy** — investigate WorkManager + periodic FusedLocation for Android 12+ battery restrictions
