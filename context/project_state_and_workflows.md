# Kalaza Care - Project State & Workflows

## Overview
Kalaza Care is an Android application designed for a clinic/hospital environment to manage patients, staff, medication (MAR), vitals, care notes, and doctor visits. The app incorporates a role-based access control system featuring Super Admins, regular Staff, and Supervisor, with an intricate approval queue for staff-made edits and a two-checkpoint (allot → administer) medication workflow.

## Technology Stack
- **Platform:** Android (Min SDK 26, Target SDK 35)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel) with StateFlow
- **Backend:** Supabase — Postgres (via `postgrest-kt`) for all app data, Supabase Auth for login/staff accounts. Schema + Row Level Security policies live directly in the Supabase project (Dashboard/SQL Editor) — there is currently no `seed.sql` (or any schema-as-code file) checked into this repo; a prior version of this doc claimed one existed at the repo root, that was wrong.
- **Push Notifications:** Firebase Cloud Messaging only (kept independent of the rest of the backend — no Firestore/Firebase Auth/Firebase Storage remain in the project).

---

## What is Done (Completed Features)

### 1. Role-Based Access & Authentication (UI & Logic)
- **Super Admin Role** (`UserRole.SUPER_ADMIN` — this is the old, fully-privileged `ADMIN`, renamed): Has full access. Can view the Summary Tab, add new patients directly, add/revoke/delete staff members, approve/reject staff edit requests, and is the only role that can add/edit/delete MAR (medication) entries. `SessionManager.isAdmin()` checks this role — the name wasn't changed everywhere it's used, since the check's meaning ("today's fully-privileged admin") didn't change, only the enum's name did.
- **Staff Role (Regular):** Has limited access. Cannot view the Summary Tab. Any edits to patient data generate an Approval Request instead of saving directly.
- **Supervisor Role:** Same dashboard and permissions as Regular Staff, plus an additional **Medicine** tab for allotting doses ahead of administration (see workflow below). MAR add/edit/delete is Super Admin-only — Supervisor cannot touch MAR entries directly, only the allotment round in the Medicine tab.
- **Login is by Name, not Email.** The login screen asks for the staff member's Name; `AuthRepository.login` matches `Staff.name` case-insensitively, looks up a synthetic per-staff email, and authenticates for real against Supabase Auth (hashed password, server-side). `Staff.email` still exists as a separate contact-info field (shown in Config), it's just never the login credential. Super Admin assigns each staff member's password at creation time (`StaffRepository.addStaff`).

### 2. Navigation & UI Shell
- **Bottom Navigation Bar:** Context-aware based on the logged-in user. Super Admin sees: Overview, Patients, Approvals, Audit Log, Config, Summary. Supervisor sees: Tasks, Patients, Scan, Medicine. Regular Staff sees: Tasks, Patients, Scan.
- **Top App Bar:** Customized to display the brand's red stripe (`KalazaRed`), the app logo, dynamic screen titles, and a clickable Notification Bell. On the Patients/Dashboard tab, the subtitle line shows the logged-in staff member's own name instead of a static "Dashboard" label.

### 3. Patient Management
- **Dashboard:** Displays quick stats (total patients, pending meds, pending approvals) and a searchable list of patient cards. Reloads on resume so returning from another tab shows current data.
- **Patient Profile:**
  - **Details Tab:** View/Edit patient demographics and medical history, including the **Admission Date** (now an editable date picker, previously fixed at creation time). Staff edits go to the Approval Queue. Super Admin edits save immediately and log to Audit.
  - **Vitals Tab:** Record and view daily vitals (BP, Heart Rate, Temp, SpO2). Every role can edit an existing row via a pencil icon on that row: edits within 24h of the original entry apply directly (and are logged to Audit); edits made more than 24h after the entry go through the Approval Queue instead. Super Admin always edits directly.
  - **Med Tab** (renamed from "MAR" — see section 20): Track scheduled medications. Add/edit/delete of Med entries is Super Admin-only. Every dose is either **recurring** (`isRecurring = true`, the default — due every day regardless of its stored date, optionally narrowed to specific weekdays via `recurringDays`) or a **one-time dose** on a specific date (toggle + date picker in Add Medication). Every dose also carries a mandatory `DoseTag` (Morning/Afternoon/Evening — see section 20). Overdue status is computed live on every read: for a one-time dose against its own stored date, for a recurring dose against *today's* date — and for a recurring dose, ALLOTTED/ADMINISTERED also resets to a fresh PENDING/OVERDUE view once its day has passed, so "given yesterday" doesn't suppress today's occurrence. Marking a dose "given" here requires scanning the medicine's QR code as evidence, and shows whether the dose has been allotted yet; any staff can flag a "Request Allotment" if supervisor forgot. (The Scan tab — section 20 — is the other, batch-oriented way to mark doses given.)
  - **Utility Tab:** Log usage of medical utilities. Columns/fields are generated dynamically from whatever's configured in Config → Utility Items — adding a new item type there shows up here immediately, no code change needed. Row-level edit uses the same 24h-grace-then-approval policy as Vitals.
  - **Doctor Visits Tab:** Log specific instructions and notes left by visiting doctors, now including a visit **time** alongside the date. Visits can also be **deleted** — Super Admin deletes directly (logged to Audit); every other role's delete request goes through the Approval Queue first.
  - **Care Notes Tab:** Add general nursing/care notes for the patient, and edit an existing note via its pencil icon — same 24h-grace-then-approval policy as Vitals/Utility.
- **Medicine Tab (Supervisor only):** A facility-wide "rounds" view of every dose still awaiting allotment today, plus any pending allotment requests raised by regular staff. Allotting a dose requires scanning the medicine's QR code as evidence. This is unchanged by the MAR-CRUD restriction above — allotment rounds and MAR entry CRUD are separate concerns.

