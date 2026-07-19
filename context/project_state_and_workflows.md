# Kalaza Care - Project State & Workflows

## Overview
Kalaza Care is an Android application designed for a clinic/hospital environment to manage patients, staff, medication (MAR), vitals, care notes, and doctor visits. The app incorporates a role-based access control system featuring Admins, regular Staff, and Supervisor, with an intricate approval queue for staff-made edits and a two-checkpoint (allot → administer) medication workflow.

## Technology Stack
- **Platform:** Android (Min SDK 24, Target SDK 34)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel) with StateFlow
- **Data Source:** In-memory Mock Data (Backend ready for future Firebase/SQL integration)

---

## What is Done (Completed Features)

### 1. Role-Based Access & Authentication (UI & Logic)
- **Admin Role:** Has full access. Can view the Summary Tab, add new patients directly, add/revoke/delete staff members (choosing between the two operational roles), and approve/reject staff edit requests.
- **Staff Role (Regular):** Has limited access. Cannot view the Summary Tab. Any edits to patient data generate an Approval Request instead of saving directly.
- **Staff Role (Medicine):** Same dashboard and permissions as Regular Staff, plus an additional **Medicine** tab for allotting doses ahead of administration (see workflow below).
- **Login is by Name, not Email.** The login screen asks for the staff member's Name; `AuthRepository.login` matches `Staff.name` case-insensitively. `Staff.email` still exists as a separate contact-info field (shown in Config), it's just no longer the login credential.

### 2. Navigation & UI Shell
- **Bottom Navigation Bar:** Context-aware based on the logged-in user. (Admin sees: Patients, Approvals, Audit Log, Config, Summary. Supervisor sees: Patients, Medicine. Regular Staff sees: Patients).
- **Top App Bar:** Customized to display the brand's red stripe (`KalazaRed`), the app logo, dynamic screen titles, and a clickable Notification Bell.

### 3. Patient Management
- **Dashboard:** Displays quick stats (total patients, pending meds, pending approvals) and a searchable list of patient cards. Reloads on resume so returning from another tab shows current data.
- **Patient Profile:**
  - **Details Tab:** View/Edit patient demographics and medical history. Staff edits go to the Approval Queue. Admin edits save immediately and log to Audit.
  - **Vitals Tab:** Record and view daily vitals (BP, Heart Rate, Temp, SpO2).
  - **MAR (Medication Administration Record) Tab:** Track scheduled medications. Overdue status is computed live from the scheduled time. Marking a dose "given" requires photo evidence, and shows whether the dose has been allotted yet; any staff can flag a "Request Allotment" if supervisor forgot.
  - **Utility Tab:** Log usage of medical utilities. Columns/fields are generated dynamically from whatever's configured in Config → Utility Items — adding a new item type there shows up here immediately, no code change needed.
  - **Doctor Visits Tab:** Log specific instructions and notes left by visiting doctors.
  - **Care Notes Tab:** Add general nursing/care notes for the patient.
- **Medicine Tab (Supervisor only):** A facility-wide "rounds" view of every dose still awaiting allotment today, plus any pending allotment requests raised by regular staff. Allotting a dose requires photo evidence.

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

