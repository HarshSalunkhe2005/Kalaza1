# Kalaza Care - App Requirements & Context

This file contains the foundational requirements gathered for the Kalaza Care senior assisted living application.

## 1. Core Goal
Build a mobile application (Android, built with Jetpack Compose) to manage the day-to-day operations of the Kalaza Care NGO facility in Pune.

## 2. Role-Based Requirements

### Staff Requirements
- **Patient Dashboard**: View a list of assigned patients and their basic information.
- **Vitals & Medication Logging**: Record daily vitals (BP, sugar, pulse) and check off the Medication Administration Record (MAR).
- **Daily Care Notes**: Add qualitative notes about a patient's day, mood, or health.
- **Doctor Visits**: Schedule and update notes from visiting doctors.
- **Record Edits**: Request changes to patient records (requires Admin approval).

### Admin Requirements
- **Staff Management**: Create, view, and revoke staff accounts.
- **Approval Workflow**: Review (approve/reject) edits to patient records requested by staff.
- **Patient Management**: Full CRUD operations for the entire patient registry.
- **Audit Logs**: View an immutable timeline of all actions taken by staff (meds given, edits made, etc.).
- **Monitoring**: Oversee medication reminders and system-wide utilities.

### Patient Profile Data
- Personal details (Demographics, emergency contacts)
- Medical history & primary diagnosis
- Current health issues & allergies
- Vitals timeline
- Medication schedule (MAR)
- Doctor visit history
- Utility checklists (Face masks, diapers, gloves, etc.)
- Archival capabilities

## 3. Design Aesthetics
- **Core Colors**: White background with red accents (`#9B000A`).
- **Typography**: Libre Baskerville (headings/branding), Roboto (body/UI elements).
- **Vibe**: Clean, modern, trustworthy, replicating the look of physical medical charts and the Kalaza Care website.
