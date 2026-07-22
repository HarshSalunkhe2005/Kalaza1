package com.kalazacare.app.util

import android.content.Context
import android.net.Uri
import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.storage.storage
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val EVIDENCE_BUCKET = "evidence"

/**
 * Real evidence-photo upload to Supabase Storage — takes the local file the
 * camera just wrote to (via [CameraCaptureFile]) and uploads it. The bucket
 * is private (patient medication evidence, not public data), so what gets
 * stored/persisted everywhere downstream (`PhotoEvidence.url`, and the
 * `*_photo_url` DB columns) is the bare object *path*, not a fetchable URL —
 * callers that need to actually display the photo must call [signedUrl] to
 * mint a short-lived, authenticated one at view time. Evidence is purged
 * ~48h after upload by the `cleanup-photos` Edge Function, not by the app.
 */
object PhotoUploader {
    const val EVIDENCE_RETENTION_HOURS = 48L

    suspend fun upload(context: Context, localUri: Uri): PhotoEvidence {
        val bytes = context.contentResolver.openInputStream(localUri)?.use { it.readBytes() }
            ?: error("Could not read captured photo")
        val path = "${UUID.randomUUID()}.jpg"
        SupabaseClients.main.storage.from(EVIDENCE_BUCKET).upload(path, bytes)
        return PhotoEvidence(url = path, expiresAt = LocalDateTime.now().plusHours(EVIDENCE_RETENTION_HOURS))
    }

    /** Mints a short-lived signed URL for viewing a stored evidence photo (the path returned by [upload]). */
    suspend fun signedUrl(path: String, validForSeconds: Int = 3600): String =
        SupabaseClients.main.storage.from(EVIDENCE_BUCKET).createSignedUrl(path, validForSeconds.seconds)
}

data class PhotoEvidence(
    val url: String,
    val expiresAt: LocalDateTime,
)
