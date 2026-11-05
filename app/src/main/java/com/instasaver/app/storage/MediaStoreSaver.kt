package com.instasaver.app.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.instasaver.app.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

data class SaveResult(
    val uri: Uri,
    val filePath: String,
    val bytesWritten: Long
)

class MediaStoreSaver(private val context: Context) {

    suspend fun saveMediaStream(
        inputStream: InputStream,
        fileName: String,
        mediaType: MediaType,
        mimeType: String,
        totalBytes: Long,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ): SaveResult = withContext(Dispatchers.IO) {
        val isVideo = mimeType.startsWith("video/") || fileName.endsWith(".mp4") ||
                mediaType == MediaType.VIDEO || mediaType == MediaType.REEL
        val subDir = "InstaSaver"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStoreScoped(
                inputStream = inputStream,
                fileName = fileName,
                isVideo = isVideo,
                mimeType = mimeType,
                subDir = subDir,
                totalBytes = totalBytes,
                onProgress = onProgress
            )
        } else {
            saveWithLegacyStorage(
                inputStream = inputStream,
                fileName = fileName,
                isVideo = isVideo,
                subDir = subDir,
                totalBytes = totalBytes,
                onProgress = onProgress
            )
        }
    }

    private fun saveWithMediaStoreScoped(
        inputStream: InputStream,
        fileName: String,
        isVideo: Boolean,
        mimeType: String,
        subDir: String,
        totalBytes: Long,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ): SaveResult {
        val relativePath = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/$subDir"
        } else {
            "${Environment.DIRECTORY_PICTURES}/$subDir"
        }

        val contentUri = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(contentUri, values)
            ?: throw IllegalStateException("Failed to create MediaStore record for $fileName")

        var bytesWritten = 0L

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bytesWritten = copyWithProgress(inputStream, outputStream, totalBytes, onProgress)
            } ?: throw IllegalStateException("Could not open output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            val filePath = "$relativePath/$fileName"
            return SaveResult(uri = uri, filePath = filePath, bytesWritten = bytesWritten)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveWithLegacyStorage(
        inputStream: InputStream,
        fileName: String,
        isVideo: Boolean,
        subDir: String,
        totalBytes: Long,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ): SaveResult {
        val baseDir = if (isVideo) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }

        val targetDir = File(baseDir, subDir)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val file = File(targetDir, fileName)
        val bytesWritten = FileOutputStream(file).use { outputStream ->
            copyWithProgress(inputStream, outputStream, totalBytes, onProgress)
        }

        // Scan file so it shows in Gallery immediately
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )

        return SaveResult(
            uri = Uri.fromFile(file),
            filePath = file.absolutePath,
            bytesWritten = bytesWritten
        )
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ): Long {
        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int
        var totalRead = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            onProgress(totalRead, totalBytes)
        }
        output.flush()
        return totalRead
    }
}
