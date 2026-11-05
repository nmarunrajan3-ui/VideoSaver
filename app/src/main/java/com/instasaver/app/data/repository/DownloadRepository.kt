package com.instasaver.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.instasaver.app.data.local.DownloadDao
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.MediaItem
import com.instasaver.app.data.model.ResolutionResult
import com.instasaver.app.network.InstagramMediaResolver
import com.instasaver.app.network.InstagramUrlParser
import com.instasaver.app.workers.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val mediaResolver: InstagramMediaResolver = InstagramMediaResolver(context)
) {

    private val workManager = WorkManager.getInstance(context)

    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getDownloadsByType(type: MediaType): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByType(type)

    fun getRecentDownloads(limit: Int = 10): Flow<List<DownloadEntity>> =
        downloadDao.getRecentDownloads(limit)

    fun getDownloadById(id: Long): Flow<DownloadEntity?> = downloadDao.getDownloadById(id)

    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    fun getCompletedCount(): Flow<Int> = downloadDao.getCompletedCount()

    fun getTotalDownloadedBytes(): Flow<Long?> = downloadDao.getTotalDownloadedBytes()

    suspend fun resolveUrl(url: String): ResolutionResult {
        return mediaResolver.resolve(url)
    }

    suspend fun checkDuplicate(url: String): DownloadEntity? {
        val parsed = InstagramUrlParser.parse(url)
        val shortcode = parsed?.shortcode ?: ""
        return if (shortcode.isNotEmpty()) {
            downloadDao.findByShortcode(shortcode) ?: downloadDao.findByUrl(url)
        } else {
            downloadDao.findByUrl(url)
        }
    }

    suspend fun startDownload(mediaItem: MediaItem): Long {
        val entity = DownloadEntity(
            instagramUrl = mediaItem.originalUrl,
            shortcode = mediaItem.shortcode,
            mediaType = mediaItem.mediaType,
            title = mediaItem.title,
            author = mediaItem.author,
            thumbnailUrl = mediaItem.thumbnailUrl,
            downloadUrl = mediaItem.downloadUrl,
            status = DownloadStatus.PENDING,
            downloadProgress = 0,
            fileSizeBytes = 0L,
            createdAt = System.currentTimeMillis()
        )

        val id = downloadDao.insert(entity)
        enqueueWork(id, mediaItem.downloadUrl, mediaItem.originalUrl, mediaItem.mediaType, mediaItem.title, mediaItem.shortcode)
        return id
    }

    suspend fun retryDownload(id: Long) {
        val entity = downloadDao.getDownloadByIdDirect(id) ?: return
        downloadDao.updateStatus(id, DownloadStatus.PENDING, 0, null)
        enqueueWork(id, entity.downloadUrl, entity.instagramUrl, entity.mediaType, entity.title, entity.shortcode)
    }

    suspend fun cancelDownload(id: Long) {
        val uniqueWorkName = "download_$id"
        workManager.cancelUniqueWork(uniqueWorkName)
        downloadDao.updateStatus(id, DownloadStatus.CANCELLED, 0, "Cancelled by user")
    }

    suspend fun deleteDownload(id: Long, deleteFile: Boolean = true) {
        val entity = downloadDao.getDownloadByIdDirect(id)
        if (entity != null) {
            if (deleteFile) {
                // Delete local file if it exists
                entity.localUriString?.let { uriStr ->
                    runCatching {
                        context.contentResolver.delete(Uri.parse(uriStr), null, null)
                    }
                }
                entity.localFilePath?.let { path ->
                    runCatching {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
            }
            downloadDao.deleteById(id)
        }
    }

    suspend fun clearHistory() {
        downloadDao.clearAll()
    }

    private fun enqueueWork(
        downloadId: Long,
        downloadUrl: String,
        originalInstagramUrl: String,
        mediaType: MediaType,
        title: String,
        shortcode: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = workDataOf(
            DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
            DownloadWorker.KEY_DOWNLOAD_URL to downloadUrl,
            DownloadWorker.KEY_ORIGINAL_URL to originalInstagramUrl,
            DownloadWorker.KEY_MEDIA_TYPE to mediaType.name,
            DownloadWorker.KEY_TITLE to title,
            DownloadWorker.KEY_SHORTCODE to shortcode
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .addTag("instasaver_download")
            .build()

        val uniqueWorkName = "download_$downloadId"
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
