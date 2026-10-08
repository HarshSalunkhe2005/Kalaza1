package com.kalazacare.app.util

import com.kalazacare.app.data.model.DoseTag

/**
 * Checks a scanned/typed dosing-QR payload ("<patient name>|<TAG>") against the dose it is
 * being used to mark given. Returns null when it matches, else the message to show staff.
 * Used by the Admin "Mark Given" override on the Med tab, which — unlike the Scan tab, where
 * the QR itself picks the meds — starts from a dose and so must confirm the QR belongs to it.
 */
fun doseQrMismatch(raw: String, patientName: String, tag: DoseTag): String? {
    val parts = raw.split("|", limit = 2)
    if (parts.size != 2) return "Wrong QR — this isn't a patient dosing QR"
    fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    if (norm(parts[0]) != norm(patientName)) return "Wrong QR — this code is for ${parts[0].trim()}, not $patientName"
    if (parts[1].trim().uppercase() != tag.name) return "Wrong QR — this is the ${parts[1].trim().lowercase()} code, this dose is ${tag.name.lowercase()}"
    return null
}
