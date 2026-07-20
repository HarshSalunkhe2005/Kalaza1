package com.kalazacare.app.util

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.util.UUID

/**
 * Real evidence-photo upload to Firebase Storage — takes the local file the
 * camera just wrote to (via [CameraCaptureFile]) and uploads it, returning the
 * real download URL. Evidence is purged 48h after upload per policy (see
 * [EVIDENCE_RETENTION_HOURS]) — nothing automatic enforces that deletion yet;
 * a scheduled Cloud Function is the natural place for it later.
 */
object PhotoUploader {
    const val EVIDENCE_RETENTION_HOURS = 48L

    suspend fun upload(localUri: Uri): PhotoEvidence {
        val ref = FirebaseStorage.getInstance().reference.child("evidence/${UUID.randomUUID()}.jpg")
        ref.putFile(localUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        return PhotoEvidence(url = downloadUrl, expiresAt = LocalDateTime.now().plusHours(EVIDENCE_RETENTION_HOURS))
    }
}

data class PhotoEvidence(
    val url: String,
    val expiresAt: LocalDateTime,
)
