package com.kalazacare.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves a file straight into the device's Downloads folder — no share sheet,
 * no user-facing "send to" step. Uses MediaStore on API 29+ (no permission
 * needed there); falls back to the public Downloads directory on older
 * devices, which relies on the WRITE_EXTERNAL_STORAGE permission already
 * declared (maxSdkVersion 28) in the manifest.
 */
object DownloadsSaver {
    fun saveToDownloads(context: Context, filename: String, mimeType: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }
    }
}