### 6. In-App Notification System
- A real Notifications screen (bell icon → badge count → list), reachable from Dashboard and the Medicine tab.
- Notifications are generated at the actual point of the event, not just seeded: a staff edit request notifies all Admins; an approval/rejection notifies the requester; a supervisor allotment request notifies all Supervisors; fulfilling one notifies the requester back. Tapping a notification marks it read and navigates to the relevant screen (Approval Queue, Medicine tab, or the specific patient's profile).
- This is in-app only — no real push yet (see Future Scope).

### 7. Input Validation
- Phone numbers (staff phone, patient emergency phone) only accept digits as typed and require exactly 10 before the form can submit.
- Patient age must be between 1 and 120.
- Staff email is validated against a standard email pattern before Admin can add them.
- Vitals fields (pulse, BP, SpO2, sugar) only accept digits; temperature accepts digits and a decimal point. All vitals fields are also range-checked (e.g. pulse 30–220, SpO2 0–100, temperature 90–110°F) with inline error text — an out-of-range value blocks Save.
- Utility quantities only accept digits.
- Staff names are trimmed before being stored, and login matching trims and case-folds the name, so trailing/leading whitespace never blocks a valid login.
- Medications can still be scheduled before a patient's admission date (there are legitimate backdating reasons), but doing so now shows a warning Toast instead of silently accepting it.

### 8. Doctor Visit Editing & Generalized Approval
- Doctor visits are now editable by any role. Admin edits apply directly (+ Audit Log entry); Staff/Supervisor edits generate field-level `ApprovalRequest`s just like Patient edits, routed to the same Approval Queue.
- `ApprovalRequest` now carries an `entityType` (`PATIENT` or `DOCTOR_VISIT`) and `entityId` so `ApprovalViewModel.approve()` knows which repository to apply the diff to. The Approval Queue UI is unchanged — it already only cares about patient name / field / old-new value, which both entity types populate.
- **Not yet covered by approval-gating:** Care Notes, Vitals, and Utility records still have no edit UI at all (add-only), so item 18 ("anything added should be editable with due approval") was scoped to Doctor Visits since that was explicitly called out as broken; extending edit+approval to those three would be a reasonable follow-up but is a larger net-new feature, not a bug fix.

### 9. Time Input
- All medication scheduling (Add Medication, Edit Medication) now uses a 12-hour HH:MM + AM/PM picker (`TimeOfDayField`) instead of raw 24-hour text fields, while still storing/computing everything internally as 24-hour `LocalTime`.

### 10. Patient Profile Robustness
- Editing a patient whose data hasn't finished loading (e.g. a stale/bad deep link) no longer risks a `NullPointerException` — Save is disabled and blocked with a "still loading" message until the patient record is actually present.
- A patient profile for a non-existent ID now shows a "Patient not found" state with a Go Back button, instead of spinning forever indistinguishably from a real loading state.
- `MedicationEntry`'s live-computed OVERDUE/PENDING status is now reversible in both directions — editing a dose's time to a later slot un-overdues it instead of leaving it stuck OVERDUE forever (previously only PENDING→OVERDUE was recomputed).

---

## What is Remaining (Future Scope)

### 1. Backend Integration (High Priority)
- The app currently relies on `MockData.kt` and in-memory lists within `Repositories.kt`.
- **Action Required:** Implement Firebase Firestore (or a REST API with SQL) for real persistent data storage.

### 2. Real Authentication (High Priority)
- The mock login accepts any password for active emails.
- **Action Required:** Integrate Firebase Auth (or JWT-based custom auth) to securely authenticate Staff and Admins.

### 3. Push Notifications (Medium Priority)
- The in-app Notification system (bell → screen) is fully built and generates real notifications at real trigger points (see "In-App Notification System" above), but they only appear when the app is open — there's no actual push delivery when the app is backgrounded or closed, and no "before-schedule allotment reminder" timer yet (that would need a scheduled job).
- **Action Required:** Integrate Firebase Cloud Messaging (FCM) so `NotificationRepository.add(...)` also triggers a real push, and add a scheduled job for the pre-deadline allotment reminder.

### 3a. Photo Evidence Upload (Medium Priority)
- Allotment and administration checkpoints currently "capture" a photo via `PhotoCapture.kt`, which just mints a mock URL and a 48h expiry timestamp — no camera or upload actually happens.
- **Action Required:** Wire an actual camera/gallery picker and backend storage (e.g. Firebase Storage), plus a scheduled job to delete evidence photos after 48h as required by policy.

### 4. Data Validation & Error Handling (Medium Priority)
- Phone (10 digits), age (1-120), and email format are now validated client-side (see "Input Validation" above). Still to add: server-side re-validation once a backend exists, and validation for any medical ID fields introduced later.

### 5. Offline Support / Caching (Low Priority)
- Implement Room Database to cache patient data locally so the app remains partially usable during network outages.

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
6. When adding a staff member, Admin picks between the two operational roles — Regular Staff or Supervisor. (Admin accounts aren't created through this dialog.)

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
2. Supervisor taps "Allot" on a dose, takes a photo as evidence, and confirms.
3. `MedicationRepository.allotMedication` records who allotted it, when, and the photo evidence (mock URL + 48h expiry), and an Audit Log entry ("Medication Allotted") is created.
4. Whoever ultimately administers the dose (Regular or Supervisor) marks it "Given" from the patient's MAR tab, which also requires a photo, independently of the allotment checkpoint.
5. If Supervisor forgets to allot a dose ahead of time, any staff member can tap "Request Allotment" on that dose in the MAR tab. This creates an `AllotmentRequest` that surfaces at the top of the Medicine tab (standing in for a push notification) until a Supervisor fulfills it.
