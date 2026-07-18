# Kalaza Care - Project State & Workflows

## Overview
Kalaza Care is an Android application designed for a clinic/hospital environment to manage patients, staff, medication (MAR), vitals, care notes, and doctor visits. The app incorporates a role-based access control system featuring Admins, regular Staff, and Medicine Staff, with an intricate approval queue for staff-made edits and a two-checkpoint (allot → administer) medication workflow.

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

### 2. Navigation & UI Shell
- **Bottom Navigation Bar:** Context-aware based on the logged-in user. (Admin sees: Patients, Approvals, Audit Log, Config, Summary. Medicine Staff sees: Patients, Medicine. Regular Staff sees: Patients).
- **Top App Bar:** Customized to display the brand's red stripe (`KalazaRed`), the app logo, dynamic screen titles, and a clickable Notification Bell.

### 3. Patient Management
- **Dashboard:** Displays quick stats (total patients, pending meds, pending approvals) and a searchable list of patient cards. Reloads on resume so returning from another tab shows current data.
- **Patient Profile:**
  - **Details Tab:** View/Edit patient demographics and medical history. Staff edits go to the Approval Queue. Admin edits save immediately and log to Audit.
  - **Vitals Tab:** Record and view daily vitals (BP, Heart Rate, Temp, SpO2).
  - **MAR (Medication Administration Record) Tab:** Track scheduled medications. Overdue status is computed live from the scheduled time. Marking a dose "given" requires photo evidence, and shows whether the dose has been allotted yet; any staff can flag a "Request Allotment" if medicine-staff forgot.
  - **Utility Tab:** Log usage of medical utilities (e.g., Oxygen, IV fluids, Catheters).
  - **Doctor Visits Tab:** Log specific instructions and notes left by visiting doctors.
  - **Care Notes Tab:** Add general nursing/care notes for the patient.
- **Medicine Tab (Medicine Staff only):** A facility-wide "rounds" view of every dose still awaiting allotment today, plus any pending allotment requests raised by regular staff. Allotting a dose requires photo evidence.

### 4. Admin Workflows
- **Approval Queue:** A dedicated screen where Admins can review, approve, or reject field-level changes requested by Staff. Approving applies the change directly to the Patient record (not just the request's status) and logs to Audit; rejecting also logs to Audit.
- **Audit Logs:** A read-only chronological log of all major actions (Patient Added, Patient Edited, Approvals, Rejections, Medication Allotted, Patient Archived), each with the correct icon for its action type.
- **Configuration / Staff Management:** Admins can add new staff (Regular or Medicine role), revoke existing staff, activate revoked staff, or delete staff entirely. Admins cannot revoke themselves.
- **Archive Patient:** From a patient's profile (overflow menu, Admin-only), Admin can archive a patient's record after confirming. Archived patients are hidden from the main Dashboard list by default; a "Show Archived" toggle on the Dashboard reveals them (marked with an "Archived" badge).

### 5. UI Polish & Theming
- Strict adherence to the `KalazaRed` and `KalazaDarkMaroon` color palette.
- Pixel-perfect empty states (e.g., "No Patients Found", "No Audit Logs").
- Elegant tab navigation within the Patient Profile.

---

## What is Remaining (Future Scope)

### 1. Backend Integration (High Priority)
- The app currently relies on `MockData.kt` and in-memory lists within `Repositories.kt`.
- **Action Required:** Implement Firebase Firestore (or a REST API with SQL) for real persistent data storage.

### 2. Real Authentication (High Priority)
- The mock login accepts any password for active emails.
- **Action Required:** Integrate Firebase Auth (or JWT-based custom auth) to securely authenticate Staff and Admins.

### 3. Push Notifications (Medium Priority)
- The Notification Bell in the top bar currently shows a Toast. Similarly, the "before-schedule allotment reminder" and "medicine-staff forgot" alerts are stood in for by an in-app pending-request list on the Medicine tab, not a real push.
- **Action Required:** Integrate Firebase Cloud Messaging (FCM) to push real-time alerts to Admins when a new Approval Request is submitted, to Medicine Staff ahead of a dose's scheduled time, and when a regular staff member flags a forgotten allotment.

### 3a. Photo Evidence Upload (Medium Priority)
- Allotment and administration checkpoints currently "capture" a photo via `PhotoCapture.kt`, which just mints a mock URL and a 48h expiry timestamp — no camera or upload actually happens.
- **Action Required:** Wire an actual camera/gallery picker and backend storage (e.g. Firebase Storage), plus a scheduled job to delete evidence photos after 48h as required by policy.

### 4. Data Validation & Error Handling (Medium Priority)
- While basic validation exists, stricter regex for phone numbers, ages, and medical IDs should be enforced before data is sent to the backend.

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
6. When adding a staff member, Admin picks between the two operational roles — Regular Staff or Medicine Staff. (Admin accounts aren't created through this dialog.)

### Medication Allotment Workflow (Medicine Staff)
1. Medicine Staff opens the Medicine tab, showing every dose across all patients still awaiting allotment today, sorted by scheduled time.
2. Medicine Staff taps "Allot" on a dose, takes a photo as evidence, and confirms.
3. `MedicationRepository.allotMedication` records who allotted it, when, and the photo evidence (mock URL + 48h expiry), and an Audit Log entry ("Medication Allotted") is created.
4. Whoever ultimately administers the dose (Regular or Medicine Staff) marks it "Given" from the patient's MAR tab, which also requires a photo, independently of the allotment checkpoint.
5. If Medicine Staff forgets to allot a dose ahead of time, any staff member can tap "Request Allotment" on that dose in the MAR tab. This creates an `AllotmentRequest` that surfaces at the top of the Medicine tab (standing in for a push notification) until a Medicine Staff member fulfills it.
