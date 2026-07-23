package com.kalazacare.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline cache for the patient list — foundation only. Only Patients are
 * cached so far; other entities (vitals, medications, etc.) still require a
 * live connection. Written to on every successful [SupabasePatientRepository]
 * fetch, read from only when that fetch fails (no connectivity).
 */
@Entity(tableName = "cached_patients")
data class PatientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val roomNo: String,
    val medicalHistory: String,
    val currentIssues: String,
    val allergies: String,
    val emergencyContact: String,
    val emergencyPhone: String,
    val admissionDate: String,
    val isArchived: Boolean,
    val primaryDiagnosis: String,
)