### 4. Admin Workflows
- **Approval Queue:** A dedicated screen where Admins can review, approve, or reject field-level changes requested by Staff. Approving applies the change directly to the Patient record (not just the request's status) and logs to Audit; rejecting also logs to Audit.
- **Audit Logs:** A read-only chronological log of all major actions (Patient Added, Patient Edited, Approvals, Rejections, Medication Allotted, Patient Archived), each with the correct icon for its action type.
- **Configuration / Staff Management:** Admins can add new staff (Regular or Medicine role), revoke existing staff, activate revoked staff, or delete staff entirely. Admins cannot revoke themselves.
- **Archive Patient:** From a patient's profile (overflow menu, Admin-only), Admin can archive a patient's record after confirming. Archived patients are hidden from the main Dashboard list by default; a "Show Archived" toggle on the Dashboard reveals them (marked with an "Archived" badge).

### 5. UI Polish & Theming
- Strict adherence to the `KalazaRed` and `KalazaDarkMaroon` color palette.
- Pixel-perfect empty states (e.g., "No Patients Found", "No Audit Logs").
- Elegant tab navigation within the Patient Profile. The tab pager's own swipe gesture is disabled (`userScrollEnabled = false`) so it no longer fights with the Vitals/Utility tables' sideways scroll — tabs are still switchable by tapping.
- Staff cards in Config never squeeze the name/role badge regardless of state — actions (Revoke, or Activate+Delete) live on their own footer row instead of competing for space in the header.

### 6. In-App Notification System — fully live, including real push
- A real Notifications screen (bell icon → badge count → list), reachable from Dashboard and the Medicine tab.
- Notifications are generated at the actual point of the event, not just seeded: a staff edit request notifies all Admins; an approval/rejection notifies the requester; a supervisor allotment request notifies all Supervisors; fulfilling one notifies the requester back. Tapping a notification marks it read and navigates to the relevant screen (Approval Queue, Medicine tab, or the specific patient's profile).
- **Push delivery is fully wired, both sides.** Client side: `KalazaMessagingService` requests the `POST_NOTIFICATIONS` permission, registers/refreshes the device's FCM token to the staff row, displays a real system notification (foreground/background/killed, all states), and deep-links a tap back into the right screen via `MainActivity.onNewIntent`. Server side: a Supabase Database Webhook fires the `send-push` Edge Function on every new `notifications` row, which resolves recipient(s) by `recipient_staff_id`/`recipient_role`, mints an FCM v1 OAuth token from a Cloud-Messaging-scoped service account, and sends a data-only push. Confirmed working end to end, app closed included.
- **Medication deadline reminders & escalation** (`supabase/functions/medication-watchdog`, scheduled every minute via `pg_cron`): 15 min before a dose's deadline, Staff + Supervisor get a reminder; 5 min after, Admin gets a missed-dose alert; 10 min after, Super Admin gets an escalation. Each checkpoint fires at most once per (IST) calendar day per dose, tracked via `reminder_sent_at`/`admin_alert_sent_at`/`superadmin_alert_sent_at` on `medications`.
- **Real-time sync:** Dashboard, Approval Queue, Medicine tab, and the Notifications screen all subscribe to Supabase Realtime on their underlying tables (`patients`, `medications`, `approval_requests`, `allotment_requests`, `notifications`) and refetch automatically on any change — no more waiting to navigate away and back to see another staff member's update.

### 7. Input Validation
- Phone numbers (staff phone, patient emergency phone) only accept digits as typed and require exactly 10 before the form can submit.
- Patient age must be between 1 and 120.
- Staff email is validated against a standard email pattern before Admin can add them.
- Vitals fields (pulse, BP, SpO2, sugar) only accept digits; temperature accepts digits and a decimal point. All vitals fields are also range-checked (e.g. pulse 30–220, SpO2 0–100, temperature 90–110°F) with inline error text — an out-of-range value blocks Save.
- Utility quantities only accept digits.
- Staff names are trimmed before being stored, and login matching trims and case-folds the name, so trailing/leading whitespace never blocks a valid login.
- Medications can still be scheduled before a patient's admission date (there are legitimate backdating reasons), but doing so now shows a warning Toast instead of silently accepting it.

### 8. Doctor Visit Editing & Generalized Approval
- Doctor visits are editable and deletable by any role. Super Admin edits/deletes apply directly (+ Audit Log entry); Staff/Supervisor edits/deletes generate `ApprovalRequest`s (field-level diffs for edits, a single delete-flagged request for deletes) routed to the Approval Queue.
- `ApprovalRequest` now carries an `entityType` (`PATIENT`, `DOCTOR_VISIT`, `VITAL`, `UTILITY`, or `CARE_NOTE`), an `entityId`, and an `action` (`EDIT` or `DELETE`), so `ApprovalViewModel.approve()` knows which repository to apply the diff to and whether to delete or patch the record.
- Vitals, Utility records, and Care Notes are now also editable by every role, via the row-level pencil icon on each entry. These three follow a **24h grace window**: edits made within 24h of the original entry's timestamp apply directly for any role (mistakes happen — all such edits are still logged to Audit); edits made after 24h route through the Approval Queue like everything else. Super Admin always edits directly regardless of age. There is currently no delete UI for Vitals/Utility/Care Notes (edit-only, matching what was asked for) — a follow-up if delete is ever needed there too.

### 9. Time Input
- All medication scheduling (Add Medication, Edit Medication) now uses a 12-hour HH:MM + AM/PM picker (`TimeOfDayField`) instead of raw 24-hour text fields, while still storing/computing everything internally as 24-hour `LocalTime`.

### 10. Patient Profile Robustness
- Editing a patient whose data hasn't finished loading (e.g. a stale/bad deep link) no longer risks a `NullPointerException` — Save is disabled and blocked with a "still loading" message until the patient record is actually present.
- A patient profile for a non-existent ID now shows a "Patient not found" state with a Go Back button, instead of spinning forever indistinguishably from a real loading state.
- `MedicationEntry`'s live-computed OVERDUE/PENDING status is reversible in both directions — editing a dose's time to a later slot un-overdues it instead of leaving it stuck OVERDUE forever. Verified by trace: this self-heals on every subsequent read regardless of what gets persisted mid-edit, so no residual bug remains here.

### 11. Role Restructure: Super Admin + Photo-Audit-Only Admin
- `UserRole.ADMIN` was renamed to `UserRole.SUPER_ADMIN` (keeps every existing admin power — `SessionManager.isAdmin()` now checks `SUPER_ADMIN`). A brand new, much more restricted `UserRole.ADMIN` was added: on login it goes straight to a standalone Photo Audit screen (`Routes.PHOTO_AUDIT`) and has no other access anywhere in the app — no dashboard, no approvals, no config, no bottom nav.
- `StaffEditor`'s "Add Staff" role picker now excludes only `SUPER_ADMIN` (was excluding the old `ADMIN`) — Staff/Supervisor/Admin are all assignable through Config, Super Admin accounts aren't created through this dialog.
- `RoleBadge` gained a distinct color for the new `ADMIN` role so it's visually distinguishable from `SUPER_ADMIN` in staff lists.

### 12. MAR (Medication) Fixes & Delete
- MAR add/edit/delete is Super Admin-only (`SessionManager.isAdmin()`); a delete action (with confirmation dialog) was added next to the existing edit pencil — there was previously no way to remove a MAR entry at all.
- `TimeOfDayField`'s AM/PM `FilterChip`s previously left the selected chip's label using the theme's default (sometimes low-contrast) color; both chips now use an explicit `selectedLabelColor`/`labelColor` pair for reliable contrast in both states.
- `TimeOfDayField`'s HH/MM text fields now clamp live as you type (HH to 1–12, MM to 0–59) instead of only clamping the emitted value while letting the displayed text show something out of range.

### 13. Summary Report: Date Range + Per-Patient xlsx Export
- The Summary screen takes a **date range** (start + end date pickers); `SummaryViewModel.load(start, end)` aggregates stats and per-patient breakdowns across the whole range, `buildRangeReport()` returns the raw per-patient data for export.
- **(Superseded) Originally exported one combined multi-sheet workbook** (a Summary tab + one tab per patient). **Replaced** with **N separate styled `.xlsx` files, one per patient**, matching a reference "Simple Patient Report" template the team supplied: a dark navy title banner (patient name + date range), a blue column-header row (Date / Diagnosis / Medication / Vitals / Utilities / Notes / Signed By), alternating white/light-blue row banding, wrapped text, and frozen header rows. One row per calendar date in the selected range.
- `XlsxWriter` (`util/XlsxWriter.kt`) was rebuilt from a plain unstyled writer into one that emits real OOXML styling (`styles.xml`: fonts, fills, cellXfs), merged cells, column widths, row heights, and frozen panes — still dependency-free, no Apache POI. Its API is now purpose-built (`buildPatientReport(patientName, rangeLabel, rows)`) rather than a generic multi-sheet writer, since only this one report shape is ever produced.
- **Known modeling limitation, carried over from the data model (see "What is Remaining #1" below):** recurring medications have no true per-day history (their live status/administeredBy reset daily), so a recurring dose's status is only accurate for *today* — it's shown only on today's row in the report, not repeated across every day in a past range. One-time doses use their real, fixed `scheduledDate` regardless of range position.
- Each file is saved straight to the device's **Downloads** folder via `DownloadsSaver` (`util/DownloadsSaver.kt`) — no share sheet, no "send to" step, no zip bundling.

### 14. Wi-Fi-Scoped Login Gate
- Login is blocked unless the device's connected Wi-Fi network is on an allow-list, matched by the network's **gateway/router IP** (`WifiChecker.currentWifiGatewayIp`, via `ConnectivityManager`/`LinkProperties`), not by SSID — SSID reading proved unreliable across recent Android versions/OEMs (kept coming back redacted despite every documented permission being granted). `ALLOWED_GATEWAY_IPS` in `WifiChecker.kt` holds the facility's known router IPs.
- A visible "Skip Wi-Fi check (testing)" switch exists on the login screen's blocking dialog, intentionally left in for now — **known, accepted gap**: any staff member can currently bypass the network gate with it. Remove or otherwise lock this down before final handover (see Security Hardening Backlog below).
- Deferred (explicitly, not started): a way for staff/Super Admin to authenticate off-network during hospital visits — leaning toward Super-Admin-issued temporary access codes as the approach, but **intentionally not built yet** — scheduled for a later pass.

### 15. Super Admin Landing Screen: "Today" Overview
- Super Admin's post-login landing screen (`SuperAdminOverviewScreen.kt`) was first built as a tabbed Today/Weekly-Report/Utilities/Patient-Details view, then simplified down to a single, denser "Today" view per product direction — those other three tabs were removed outright, not just hidden. Current screen: stat cards, a "Needs Your Attention" section listing pending Approval/Allotment requests in full (not just a count), and a Daily Breakdown (By Category incl. Doctor Visits / By Patient toggle).
- Staff/Supervisor now land on a medicine-focused **Todo List** screen instead of the Patients dashboard (`ui/todo/TodoListScreen.kt`) — a flat, time-sorted list of today's medication tasks.
- Top app bar was rebuilt as a plain, explicitly-sized `Row` instead of Material3's `TopAppBar` (`KalazaTopBar.kt`) — the latter enforces its own fixed ~64dp minimum height regardless of content, which is why an earlier attempt to resize the logo/title didn't visibly change the bar's height at all.

### 16. Performance: Batched Queries
- `DailySummaryViewModel.load()` used to do 1 + 3×N sequential Supabase round-trips (three per-patient queries, looped over every patient) to build the Super Admin "Today" view — now a fixed 4 queries regardless of patient count, via `getVitalsForDate`/`getUtilityForDate`/`getVisitsForDate` (one query across all patients, grouped client-side).
- `ApprovalRepository`/`AllotmentRequestRepository.getPendingRequests()` and `NotificationRepository`'s unread-count/mark-all-read used to fetch the entire table's history and filter in Kotlin — now filtered server-side (`eq("status", ...)` / `eq("is_read", false)`), and `markAllReadForRecipient` is a single UPDATE instead of one per row. This mattered more than the query above long-term: it scaled with *time* (rows accumulate forever), not patient count.

### 17. RLS Policy Fixes (from a full policy review against actual app call sites)
- `doctor_visits_insert` was `is_super_admin()`-only, but the UI (`DoctorVisitsTab.kt`) deliberately lets every role schedule a visit — Staff/Supervisor tapping the FAB was silently failing (no try/catch on `addVisit()` either). Relaxed to `is_active_staff()` to match the UI's actual, intended behavior.
- `notifications_insert` was `is_active_staff()`-only with no check on *which* notification type was being sent — any staff account could insert a fake notification (e.g. a spoofed "approved by Somnath") addressed to anyone. Tightened per-type to match exactly which role actually triggers each one in the app: `APPROVAL_REQUESTED`/`ALLOTMENT_REQUESTED` (any active staff, they self-submit), `ALLOTMENT_FULFILLED` (Supervisor+), `APPROVAL_APPROVED`/`APPROVAL_REJECTED` (Super Admin only).
- Full policy set otherwise checked out clean against real insert/update/delete call sites (`patients`, `medications`, `staff`, `approval_requests`, `allotment_requests`, `audit_log`, `utility_items` all correctly gated and matched by the app's own role checks).

### 18. Mock Data
- A comprehensive 5-patient mock dataset exists (delivered as standalone SQL, not committed to the repo — run manually in the Supabase SQL Editor) covering every table, with distinct staff actors on each side of every allotment/administration/approval chain (no self-contradictory transactions). Required creating 3 additional real Supabase Auth-backed staff accounts (`staff.id` has a hard FK to `auth.users`, so a plain SQL insert into `staff` alone isn't possible) — Priya Deshmukh (Supervisor), Ramesh Kumar (Staff), Sunita Patil (Staff), shared password `KalazaStaff@123`.

### 19. QR-Scan Evidence (Replaces Photo Evidence) + Admin Role Removed
- Photo-evidence capture is gone. Allotting or administering a dose now requires scanning the medicine's QR code with a **live camera-only viewfinder** (`ui/components/QrScanDialog.kt`, built on CameraX + on-device ML Kit Barcode Scanning) — there is no gallery/file-picker path, matching the "must be scanned in the moment" requirement. The decoded text is shown and requires an explicit Confirm tap; it's recorded as-is (not matched against the medicine name).
- `PhotoUploader.kt`, `CameraCaptureFile.kt`, and `PhotoConfirmDialog.kt` were deleted. The Supabase `Storage` plugin was removed from `SupabaseClients` (and the `storage-kt` dependency dropped) since nothing uploads files anymore.
- `MedicationEntry`'s `allotmentPhotoUrl`/`allotmentPhotoExpiresAt` and `administeredPhotoUrl`/`administeredPhotoExpiresAt` became `allotmentScannedCode`/`administeredScannedCode` (no expiry — it's just text, not a Storage object with a retention window). `MedicationEvidenceEvent` similarly replaced `photoUrl`/`expiresAt` with a single `scannedCode`. `markAdministered`/`allotMedication` now take `scannedCode: String` instead of a photo URL + expiry pair. The per-patient xlsx report's per-day administration lookup (section 13) is unaffected — it only ever read `occurredAt`/`patientId`/`medicationId` off `MedicationEvidenceEvent`, never the photo fields.
- The restricted, photo-audit-only `UserRole.ADMIN` (added in section 11) was removed entirely — `ui/photoaudit/PhotoAuditScreen.kt`, `PhotoAuditViewModel`/`PhotoAuditEntry`, `Routes.PHOTO_AUDIT`, and `SessionManager.isPhotoAdmin()` are all gone. `StaffEditor`'s role picker is unaffected (it already derived its options generically from `UserRole.entries`). Only three roles remain: `SUPER_ADMIN`, `SUPERVISOR`, `STAFF`.
- **Backend restructuring is done too**: `medications`/`medication_evidence_log` columns were migrated to the `*_scanned_code` names, the `cleanup-photos` Edge Function's `pg_cron` schedule was unscheduled and the function itself deleted, Arti's `ADMIN` staff row was removed, and the now-unused evidence Storage bucket was deleted. Postgres has no `DROP VALUE` for enum types, so the now-unused `'ADMIN'` label is left in the DB's `user_role` enum type harmlessly rather than migrated away (would need a full type-swap for zero practical benefit).

### 20. Batch-QR Scan Tab + Mandatory Dose Tags
- Every `MedicationEntry` now carries a mandatory `DoseTag` (`MORNING`/`AFTERNOON`/`EVENING`, `data/model/Entities.kt`) alongside its exact `scheduleTime` — a coarse bucket independent of the precise time, picked via `DoseTagPicker` (required, no silent default) in both Add and Edit Medication. `DoseTag.matches(time)` (Morning 12am–12pm, Afternoon 12pm–5pm, Evening 5pm–12am) is checked live in both dialogs: a time/tag combo that doesn't agree (e.g. a 9pm dose tagged Morning) shows an inline red warning and blocks Save until resolved.
- **New "Scan" tab** (`ui/scan/ScanScreen.kt`, bottom nav for Staff + Supervisor, not Super Admin): staff scan a patient's printed QR code — plain text payload `"<patient name>|<TAG>"`, e.g. `Vasant Rao Joshi|MORNING` — or use an always-available manual name+tag fallback if the camera won't cooperate. A successful match (name matched loosely: trimmed, case-insensitive, internal whitespace collapsed; tag matched exactly) fetches that patient's meds tagged for that round via `MedicationRepository.getMedicationsForPatientAndTag`. An unmatched/garbled QR hard-blocks with an error banner — the list never shows for a wrong scan.
- The matched list is **checkbox multi-select**, not one QR-gated tap per dose like the Med tab's flow: doses already given show their status badge (greyed); doses whose `scheduleTime` hasn't arrived yet (compared live against `LocalTime.now()`) are also greyed out and their checkbox disabled, un-greying automatically once the clock catches up without needing a re-scan. Selecting one or more due doses surfaces a "Confirm N Given" bar, which opens a review popup listing exactly what's about to be marked (name + dose) — its OK button is disabled for 6 seconds (`CONFIRM_HOLD_SECONDS`) so it can't be reflex-tapped past; Cancel stays live throughout. Confirming calls `ScanViewModel.markGivenBatch()`, which marks every selected dose given and refreshes the list once.
- "MAR" was renamed to "**Med**" as a patient-profile tab label (cosmetic only, `PatientProfileScreen.kt`'s tab list) — no route/behavior change.

### 21. Schema Verification & Two Latent Bugs Found + Fixed
A full introspection query (see prompt below) was run against the live Supabase project to replace assumption with a verified ground truth. It surfaced two real, live bugs, both fixed:
- **`supabase-kt`'s Postgrest client hardcodes `Json { encodeDefaults = false }`** with no per-client override (confirmed from the library's own source, `Supabase/src/commonMain/kotlin/io/github/jan/supabase/Utils.kt`) — any field left at its Kotlin default value is silently **dropped from the request entirely**, not sent as null. This bit `MedicationRow.tag` (default `"MORNING"`, and the DB column had no server-side default): creating/leaving a medication on the default tag omitted `tag` from the insert, hitting the column's `NOT NULL` constraint (`23502` crash) — and editing a tag *back* to Morning would have silently no-op'd on update instead of persisting. Fixed with `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` on `MedicationRow.tag`, which forces it into every request regardless of the encoder's own skip-defaults behavior; `MedicationRow.recurringDays` (default `""`) got the same annotation pre-emptively for the identical reason. **Any future field whose Kotlin default could legitimately be re-selected by a user (an enum-like column, a boolean, an empty-collection default) needs this same annotation** — don't assume a plain `@SerialName` field will always reach the request.
- **`medications.recurring_days` didn't exist as a column at all** — the day-of-week recurring feature (section 13/14 of prior history) shipped Kotlin code (`MedicationRow.recurringDays`, `MedicationEntry.recurringDays: Set<Int>`) against a column that was never migrated in. It was masked by the exact bug above (an empty/default selection never got sent, so nothing ever tried to write to the missing column) — the first medication saved with specific weekdays picked would have crashed with `column "recurring_days" does not exist`. Fixed: `alter table medications add column if not exists recurring_days text not null default ''`.
- Also found and fixed in passing: a stale, still-`active` `pg_cron` job (`cleanup-evidence-photos`, hourly) calling `functions/v1/cleanup-photos` — that Edge Function was deleted as part of section 19's photo→QR-scan migration, and the migration's own cron-unschedule step was apparently missed despite being marked done. Unscheduled via `select cron.unschedule('cleanup-evidence-photos')`.
- **Lesson for future sessions:** don't assume a Kotlin data class's shape matches the live DB, or that a documented migration actually fully ran — re-verify against the real schema (introspection query below) whenever something in this space looks suspicious, rather than trusting prior session notes at face value.

### 22. Scan Tab Refinements
- **Due-time gating**: a dose in the Scan tab's matched list whose `scheduleTime` hasn't arrived yet (checked live against `LocalTime.now()` on every recomposition, not pinned at scan time) greys out and its checkbox disables — can't check off a 9am dose at 8am. It un-greys on its own once the clock catches up; no re-scan needed.
- **`QrScanDialog` gained a manual-entry fallback**, shared by all four of its call sites (Medicine tab allotment, fulfilling an allotment request, Med tab mark-administered, and the Scan tab's own scan trigger) since it lives in the component itself rather than being duplicated per caller: an "enter code manually" text field is always available alongside the live camera view (and alongside the Grant Camera Access button if permission was denied), typing a value in and using it exactly as if it had been scanned. No QR flow in the app is camera-only anymore.
- **`QrScanDialog`'s `title`/`message` params are now optional** (`String? = null`) — the Scan tab's own call passes neither, so that popup shows only the camera / scanned-result content (checkmark + the raw scanned text, e.g. `Xyz|MORNING`, in the same style as before), no explanatory heading above it. The other three call sites (allotment, fulfill, administer) still pass both and keep their original explanatory copy.
- **`DoseTagPicker`'s chips got a couple extra dp of height** (`Modifier.heightIn(min = 36.dp)`) — the default Material3 `FilterChip` height was clipping the descender on "Evening" (the "g").
- **Time/tag consistency validation**: Add and Edit Medication both compute `DoseTag.matches(scheduleTime)` live and show an inline red warning + disable Save when they disagree (e.g. a 9pm dose tagged Morning) — `DoseTag.matches()` lives in `ui/components/DoseTagPicker.kt` (Morning 12am–12pm, Afternoon 12pm–5pm, Evening 5pm–12am).
- **Multi-select confirm with a deliberately slow OK**: the Scan tab's matched-dose list is checkbox-driven; selecting one or more due doses surfaces a "Confirm N Given" bar which opens `ConfirmGivenDialog` — lists every selected dose by name + dose, and its OK button is disabled for `CONFIRM_HOLD_SECONDS` (6s) so it can't be reflex-tapped past a batch without reading it; Cancel stays live the whole time. Confirming calls `ScanViewModel.markGivenBatch(ids, scannedCode)`, which marks all selected doses given and reloads the list once, not once per dose.

### 23. Medication Reminder System: Two Real Bugs Found (Reading the Edge Functions, Not Just Assuming They Worked)
Prompted by "has this actually ever been tested end-to-end, or just assumed working because a manual DB insert triggered a push once" — reading `supabase/functions/medication-watchdog/index.ts` and `send-push/index.ts` line-by-line surfaced two real, live bugs, both fixed (needs **redeploying both functions** — `supabase functions deploy medication-watchdog` / `send-push` — git having the new code isn't the same as it being live):
- **The 5-minute "missed dose" tier was notifying nobody.** It targeted `recipient_role: "ADMIN"` — the restricted, photo-audit-only role removed entirely in section 19. The insert succeeded (Postgres never dropped the unused `'ADMIN'` enum label), so it looked fine from the `notifications` table alone, but no staff row has had that role in a long time: `getForRecipient` filters by the logged-in user's real role (no one is ever `ADMIN`), and `send-push`'s `staff` lookup for that role returns zero rows, so it silently no-ops (`{sent: 0}`, no error). Retargeted to `SUPERVISOR`. The 15-min-before reminder (Staff+Supervisor) and 10-min escalation (Super Admin) were never affected — those roles are real and staffed.
- **The watchdog ignored `recurring_days` entirely**, treating every recurring dose as due every single day regardless of a weekday restriction — harmless only because the column didn't exist until section 21's fix, so nobody had actually used the feature yet. Now checked (`isoDayOfWeek` + `parseRecurringDays` in the Edge Function), matching the Kotlin app's own `isDueOn` display logic.
- **Push reliability for a fully-closed app**: `send-push`'s FCM v1 payload had no `android.priority`, which defaults to `NORMAL` — Doze/App Standby can batch-delay or drop normal-priority data messages by minutes to hours once the app is killed, exactly the state this needs to survive. Added `android: { priority: "high" }`.
- **Still genuinely untested end-to-end** as of this writing — worth actually scheduling a dose a few minutes out, killing the app fully, and confirming all three tiers arrive, rather than trusting this note alone.

### 24. Staff Deletion Leaves the Auth Account Orphaned (Known, Deferred)
`StaffRepository.deleteStaff` (both the app's normal "Delete Staff" action and any manual `delete from staff` in SQL) only removes the `staff` table row — the linked Supabase Auth account is never deleted, because that needs the Auth Admin API (service-role key), which the client SDK doesn't hold (it can only act on the *currently signed-in* user, not an arbitrary other one). Practically harmless day-to-day (no `staff` row means `login()` can never resolve that account again), but the email/identity stays permanently taken — trying to re-add a staff member with the same auth email later will conflict. Workaround for now: run `delete from auth.users where id = '<id>'` directly in the Supabase SQL Editor (which has full DB privileges, unlike the app), Supabase's internal auth tables cascade off that. **Deferred, explicit "do it later":** wire this up properly via a dedicated Edge Function (service-role key) so staff deletion cleans up Auth automatically instead of needing this manual SQL step every time.

---

## Verified Database Schema (Supabase, `public` schema)

Ground truth as of the section 21 audit — re-run the query below and re-verify before trusting this if it's been a while, rather than assuming it's still accurate.

**Introspection query** (single query, returns one pretty-printed JSON column covering tables/columns/types/nullability/defaults, primary keys, foreign keys, check constraints, RLS enabled-state + policies, indexes, and `pg_cron` jobs):
```sql
select jsonb_pretty(
  jsonb_build_object(
    'tables', (
      select jsonb_agg(jsonb_build_object(
        'table', c.table_name,
        'columns', (
          select jsonb_agg(jsonb_build_object(
            'column', col.column_name, 'type', col.data_type,
            'nullable', col.is_nullable, 'default', col.column_default
          ) order by col.ordinal_position)
          from information_schema.columns col
          where col.table_schema = 'public' and col.table_name = c.table_name
        )
      ) order by c.table_name)
      from (select distinct table_name from information_schema.tables
            where table_schema = 'public' and table_type = 'BASE TABLE') c
    ),
    'primary_keys', (
      select jsonb_agg(jsonb_build_object('table', tc.table_name, 'column', kcu.column_name))
      from information_schema.table_constraints tc
      join information_schema.key_column_usage kcu
        on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
      where tc.constraint_type = 'PRIMARY KEY' and tc.table_schema = 'public'
    ),
    'foreign_keys', (
      select jsonb_agg(jsonb_build_object(
        'table', tc.table_name, 'column', kcu.column_name,
        'references_table', ccu.table_name, 'references_column', ccu.column_name
      ))
      from information_schema.table_constraints tc
      join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
      join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
      where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public'
    ),
    'check_constraints', (
      select jsonb_agg(jsonb_build_object(
        'table', conrelid::regclass::text, 'name', conname, 'definition', pg_get_constraintdef(oid)
      ))
      from pg_constraint where contype = 'c' and connamespace = 'public'::regnamespace
    ),
    'rls_enabled', (
      select jsonb_agg(jsonb_build_object('table', relname, 'rls_enabled', relrowsecurity))
      from pg_class where relnamespace = 'public'::regnamespace and relkind = 'r'
    ),
    'rls_policies', (
      select jsonb_agg(jsonb_build_object(
        'table', tablename, 'policy', policyname, 'cmd', cmd, 'roles', roles,
        'using', qual, 'with_check', with_check
      ))
      from pg_policies where schemaname = 'public'
    ),
    'indexes', (
      select jsonb_agg(jsonb_build_object('table', tablename, 'index', indexname, 'def', indexdef))
      from pg_indexes where schemaname = 'public'
    ),
    'cron_jobs', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'name', jobname, 'schedule', schedule, 'command', command, 'active', active
      )), '[]'::jsonb)
      from cron.job
    )
  )
) as full_schema_report;
```
(If `cron.job` errors because `pg_cron` isn't enabled on a given project, drop the `cron_jobs` key and rerun.)

### Tables (13), by name — columns are `column: type` — `NN` = NOT NULL, `= x` = DB-level default. `id` on every table is `uuid`, PK, default `gen_random_uuid()` unless noted.

- **`patients`**: `name: text NN =''`, `age: int NN =0`, `gender: gender_type(enum) NN ='MALE'`, `room_no: text NN =''`, `medical_history/current_issues/allergies/emergency_contact/emergency_phone/primary_diagnosis: text NN =''`, `admission_date: date NN =CURRENT_DATE`, `is_archived: bool NN =false`.
- **`staff`**: `id: uuid` (**no default** — must equal the Supabase Auth user id, assigned by the app at signup, not DB-generated), `name: text NN`, `name_lower: text` (nullable, unique — app never writes this directly; a DB trigger/generated column presumably maintains it for case-insensitive login lookups), `email: text NN =''`, `role: user_role(enum) NN ='STAFF'`, `phone: text NN =''`, `is_active: bool NN =true`, `joined_date: date NN =CURRENT_DATE`, `auth_email: text NN` (no default), `fcm_token: text NN =''`.
- **`medications`**: `patient_id: uuid NN FK→patients`, `medicine_name/dose/quantity: text NN =''`, `schedule_time: time NN =CURRENT_TIME`, `scheduled_date: date NN =CURRENT_DATE`, `status: med_status(enum) NN ='PENDING'`, `administered_by: text NN =''`, `administered_at: timestamptz` (nullable), `notes: text NN =''`, `allotment_status: allotment_status(enum) NN ='NOT_ALLOTTED'`, `allotted_by_id: uuid FK→staff` (nullable), `allotted_by_name: text NN =''`, `allotted_at: timestamptz` (nullable), `allotment_scanned_code/administered_scanned_code: text NN =''`, `is_recurring: bool NN =true`, `reminder_sent_at/admin_alert_sent_at/superadmin_alert_sent_at: timestamptz` (nullable, watchdog-only — no Kotlin field references these), `tag: text NN ='MORNING'` (CHECK constrained to MORNING/AFTERNOON/EVENING — see section 20/21), `recurring_days: text NN =''` (see section 21 — added late, was missing for a while).
- **`vitals`**: `patient_id: uuid NN FK→patients`, `date: date NN =CURRENT_DATE`, `time: time NN =CURRENT_TIME`, `pulse/bp/spo2/temperature/sugar_fasting/sugar_pp/signed_by: text NN =''`, `created_at: timestamptz NN =now()`.
- **`utility_records`**: `patient_id: uuid NN FK→patients`, `date: date NN =CURRENT_DATE`, `time: time NN =CURRENT_TIME`, `quantities: jsonb NN ='{}'`, `issued_to_caregiver/issued_by_supervisor/checked_by: text NN =''`, `created_at: timestamptz NN =now()`.
- **`utility_items`**: `name: text NN =''`, `unit: text NN ='pcs'`, `display_order: int NN =0`, `is_active: bool NN =true`.
- **`doctor_visits`**: `patient_id: uuid NN FK→patients`, `doctor_name/specialty/notes/prescription_changes: text NN =''`, `date: date NN =CURRENT_DATE`, `time: time NN =CURRENT_TIME`, `next_visit_date: date` (nullable), `is_confirmed/is_archived: bool NN =false`.
- **`care_notes`**: `patient_id: uuid NN FK→patients`, `staff_id: uuid FK→staff` (nullable), `staff_name: text NN =''`, `timestamp: timestamptz NN =now()`, `note: text NN =''`.
- **`approval_requests`**: `entity_type: approval_entity_type(enum) NN` (no default), `entity_id: text NN =''`, `action: approval_action(enum) NN ='EDIT'`, `patient_id: uuid FK→patients` (nullable), `patient_name/requested_by_name/field_changed/old_value/new_value/reviewed_by_name/rejection_reason: text NN =''`, `requested_by_id/reviewed_by_id: uuid FK→staff` (nullable), `status: approval_status(enum) NN ='PENDING'`, `timestamp: timestamptz NN =now()`, `reviewed_at: timestamptz` (nullable).
- **`allotment_requests`**: `medication_entry_id: uuid NN FK→medications`, `patient_id: uuid NN FK→patients`, `patient_name/medicine_name/dose/requested_by_name/fulfilled_by_name: text NN =''`, `scheduled_time: time NN =CURRENT_TIME`, `requested_by_id/fulfilled_by_id: uuid FK→staff` (nullable), `status: allotment_request_status(enum) NN ='PENDING'`, `timestamp: timestamptz NN =now()`, `fulfilled_at: timestamptz` (nullable).
- **`audit_log`**: `action/target_patient_id/target_patient_name/details: text NN =''`, `performed_by_id: uuid FK→staff` (nullable), `performed_by_name: text NN =''`, `timestamp: timestamptz NN =now()`, `icon_name: text NN ='edit'`.
- **`notifications`**: `recipient_staff_id: uuid FK→staff` (nullable), `recipient_role: user_role(enum)` (nullable), `type: notification_type(enum) NN` (no default), `title/message/target_route: text NN =''`, `timestamp: timestamptz NN =now()`, `is_read: bool NN =false`.
- **`medication_evidence_log`**: `medication_id: uuid NN FK→medications`, `patient_id: uuid NN FK→patients`, `medicine_name: text NN =''`, `kind: text NN` (CHECK: `ALLOTMENT`/`ADMINISTRATION`), `staff_id: uuid FK→staff` (nullable), `staff_name/scanned_code: text NN =''`, `occurred_at: timestamptz NN =now()`.

All `USER-DEFINED` columns above are real Postgres enum types (`gender_type`, `user_role`, `med_status`, `allotment_status`, `approval_status`, `approval_action`, `approval_entity_type`, `allotment_request_status`, `notification_type`) — Kotlin sends/receives them as plain strings via `.name`/`.valueOf()`, which PostgREST coerces against the enum label, so a mismatched string (typo, or a Kotlin enum entry with no matching Postgres label) fails loudly rather than silently — good, but means adding a new Kotlin enum entry always needs a matching `ALTER TYPE ... ADD VALUE` migration too.

**RLS**: enabled on all 13 tables. Broad pattern: `is_active_staff()` gates most SELECT/INSERT, `is_super_admin()` gates DELETE everywhere it's allowed at all and most sensitive UPDATEs (`patients`, `approval_requests`), `is_supervisor_or_above()` gates allotment-request fulfillment. `medications_insert`/`medications_delete` are Super-Admin-only (matches the client-side `SessionManager.isAdmin()` gate on Add/Edit/Delete Medication); `medications_update` allows `is_super_admin() OR is_active_staff()` (covers regular staff/supervisor marking doses given or allotted). `notifications_insert` is checked per notification `type` (see section 17).

**`pg_cron` jobs**: `medication-watchdog` (every minute, active) — the only one that should exist post section-21 cleanup; `cleanup-evidence-photos` was found still active and was removed (section 21).

---

## Security Hardening Backlog (deferred until UI/functionality reach prod level — do NOT start early per explicit instruction)

- **No inactivity/auto-logout timer.** Session persists on-device indefinitely once logged in; no timeout, no re-auth prompt. Matters for a shared/facility device left unlocked and unattended.
- **`android:allowBackup="true"` with no `dataExtractionRules`/`fullBackupContent` exclusions**, combined with the Supabase Auth plugin's default (likely unencrypted `SharedPreferences`-backed) session storage — auth tokens could plausibly ride along in an Android cloud backup / device-to-device transfer. Likely fix: `allowBackup="false"`, optionally pair with an encrypted session-storage backend for the Auth plugin.
- **No client-side password policy** when Super Admin creates a staff account (`addStaff()` passes whatever's typed straight to Supabase Auth) — whatever protection exists is whatever the Supabase project's own Auth password policy is set to (unverified from this repo).
- **No `FLAG_SECURE`** on patient-data screens — screenshots/screen recording of patient medical/personal info aren't currently blocked by the OS. Optional, lower priority.
- **No release signing config** (`app/build.gradle.kts` has no `signingConfigs` block) — a properly signed, distributable release APK/AAB can't be produced yet.
- **`isMinifyEnabled = false`** on the release build type — no code shrinking/obfuscation.
- **No automated tests** exist (unit or instrumentation) — every future change currently has zero automated safety net.
- **`google-services.json` is tracked in git history** despite being gitignored now (committed before the ignore rule existed) — contains a Firebase Android API key GitHub's Secret Scanning flags (low real risk, Android Firebase keys aren't meant to be secret, but worth a `git rm --cached` cleanup pass eventually).
- **GitHub PAT pasted in chat during this project's development** needs revoking once active development against this repo is done.
- **Staff deletion doesn't clean up the Supabase Auth account** — see section 24. Deferred; needs a service-role-key Edge Function to do it properly instead of the current manual SQL workaround.
- **Excel/Summary-sheet format changes** — requested, parked; no format spec given yet.
- **Off-network (no-Wi-Fi) staff/Super-Admin login during hospital visits** — leaning toward Super-Admin-issued temporary access codes; not started.

---

## What is Remaining (Future Scope)

Backend integration, real authentication, push notification delivery, and real-time sync (all previously listed here as open) are now **done** — see Technology Stack and section 1 above, and "Push Notification System" and "Medication Deadline Reminders & Escalation" below. What's actually left:

### 1. Per-Day Medication Administration History (Medium Priority)
- A recurring dose's live PENDING/OVERDUE/ADMINISTERED status now correctly resets each day (see `MedicationRepository.withComputedStatus`), and every allotment/administration QR-scan event is separately preserved forever in `medication_evidence_log`. What's still a flat, single-row model is the *live* `medications` row itself — there's no per-day history table for it beyond the evidence log, so questions like "show me every day this dose was given over the last month" aren't answerable from the `medications` table alone (only from the evidence log, and only for doses that were scanned).
- **Action Required:** decide if a dedicated daily-administration-log table (mirroring `medication_evidence_log` but for every administration, not just scanned ones) is worth adding.

### 2. Test Account Coverage (Low Priority)
- Seed data currently only has a Super Admin (Somnath) account. There's no seeded `STAFF` or `SUPERVISOR` login, so the restricted-permission paths (approval requests, allotment requests, RLS column-restriction triggers) haven't been exercised end-to-end with a non-admin account yet. Explicitly deferred by the team — "testing will be done later."

### 3. Offline Support / Caching (Low Priority)
- Not implemented. A Room-based local cache was attempted and reverted (it required a Gradle 8→9 and AGP 8→9 jump just to get its annotation processor working, which was far more disruptive than the feature warranted) — worth revisiting on its own, in isolation, with a properly pinned KSP/Room version first.

### 4. Security Hardening & Wi-Fi-Scoped Auth (Next Up)
- Explicitly called out by the team as the remaining phase before this is considered feature-complete: a further security pass, plus restricting login/access to the facility's own Wi-Fi network.

---

## Detailed Workflows

### Staff Editing a Patient Workflow
1. Staff logs in and navigates to the Patient Profile -> Details Tab.
2. Staff modifies a field (e.g., Room Number) and clicks "Save".
3. `PatientViewModel` detects the role is Staff. Instead of updating the repository, it generates an `ApprovalRequest` containing the `oldValue` and `newValue`.
4. A Toast confirms to the Staff that the request was submitted.
5. The Admin receives a pending approval in their Approval Queue.

### Admin Approving a Request Workflow
1. Admin opens the Approval Queue.
2. Clicks on the pending request for the Room Number change.
3. Clicks "Approve".
4. `ApprovalRepository` marks the request as APPROVED and logs the reviewer's id/name.
5. `ApprovalViewModel` applies the new Room Number directly to the actual Patient record via `PatientRepository.updatePatient`.
6. An `AuditLogEntry` ("Edit Request Approved") is generated. Rejecting a request similarly logs an "Edit Request Rejected" entry, without touching the Patient record.

### Admin Archiving a Patient Workflow
1. Admin opens a patient's profile and taps the overflow menu (⋮) next to Edit.
2. Taps "Archive Patient" and confirms in the dialog.
3. `PatientRepository.archivePatient` flags the patient as archived; an `AuditLogEntry` ("Patient Archived") is generated, and the app navigates back to the Dashboard.
4. The patient no longer appears in the default Dashboard list or search results. Toggling "Show Archived" (Admin-only) reveals them again, tagged with an "Archived" badge.

### Staff Management Workflow
1. Admin navigates to the Config tab.
2. To remove a staff member temporarily: Clicks "Revoke". The staff member is flagged as inactive and cannot log in.
3. To restore a staff member: Admin clicks "Activate" on the revoked card.
4. To remove permanently: Admin clicks "Delete", destroying the record.
5. The Admin's own card omits the "Revoke" button to prevent locking themselves out.
6. When adding a staff member, Admin picks between the two operational roles — Regular Staff or Supervisor. (Super Admin accounts aren't created through this dialog.)

### Utility Item Workflow
1. Admin adds/removes item types in Config → Utility Items (e.g. "Syringes").
2. Any patient's Utility tab immediately reflects the change: the "Add Utility Record" dialog renders one quantity field per active item, and the table gains/loses that column — no other code path needs updating.
3. A `UtilityRecord` stores quantities as a `Map<UtilityItem.id, Int>` rather than fixed fields, which is what makes this possible.

### Notification Workflow
1. A real event happens (staff submits an edit request, Admin approves/rejects one, a regular staff flags a forgotten allotment, Supervisor fulfills that flag).
2. The relevant ViewModel calls `NotificationRepository.add(...)` with either a specific `recipientStaffId` or a broadcast `recipientRole`.
3. Whoever's affected sees the bell badge update (Dashboard and Medicine tab both show it) and can open the Notifications screen.
4. Tapping a notification marks it read and navigates to its `targetRoute` (a static route like "approval"/"medicine", or "patient/{id}").

### Medication Allotment Workflow (Supervisor)
1. Supervisor opens the Medicine tab, showing every dose across all patients still awaiting allotment today, sorted by scheduled time.
2. Supervisor taps "Allot" on a dose, scans the medicine's QR code as evidence, and confirms.
3. `MedicationRepository.allotMedication` records who allotted it, when, and the scanned code, and an Audit Log entry ("Medication Allotted") is created.
4. Whoever ultimately administers the dose (Regular or Supervisor) marks it "Given" from the patient's MAR tab, which also requires scanning the QR code, independently of the allotment checkpoint.
5. If Supervisor forgets to allot a dose ahead of time, any staff member can tap "Request Allotment" on that dose in the MAR tab. This creates an `AllotmentRequest` that surfaces at the top of the Medicine tab (standing in for a push notification) until a Supervisor fulfills it.
