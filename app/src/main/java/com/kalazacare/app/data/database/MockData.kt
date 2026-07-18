package com.kalazacare.app.data.database

import com.kalazacare.app.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Realistic mock data for Kalaza Care - Pune senior assisted living facility.
 * All data is fictional and used only for UI development/testing.
 * Will be replaced by Firestore data when Firebase is wired up.
 */
object MockData {

    // ──────────────────────────────────────────────────────────────────────────
    // Staff
    // ──────────────────────────────────────────────────────────────────────────
    val staffList = listOf(
        Staff(id = "staff_1", name = "Admin Priya Sharma", email = "admin@kalazacare.com",
            role = UserRole.ADMIN, phone = "+91 98765 00001", isActive = true,
            joinedDate = LocalDate.of(2022, 3, 1)),
        Staff(id = "staff_2", name = "Nurse Kavita Desai", email = "kavita@kalazacare.com",
            role = UserRole.MEDICINE_STAFF, phone = "+91 98765 00002", isActive = true,
            joinedDate = LocalDate.of(2023, 6, 15)),
        Staff(id = "staff_3", name = "Caregiver Rahul Patil", email = "rahul@kalazacare.com",
            role = UserRole.MEDICINE_STAFF, phone = "+91 98765 00003", isActive = true,
            joinedDate = LocalDate.of(2024, 1, 10)),
        Staff(id = "staff_4", name = "Nurse Sunita More", email = "sunita@kalazacare.com",
            role = UserRole.STAFF, phone = "+91 98765 00004", isActive = false,
            joinedDate = LocalDate.of(2023, 9, 5)),
        Staff(id = "staff_5", name = "Caregiver Anjali Rane", email = "anjali@kalazacare.com",
            role = UserRole.STAFF, phone = "+91 98765 00005", isActive = true,
            joinedDate = LocalDate.of(2024, 5, 20)),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Patients
    // ──────────────────────────────────────────────────────────────────────────
    val patientList = listOf(
        Patient(id = "p1", name = "Ramesh Kulkarni", age = 78, gender = Gender.MALE,
            roomNo = "101", medicalHistory = "Diabetes Type 2 (since 2010), Hypertension (since 2015)",
            currentIssues = "Blood sugar fluctuations, mild knee arthritis",
            allergies = "Penicillin", emergencyContact = "Suresh Kulkarni (Son)",
            emergencyPhone = "+91 99001 00001",
            admissionDate = LocalDate.of(2024, 2, 14),
            primaryDiagnosis = "Diabetes + Hypertension"),

        Patient(id = "p2", name = "Sulochana Bhide", age = 82, gender = Gender.FEMALE,
            roomNo = "102", medicalHistory = "Osteoporosis, Mild Dementia (diagnosed 2023)",
            currentIssues = "Confusion episodes in the evening, fall risk",
            allergies = "None known", emergencyContact = "Meena Joshi (Daughter)",
            emergencyPhone = "+91 99001 00002",
            admissionDate = LocalDate.of(2023, 11, 5),
            primaryDiagnosis = "Dementia + Osteoporosis"),

        Patient(id = "p3", name = "Vijay Gokhale", age = 71, gender = Gender.MALE,
            roomNo = "103", medicalHistory = "Post-hip replacement surgery (March 2026), COPD",
            currentIssues = "Physiotherapy ongoing, shortness of breath on exertion",
            allergies = "Sulfa drugs", emergencyContact = "Anita Gokhale (Wife)",
            emergencyPhone = "+91 99001 00003",
            admissionDate = LocalDate.of(2026, 4, 1),
            primaryDiagnosis = "Post-surgical recovery + COPD"),

        Patient(id = "p4", name = "Indu Apte", age = 75, gender = Gender.FEMALE,
            roomNo = "104", medicalHistory = "Parkinson's disease (Stage 2), Hypothyroidism",
            currentIssues = "Tremors, difficulty swallowing, medication schedule critical",
            allergies = "Aspirin", emergencyContact = "Deepak Apte (Husband)",
            emergencyPhone = "+91 99001 00004",
            admissionDate = LocalDate.of(2025, 7, 20),
            primaryDiagnosis = "Parkinson's Disease"),

        Patient(id = "p5", name = "Shantaram Phadke", age = 88, gender = Gender.MALE,
            roomNo = "201", medicalHistory = "Chronic heart failure, Atrial fibrillation",
            currentIssues = "Edema in legs, daily weight monitoring required",
            allergies = "Ibuprofen", emergencyContact = "Priya Phadke (Daughter)",
            emergencyPhone = "+91 99001 00005",
            admissionDate = LocalDate.of(2024, 8, 12),
            primaryDiagnosis = "Chronic Heart Failure"),

        Patient(id = "p6", name = "Malti Deshpande", age = 69, gender = Gender.FEMALE,
            roomNo = "202", medicalHistory = "Stroke recovery (Jan 2026), Left-side weakness",
            currentIssues = "Speech therapy, occupational therapy, left arm mobility limited",
            allergies = "None", emergencyContact = "Arun Deshpande (Son)",
            emergencyPhone = "+91 99001 00006",
            admissionDate = LocalDate.of(2026, 2, 1),
            primaryDiagnosis = "Post-Stroke Recovery"),

        Patient(id = "p7", name = "Ganpat Joshi", age = 80, gender = Gender.MALE,
            roomNo = "203", medicalHistory = "Prostate cancer (palliative stage), Chronic pain",
            currentIssues = "Pain management, palliative care, comfort focused",
            allergies = "Codeine", emergencyContact = "Mangal Joshi (Son)",
            emergencyPhone = "+91 99001 00007",
            admissionDate = LocalDate.of(2025, 12, 3),
            primaryDiagnosis = "Palliative – Prostate Cancer"),

        Patient(id = "p8", name = "Usha Naik", age = 73, gender = Gender.FEMALE,
            roomNo = "204", medicalHistory = "Type 1 Diabetes, Retinopathy",
            currentIssues = "Insulin-dependent, vision impairment, requires assistance with meals",
            allergies = "Latex", emergencyContact = "Rohit Naik (Son)",
            emergencyPhone = "+91 99001 00008",
            admissionDate = LocalDate.of(2025, 5, 18),
            primaryDiagnosis = "Insulin-Dependent Diabetes"),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Vitals (last 7 days for patient p1)
    // ──────────────────────────────────────────────────────────────────────────
    val vitalRecords = listOf(
        VitalRecord(id = "v1", patientId = "p1", date = LocalDate.now().minusDays(6),
            time = LocalTime.of(8, 30), pulse = "76", bp = "138/88", spo2 = "97",
            temperature = "98.4", sugarFasting = "142", sugarPP = "210", signedBy = "Kavita Desai"),
        VitalRecord(id = "v2", patientId = "p1", date = LocalDate.now().minusDays(5),
            time = LocalTime.of(8, 15), pulse = "78", bp = "135/85", spo2 = "98",
            temperature = "98.6", sugarFasting = "138", sugarPP = "198", signedBy = "Kavita Desai"),
        VitalRecord(id = "v3", patientId = "p1", date = LocalDate.now().minusDays(4),
            time = LocalTime.of(8, 45), pulse = "74", bp = "140/90", spo2 = "96",
            temperature = "99.1", sugarFasting = "155", sugarPP = "230", signedBy = "Rahul Patil"),
        VitalRecord(id = "v4", patientId = "p1", date = LocalDate.now().minusDays(3),
            time = LocalTime.of(9, 0), pulse = "80", bp = "132/84", spo2 = "97",
            temperature = "98.2", sugarFasting = "130", sugarPP = "185", signedBy = "Kavita Desai"),
        VitalRecord(id = "v5", patientId = "p1", date = LocalDate.now().minusDays(2),
            time = LocalTime.of(8, 30), pulse = "77", bp = "136/88", spo2 = "98",
            temperature = "98.6", sugarFasting = "128", sugarPP = "192", signedBy = "Rahul Patil"),
        VitalRecord(id = "v6", patientId = "p1", date = LocalDate.now().minusDays(1),
            time = LocalTime.of(8, 20), pulse = "75", bp = "134/86", spo2 = "97",
            temperature = "98.5", sugarFasting = "135", sugarPP = "200", signedBy = "Kavita Desai"),
        VitalRecord(id = "v7", patientId = "p1", date = LocalDate.now(),
            time = LocalTime.of(8, 10), pulse = "79", bp = "139/89", spo2 = "97",
            temperature = "98.7", sugarFasting = "145", sugarPP = "", signedBy = ""),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Medications (for patient p1 – today)
    // ──────────────────────────────────────────────────────────────────────────
    val medicationEntries = listOf(
        MedicationEntry(id = "m1", patientId = "p1", medicineName = "Metformin",
            dose = "500mg", quantity = "1 tablet", scheduleTime = LocalTime.of(8, 0),
            scheduledDate = LocalDate.now(), status = MedStatus.ADMINISTERED,
            administeredBy = "Kavita Desai", administeredAt = LocalDateTime.now().minusHours(2),
            allotmentStatus = AllotmentStatus.ALLOTTED, allottedById = "staff_2", allottedByName = "Kavita Desai",
            allottedAt = LocalDateTime.now().minusHours(2).minusMinutes(15),
            allotmentPhotoUrl = "mock://evidence/seed_m1_allot.jpg",
            allotmentPhotoExpiresAt = LocalDateTime.now().plusHours(46),
            administeredPhotoUrl = "mock://evidence/seed_m1_admin.jpg",
            administeredPhotoExpiresAt = LocalDateTime.now().plusHours(46)),
        MedicationEntry(id = "m2", patientId = "p1", medicineName = "Amlodipine",
            dose = "5mg", quantity = "1 tablet", scheduleTime = LocalTime.of(8, 0),
            scheduledDate = LocalDate.now(), status = MedStatus.ADMINISTERED,
            administeredBy = "Kavita Desai", administeredAt = LocalDateTime.now().minusHours(2),
            allotmentStatus = AllotmentStatus.ALLOTTED, allottedById = "staff_2", allottedByName = "Kavita Desai",
            allottedAt = LocalDateTime.now().minusHours(2).minusMinutes(15),
            allotmentPhotoUrl = "mock://evidence/seed_m2_allot.jpg",
            allotmentPhotoExpiresAt = LocalDateTime.now().plusHours(46),
            administeredPhotoUrl = "mock://evidence/seed_m2_admin.jpg",
            administeredPhotoExpiresAt = LocalDateTime.now().plusHours(46)),
        MedicationEntry(id = "m3", patientId = "p1", medicineName = "Aspirin",
            dose = "75mg", quantity = "1 tablet", scheduleTime = LocalTime.of(13, 0),
            scheduledDate = LocalDate.now(), status = MedStatus.PENDING),
        MedicationEntry(id = "m4", patientId = "p1", medicineName = "Metformin",
            dose = "500mg", quantity = "1 tablet", scheduleTime = LocalTime.of(20, 0),
            scheduledDate = LocalDate.now(), status = MedStatus.PENDING),
        MedicationEntry(id = "m5", patientId = "p1", medicineName = "Vitamin D3",
            dose = "1000IU", quantity = "1 capsule", scheduleTime = LocalTime.of(9, 0),
            scheduledDate = LocalDate.now(), status = MedStatus.ADMINISTERED,
            administeredBy = "Kavita Desai", administeredAt = LocalDateTime.now().minusHours(1),
            allotmentStatus = AllotmentStatus.ALLOTTED, allottedById = "staff_2", allottedByName = "Kavita Desai",
            allottedAt = LocalDateTime.now().minusHours(1).minusMinutes(10),
            allotmentPhotoUrl = "mock://evidence/seed_m5_allot.jpg",
            allotmentPhotoExpiresAt = LocalDateTime.now().plusHours(47),
            administeredPhotoUrl = "mock://evidence/seed_m5_admin.jpg",
            administeredPhotoExpiresAt = LocalDateTime.now().plusHours(47)),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Utility Records (for patient p1)
    // ──────────────────────────────────────────────────────────────────────────
    val utilityRecords = listOf(
        UtilityRecord(id = "u1", patientId = "p1", date = LocalDate.now().minusDays(2),
            time = LocalTime.of(7, 0), faceMask = 2, diaperPant = 3, diaperStitch = 1,
            handGloves = 4, tinaBed = 1, wetWipes = 10,
            issuedToCaregiver = "Rahul Patil", issuedBySupervisor = "Kavita Desai",
            checkedBy = "Admin Priya"),
        UtilityRecord(id = "u2", patientId = "p1", date = LocalDate.now().minusDays(1),
            time = LocalTime.of(7, 0), faceMask = 2, diaperPant = 3, diaperStitch = 0,
            handGloves = 4, tinaBed = 1, wetWipes = 8,
            issuedToCaregiver = "Rahul Patil", issuedBySupervisor = "Kavita Desai",
            checkedBy = "Admin Priya"),
        UtilityRecord(id = "u3", patientId = "p1", date = LocalDate.now(),
            time = LocalTime.of(7, 0), faceMask = 2, diaperPant = 2, diaperStitch = 1,
            handGloves = 4, tinaBed = 1, wetWipes = 10,
            issuedToCaregiver = "Rahul Patil", issuedBySupervisor = "Kavita Desai",
            checkedBy = ""),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Doctor Visits
    // ──────────────────────────────────────────────────────────────────────────
    val doctorVisits = listOf(
        DoctorVisit(id = "dv1", patientId = "p1", doctorName = "Dr. Suresh Mehta",
            specialty = "Endocrinologist", date = LocalDate.now().minusDays(14),
            notes = "HbA1c improved to 7.2. Continue current Metformin dose. Review in 3 months.",
            nextVisitDate = LocalDate.now().plusDays(76),
            prescriptionChanges = "No changes"),
        DoctorVisit(id = "dv2", patientId = "p1", doctorName = "Dr. Anita Joshi",
            specialty = "Cardiologist", date = LocalDate.now().minusDays(30),
            notes = "BP slightly elevated. Increased Amlodipine to 5mg. Monitor daily.",
            nextVisitDate = LocalDate.now().plusDays(30),
            prescriptionChanges = "Amlodipine: 2.5mg → 5mg"),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Care Notes
    // ──────────────────────────────────────────────────────────────────────────
    val careNotes = listOf(
        CareNote(id = "cn1", patientId = "p1", staffId = "staff_2",
            staffName = "Kavita Desai",
            timestamp = LocalDateTime.now().minusHours(3),
            note = "Patient had a good morning. Ate full breakfast. Mood was cheerful. Complained of slight knee pain around 10 AM — applied cold pack as advised."),
        CareNote(id = "cn2", patientId = "p1", staffId = "staff_3",
            staffName = "Rahul Patil",
            timestamp = LocalDateTime.now().minusDays(1).minusHours(5),
            note = "Evening restlessness noted. Patient had difficulty sleeping. Given warm milk. Slept by 10:30 PM."),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Approval Requests
    // ──────────────────────────────────────────────────────────────────────────
    val approvalRequests = listOf(
        ApprovalRequest(id = "ar1", patientId = "p2", patientName = "Sulochana Bhide",
            requestedById = "staff_2", requestedByName = "Kavita Desai",
            fieldChanged = "Current Issues",
            oldValue = "Confusion episodes in the evening, fall risk",
            newValue = "Confusion episodes throughout the day, high fall risk - bed rails requested",
            status = ApprovalStatus.PENDING,
            timestamp = LocalDateTime.now().minusHours(2)),
        ApprovalRequest(id = "ar2", patientId = "p3", patientName = "Vijay Gokhale",
            requestedById = "staff_3", requestedByName = "Rahul Patil",
            fieldChanged = "Allergies",
            oldValue = "Sulfa drugs",
            newValue = "Sulfa drugs, Latex",
            status = ApprovalStatus.PENDING,
            timestamp = LocalDateTime.now().minusHours(5)),
        ApprovalRequest(id = "ar3", patientId = "p1", patientName = "Ramesh Kulkarni",
            requestedById = "staff_2", requestedByName = "Kavita Desai",
            fieldChanged = "Emergency Phone",
            oldValue = "+91 99001 00001",
            newValue = "+91 99002 11111",
            status = ApprovalStatus.APPROVED,
            reviewedByName = "Admin Priya Sharma",
            timestamp = LocalDateTime.now().minusDays(1),
            reviewedAt = LocalDateTime.now().minusHours(18)),
        ApprovalRequest(id = "ar4", patientId = "p5", patientName = "Shantaram Phadke",
            requestedById = "staff_3", requestedByName = "Rahul Patil",
            fieldChanged = "Medical History",
            oldValue = "Chronic heart failure, Atrial fibrillation",
            newValue = "Chronic heart failure, Atrial fibrillation, Recent hospitalization May 2026",
            status = ApprovalStatus.REJECTED,
            reviewedByName = "Admin Priya Sharma",
            rejectionReason = "Incomplete information — please include discharge summary reference.",
            timestamp = LocalDateTime.now().minusDays(2),
            reviewedAt = LocalDateTime.now().minusDays(2).plusHours(3)),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Audit Log
    // ──────────────────────────────────────────────────────────────────────────
    val auditLog = listOf(
        AuditLogEntry(id = "al1", action = "Medication Administered",
            performedByName = "Kavita Desai", targetPatientName = "Ramesh Kulkarni",
            details = "Metformin 500mg administered at 08:05 AM",
            timestamp = LocalDateTime.now().minusHours(2), iconName = "medication"),
        AuditLogEntry(id = "al2", action = "Vitals Recorded",
            performedByName = "Kavita Desai", targetPatientName = "Ramesh Kulkarni",
            details = "BP: 139/89, Pulse: 79, SPO2: 97%, Temp: 98.7°F",
            timestamp = LocalDateTime.now().minusHours(2).minusMinutes(10), iconName = "monitor_heart"),
        AuditLogEntry(id = "al3", action = "Edit Request Approved",
            performedByName = "Admin Priya Sharma", targetPatientName = "Ramesh Kulkarni",
            details = "Approved change to Emergency Phone number by Kavita Desai",
            timestamp = LocalDateTime.now().minusHours(18), iconName = "check_circle"),
        AuditLogEntry(id = "al4", action = "Care Note Added",
            performedByName = "Rahul Patil", targetPatientName = "Sulochana Bhide",
            details = "Evening care note added — patient had restless night",
            timestamp = LocalDateTime.now().minusDays(1).minusHours(5), iconName = "note_add"),
        AuditLogEntry(id = "al5", action = "Edit Request Rejected",
            performedByName = "Admin Priya Sharma", targetPatientName = "Shantaram Phadke",
            details = "Rejected medical history edit request — incomplete information",
            timestamp = LocalDateTime.now().minusDays(2).plusHours(3), iconName = "cancel"),
        AuditLogEntry(id = "al6", action = "Patient Added",
            performedByName = "Admin Priya Sharma", targetPatientName = "Malti Deshpande",
            details = "New patient admitted — Room 202",
            timestamp = LocalDateTime.now().minusDays(10), iconName = "person_add"),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Allotment Requests (regular staff flagging a forgotten allotment)
    // ──────────────────────────────────────────────────────────────────────────
    val allotmentRequests = listOf(
        AllotmentRequest(id = "arq1", medicationEntryId = "m3", patientId = "p1", patientName = "Ramesh Kulkarni",
            medicineName = "Aspirin", scheduledTime = LocalTime.of(13, 0),
            requestedById = "staff_5", requestedByName = "Anjali Rane",
            status = AllotmentRequestStatus.PENDING,
            timestamp = LocalDateTime.now().minusMinutes(20)),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Utility Items (configurable)
    // ──────────────────────────────────────────────────────────────────────────
    val utilityItems = listOf(
        UtilityItem(id = "ui1", name = "Face Mask",        unit = "pcs", displayOrder = 1),
        UtilityItem(id = "ui2", name = "Diaper (Pant)",    unit = "pcs", displayOrder = 2),
        UtilityItem(id = "ui3", name = "Diaper (Stitch)",  unit = "pcs", displayOrder = 3),
        UtilityItem(id = "ui4", name = "Hand Gloves",      unit = "pairs", displayOrder = 4),
        UtilityItem(id = "ui5", name = "Tina Bed",         unit = "pcs", displayOrder = 5),
        UtilityItem(id = "ui6", name = "Wet Wipes",        unit = "pcs", displayOrder = 6),
    )
}
