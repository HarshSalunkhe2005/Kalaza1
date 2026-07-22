package com.kalazacare.app.util

import android.content.Context
import android.net.Uri
import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.storage.storage
import java.time.LocalDateTime
import java.util.UUID

private const val EVIDENCE_BUCKET = "evidence"

/**
 * Real evidence-photo upload to Supabase Storage — takes the local file the
 * camera just wrote to (via [CameraCaptureFile]) and uploads it, returning the
 * public URL. Evidence is purged ~48h after upload via a GCS-style lifecycle
 * policy on the bucket (age-based, see project setup notes), not by the app.
 */
object PhotoUploader {
    const val EVIDENCE_RETENTION_HOURS = 48L

    suspend fun upload(context: Context, localUri: Uri): PhotoEvidence {
        val bytes = context.contentResolver.openInputStream(localUri)?.use { it.readBytes() }
            ?: error("Could not read captured photo")
        val path = "${UUID.randomUUID()}.jpg"
        val bucket = SupabaseClients.main.storage.from(EVIDENCE_BUCKET)
        bucket.upload(path, bytes)
        val publicUrl = bucket.publicUrl(path)
        return PhotoEvidence(url = publicUrl, expiresAt = LocalDateTime.now().plusHours(EVIDENCE_RETENTION_HOURS))
    }
}

data class PhotoEvidence(
    val url: String,
    val expiresAt: LocalDateTime,
)
