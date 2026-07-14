# Kalaza Care - Project State & Workflows

## Overview
Kalaza Care is an Android application designed for a clinic/hospital environment to manage patients, staff, medication (MAR), vitals, care notes, and doctor visits. The app incorporates a role-based access control system featuring Admins and Staff, with an intricate approval queue for staff-made edits.

## Technology Stack
- **Platform:** Android (Min SDK 24, Target SDK 34)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel) with StateFlow
- **Data Source:** In-memory Mock Data (Backend ready for future Firebase/SQL integration)

---

## What is Done (Completed Features)

### 1. Role-Based Access & Authentication (UI & Logic)
- **Admin Role:** Has full access. Can view the Summary Tab, add new patients directly, add/revoke/delete staff members, and approve/reject staff edit requests.
- **Staff Role:** Has limited access. Cannot view the Summary Tab. Any edits to patient data generate an Approval Request instead of saving directly.

### 2. Navigation & UI Shell
- **Bottom Navigation Bar:** Context-aware based on the logged-in user. (Admin sees: Patients, Summary, Approval Queue, Audit Log, Config. Staff sees: Patients).
- **Top App Bar:** Customized to display the brand's red stripe (`KalazaRed`), the app logo, dynamic screen titles, and a clickable Notification Bell.

### 3. Patient Management
- **Dashboard:** Displays quick stats (total patients, pending meds, pending approvals) and a searchable list of patient cards.
- **Patient Profile:**
  - **Details Tab:** View/Edit patient demographics and medical history. Staff edits go to the Approval Queue. Admin edits save immediately and log to Audit.
  - **Vitals Tab:** Record and view daily vitals (BP, Heart Rate, Temp, SpO2).
  - **MAR (Medication Administration Record) Tab:** Track scheduled medications. Staff can mark them as administered. Overdue/Pending states are color-coded.
  - **Utility Tab:** Log usage of medical utilities (e.g., Oxygen, IV fluids, Catheters).
  - **Doctor Visits Tab:** Log specific instructions and notes left by visiting doctors.
  - **Care Notes Tab:** Add general nursing/care notes for the patient.

### 4. Admin Workflows
- **Approval Queue:** A dedicated screen where Admins can review, approve, or reject field-level changes requested by Staff.
- **Audit Logs:** A read-only chronological log of all major actions (Patient Added, Patient Edited, Approvals, Rejections).
- **Configuration / Staff Management:** Admins can add new staff, revoke existing staff, activate revoked staff, or delete staff entirely. Admins cannot revoke themselves.

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
- The Notification Bell in the top bar currently shows a Toast.
- **Action Required:** Integrate Firebase Cloud Messaging (FCM) to push real-time alerts to Admins when a new Approval Request is submitted, or when a medication is overdue.

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
4. `ApprovalRepository` marks the request as APPROVED and logs the reviewer's name. (Note: In a full backend system, this step would also apply the new Room Number to the actual Patient record).
5. An `AuditLogEntry` is generated.

### Staff Management Workflow
1. Admin navigates to the Config tab.
2. To remove a staff member temporarily: Clicks "Revoke". The staff member is flagged as inactive and cannot log in.
3. To restore a staff member: Admin clicks "Activate" on the revoked card.
4. To remove permanently: Admin clicks "Delete", destroying the record.
5. The Admin's own card omits the "Revoke" button to prevent locking themselves out.
