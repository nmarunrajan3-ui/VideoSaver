package com.instasaver.app.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.instasaver.app.data.local.DownloadDao
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaImporter(
    private val context: Context,
    private val mediaStoreSaver: MediaStoreSaver,
    private val downloadDao: DownloadDao
) {

    suspend fun importMedia(
        sourceUri: Uri,
        assignedType: MediaType = MediaType.VIDEO,
        customTitle: String? = null
    ): Result<DownloadEntity> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(sourceUri) ?: if (assignedType == MediaType.PHOTO) "image/jpeg" else "video/mp4"
            val isVideo = mimeType.startsWith("video/") || assignedType == MediaType.VIDEO || assignedType == MediaType.REEL || assignedType == MediaType.STORY

            val effectiveMediaType = when {
                assignedType == MediaType.STORY -> MediaType.STORY
                assignedType == MediaType.REEL -> MediaType.REEL
                isVideo -> MediaType.VIDEO
                else -> MediaType.PHOTO
            }

            var originalName = "imported_${System.currentTimeMillis()}"
            var sizeBytes = 0L

            contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) originalName = cursor.getString(nameIndex) ?: originalName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }

            val extension = if (isVideo) "mp4" else "jpg"
            val targetFileName = "instasaver_import_${System.currentTimeMillis()}.$extension"

            val stream = contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot read media stream"))

            val saveResult = mediaStoreSaver.saveMediaStream(
                inputStream = stream,
                fileName = targetFileName,
                mediaType = effectiveMediaType,
                mimeType = mimeType,
                totalBytes = sizeBytes,
                onProgress = { _, _ -> }
            )

            val shortcode = "import_" + UUID.randomUUID().toString().take(8)
            val title = customTitle?.ifBlank { null } ?: originalName

            val entity = DownloadEntity(
                instagramUrl = "local://$shortcode",
                shortcode = shortcode,
                mediaType = effectiveMediaType,
                title = title,
                author = "Manual Import",
                thumbnailUrl = saveResult.uri.toString(),
                downloadUrl = saveResult.uri.toString(),
                localFilePath = saveResult.filePath,
                localUriString = saveResult.uri.toString(),
                fileSizeBytes = saveResult.bytesWritten,
                downloadProgress = 100,
                status = DownloadStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )

            val insertedId = downloadDao.insert(entity)
            val insertedEntity = entity.copy(id = insertedId)

            Result.success(insertedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
