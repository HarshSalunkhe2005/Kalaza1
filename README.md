# Kalaza Care

An Android application for managing day-to-day operations at the Kalaza Care assisted-living facility in Pune — patients, staff, medication administration, vitals, care notes, doctor visits, and role-based approval workflows.

## Tech Stack

- **Platform:** Android (min SDK 26, target SDK 35)
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM with `StateFlow`
- **Backend:** [Supabase](https://supabase.com) — Postgres (via `postgrest-kt`) for all app data, Supabase Auth for staff login, Realtime for live sync, Edge Functions for admin actions and scheduled jobs
- **Push notifications:** Firebase Cloud Messaging (kept independent of the rest of the backend)
- **Local cache / offline support:** Room

## Features

- **Role-based access** — Super Admin, Supervisor, and Staff, each with a distinct dashboard, permission set, and navigation
- **Patient management** — demographics, medical history, admission/archival, searchable dashboard
- **Medication tracking (Med tab)** — recurring and one-time doses, dose-tag scheduling (Morning/Afternoon/Evening), a two-checkpoint allotment → administration workflow, and a per-day administration history view
- **QR-scan evidence** — doses are given by scanning the patient's QR code on a dedicated Scan tab (batch confirmation per round); each dose can only be ticked from its scheduled time until 30 minutes later, judged on a server-verified clock. An Admin override handles late corrections and still requires the matching patient/round QR
- **Vitals, Utility logs, Doctor Visits, Care Notes** — per-patient clinical records, with a 24-hour grace period before an edit requires approval
- **Approval Queue** — Staff edits route through Super Admin review instead of applying directly
- **Audit Log** — a permanent, read-only record of key actions across the app
- **Real-time sync** — dashboards and queues update live via Supabase Realtime subscriptions
- **Push notifications** — in-app + FCM push for edit requests, allotment requests, and medication deadline reminders/escalations (a scheduled Edge Function watchdog)
- **Offline support** — a local Room-backed cache for viewing data without connectivity, plus a write queue that safely replays queued changes on reconnect (flagging real conflicts instead of silently overwriting)
- **Wi-Fi-scoped login** — login is gated to the facility's own network (matched by gateway IP), with a password-protected off-network bypass for Super Admin
- **Excel export** — per-patient, date-ranged summary reports as styled `.xlsx` files

## Project Structure

```
app/src/main/java/com/kalazacare/app/
├── data/
│   ├── model/          # Domain models (Patient, MedicationEntry, Staff, ...)
│   ├── repository/      # Repository interfaces + Supabase-backed and offline-cached implementations
│   ├── local/            # Room database, cache DAO, offline write-queue entities
│   ├── sync/             # Offline sync manager (queues, replays, conflict detection)
│   └── remote/           # Supabase client setup
├── ui/                   # Compose screens, ViewModels, navigation
├── service/              # Firebase Cloud Messaging service
└── util/                 # Session management, Wi-Fi gating, date/time helpers

context/                  # Project requirements and running implementation history
```

## Setup

### Prerequisites

- Android Studio (a recent stable release — this project uses AGP 8.13.2 / Gradle 8.14.5)
- A Supabase project with the schema this app expects (see `context/project_state_and_workflows.md` for the verified schema and an introspection query to check any project against it)
- A Firebase project configured for Cloud Messaging

### Configuration

1. Clone the repo.
2. Add your own `app/google-services.json` (Firebase project config) — this file is gitignored and must be supplied per environment.
3. Point the app at your Supabase project — see `data/remote/SupabaseClients.kt` for where the URL and anon key are configured.
4. Open in Android Studio and sync Gradle, or build from the command line:

```bash
./gradlew assembleRelease   # shrunk APK -> app/build/outputs/apk/release (debug-signed so phones update in place)
```

### Backend

The database schema, Row Level Security policies, and Edge Functions all live in the Supabase project itself (Dashboard / SQL Editor / Edge Functions); the repo holds no backend source. `context/backend/` keeps the rollout scripts for the switch to the 30-minute dose-window rules.

## Status

Feature-complete for its current scope: dose windows per medicine (T to T+30 min, server-verified clock), QR-scan administration with offline support, reminders and missed-dose alerts, per-day medication history, 30-day filtered views and a shrunk release build. See `context/project_state_and_workflows.md` for the current rules, backend notes and rollout status.
