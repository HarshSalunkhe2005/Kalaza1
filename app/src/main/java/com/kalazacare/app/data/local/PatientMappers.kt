package com.kalazacare.app.data.local

import com.kalazacare.app.data.model.Gender
import com.kalazacare.app.data.model.Patient
import java.time.LocalDate

fun Patient.toEntity() = PatientEntity(
    id = id, name = name, age = age, gender = gender.name, roomNo = roomNo,
    medicalHistory = medicalHistory, currentIssues = currentIssues, allergies = allergies,
    emergencyContact = emergencyContact, emergencyPhone = emergencyPhone,
    admissionDate = admissionDate.toString(), isArchived = isArchived, primaryDiagnosis = primaryDiagnosis,
)

fun PatientEntity.toDomain() = Patient(
    id = id, name = name, age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.MALE),
    roomNo = roomNo, medicalHistory = medicalHistory, currentIssues = currentIssues, allergies = allergies,
    emergencyContact = emergencyContact, emergencyPhone = emergencyPhone,
    admissionDate = runCatching { LocalDate.parse(admissionDate) }.getOrDefault(LocalDate.now()),
    isArchived = isArchived, primaryDiagnosis = primaryDiagnosis,
)
