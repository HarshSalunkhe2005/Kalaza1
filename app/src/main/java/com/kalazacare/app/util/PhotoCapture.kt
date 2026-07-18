package com.kalazacare.app.util

import java.time.LocalDateTime

/**
 * Placeholder for the real camera capture / backend upload pipeline.
 * Until that's wired, "capturing" a photo mints a fake storage URL with
 * a 48h expiry — evidence photos are purged 2 days after upload per policy.
 */
object PhotoCapture {
    const val EVIDENCE_RETENTION_HOURS = 48L

    fun capture(): PhotoEvidence {
        val now = LocalDateTime.now()
        return PhotoEvidence(
            url = "mock://evidence/${System.currentTimeMillis()}.jpg",
            expiresAt = now.plusHours(EVIDENCE_RETENTION_HOURS),
        )
    }
}

data class PhotoEvidence(
    val url: String,
    val expiresAt: LocalDateTime,
)
