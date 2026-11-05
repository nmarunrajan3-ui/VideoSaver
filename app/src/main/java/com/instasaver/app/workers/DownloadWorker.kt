package com.instasaver.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.instasaver.app.data.local.DownloadDao
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.InstaSaverDatabase
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.ResolutionResult
import com.instasaver.app.network.InstagramMediaResolver
import com.instasaver.app.network.NetworkClient
import com.instasaver.app.storage.MediaStoreSaver
import com.instasaver.app.storage.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.regex.Pattern

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val downloadDao: DownloadDao by lazy {
        InstaSaverDatabase.getInstance(context).downloadDao()
    }

    private val mediaStoreSaver = MediaStoreSaver(context)
    private val mediaResolver = InstagramMediaResolver(context)

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_ORIGINAL_URL = "original_url"   // Original media URL for re-resolution
        const val KEY_TITLE = "title"
        const val KEY_SHORTCODE = "shortcode"

        const val CHANNEL_ID = "instasaver_downloads"
        const val CHANNEL_NAME = "InstaSaver Downloads"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val originalInstagramUrl = inputData.getString(KEY_ORIGINAL_URL) ?: ""
        val mediaTypeStr = inputData.getString(KEY_MEDIA_TYPE) ?: MediaType.VIDEO.name
        val mediaType = MediaType.fromString(mediaTypeStr)
        val title = inputData.getString(KEY_TITLE) ?: "Media Download"
        val shortcode = inputData.getString(KEY_SHORTCODE) ?: System.currentTimeMillis().toString()

        createNotificationChannel()

        val notificationId = downloadId.toInt().coerceAtLeast(1001)

        // Show immediate notification for this specific download
        updateNotificationProgress(notificationId, title, 0)

        downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING, 0, null)

        val isVideoByUrl = downloadUrl.contains(".mp4") || downloadUrl.contains("video") ||
                downloadUrl.contains(".m3u8") || downloadUrl.contains("googlevideo")
        val isVideo = when (mediaType) {
            MediaType.PHOTO -> false
            MediaType.REEL, MediaType.VIDEO -> true
            else -> isVideoByUrl
        }
        val extension = if (isVideo) "mp4" else "jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val filename = "InstaSaver_${mediaType.name}_${shortcode}_${System.currentTimeMillis()}.$extension"

        val isHls = downloadUrl.contains(".m3u8") ||
                downloadUrl.contains("hls_playlist") ||
                downloadUrl.contains("manifest.googlevideo.com")

        // ── Pathway A: Native HLS Video + Audio Stream Downloader (YouTube / M3U8) ───
        if (isHls) {
            return@withContext try {
                val saveResult = downloadHlsStream(
                    hlsManifestUrl = downloadUrl,
                    targetFilename = filename,
                    mediaType = mediaType,
                    notificationId = notificationId,
                    title = title,
                    downloadId = downloadId
                )

                if (isStopped) {
                    downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, 0, "Cancelled by user")
                    notificationManager.cancel(notificationId)
                    return@withContext Result.failure()
                }

                downloadDao.markCompleted(
                    id = downloadId,
                    status = DownloadStatus.COMPLETED,
                    filePath = saveResult.filePath,
                    uriString = saveResult.uri.toString(),
                    fileSize = saveResult.bytesWritten,
                    completedAt = System.currentTimeMillis()
                )

                showCompletionNotification(notificationId, title, true, "Saved to ${saveResult.filePath}")
                Result.success()
            } catch (e: Exception) {
                val errorMsg = e.message ?: "HLS download failed"
                if (isStopped) {
                    downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, 0, "Cancelled by user")
                } else {
                    downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, 0, errorMsg)
                    showCompletionNotification(notificationId, title, false, errorMsg)
                }
                Result.failure()
            }
        }

        // ── Pathway B: Progressive Media Stream Downloader (Instagram, FB, X) ─────────
        fun buildRequest(url: String): Request {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val igCookies = cookieManager.getCookie("https://www.instagram.com") ?: ""

            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")
                .header("Connection", "keep-alive")

            if (url.contains("cdninstagram.com") || url.contains("fbcdn.net") || url.contains("instagram.com")) {
                builder.header("Referer", "https://www.instagram.com/")
                if (igCookies.isNotBlank()) {
                    builder.header("Cookie", igCookies)
                }
            }
            return builder.build()
        }

        try {
            var response = NetworkClient.okHttpClient.newCall(buildRequest(downloadUrl)).execute()

            // If CDN URL expired (403/410/404), re-resolve fresh URL
            if (!response.isSuccessful && originalInstagramUrl.isNotBlank()) {
                response.close()
                downloadDao.updateStatus(downloadId, DownloadStatus.RESOLVING, 0, "Re-resolving fresh URL...")

                val freshResult = mediaResolver.resolveHttpOnly(originalInstagramUrl)
                if (freshResult is ResolutionResult.Success) {
                    val freshUrl = freshResult.mediaItem.downloadUrl
                    downloadDao.updateDownloadUrl(downloadId, freshUrl)
                    response = NetworkClient.okHttpClient.newCall(buildRequest(freshUrl)).execute()
                }
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code} – please retry download or open video sniffer."
                response.close()
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, 0, errorMsg)
                showCompletionNotification(notificationId, title, false, errorMsg)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, 0, "Empty response body")
                showCompletionNotification(notificationId, title, false, "Empty response body")
                return@withContext Result.failure()
            }

            val contentLength = body.contentLength()
            var lastReportedProgress = 0
            var lastDbUpdateTime = 0L

            val saveResult = mediaStoreSaver.saveMediaStream(
                inputStream = body.byteStream(),
                fileName = filename,
                mediaType = mediaType,
                mimeType = mimeType,
                totalBytes = contentLength,
                onProgress = { bytesRead, total ->
                    if (isStopped) return@saveMediaStream
                    if (total > 0) {
                        val progress = ((bytesRead * 100) / total).toInt().coerceIn(0, 100)
                        if (progress != lastReportedProgress) {
                            lastReportedProgress = progress
                            val now = System.currentTimeMillis()
                            if (now - lastDbUpdateTime > 350) {
                                lastDbUpdateTime = now
                                downloadDao.updateProgressSync(downloadId, progress)
                                setProgressAsync(workDataOf("progress" to progress))
                                updateNotificationProgress(notificationId, title, progress)
                            }
                        }
                    }
                }
            )

            if (isStopped) {
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, lastReportedProgress, "Cancelled by user")
                notificationManager.cancel(notificationId)
                return@withContext Result.failure()
            }

            downloadDao.markCompleted(
                id = downloadId,
                status = DownloadStatus.COMPLETED,
                filePath = saveResult.filePath,
                uriString = saveResult.uri.toString(),
                fileSize = saveResult.bytesWritten,
                completedAt = System.currentTimeMillis()
            )

            showCompletionNotification(notificationId, title, true, "Saved to ${saveResult.filePath}")
            Result.success()

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Download failed"
            if (isStopped) {
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, 0, "Cancelled by user")
            } else {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, 0, errorMsg)
                showCompletionNotification(notificationId, title, false, errorMsg)
            }
            Result.failure()
        }
    }

    private suspend fun downloadHlsStream(
        hlsManifestUrl: String,
        targetFilename: String,
        mediaType: MediaType,
        notificationId: Int,
        title: String,
        downloadId: Long
    ): SaveResult {
        // Step 1: Download master playlist
        val masterReq = Request.Builder()
            .url(hlsManifestUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()

        val masterContent = NetworkClient.okHttpClient.newCall(masterReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HLS master request failed: HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("Empty master playlist")
        }

        // Step 2: Parse master playlist for video sub-playlist and audio sub-playlist
        val masterLines = masterContent.lines()
        var videoSubUrl: String? = null
        var audioSubUrl: String? = null
        var maxBandwidth = -1

        // Look for audio playlist in EXT-X-MEDIA
        val audioMediaPattern = Pattern.compile("#EXT-X-MEDIA:URI=\"([^\"]+)\",TYPE=AUDIO")
        val audioMatcher = audioMediaPattern.matcher(masterContent)
        if (audioMatcher.find()) {
            audioSubUrl = audioMatcher.group(1)
        }

        // Look for video streams
        for (i in masterLines.indices) {
            val line = masterLines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bwMatcher = Pattern.compile("BANDWIDTH=(\\d+)").matcher(line)
                val bw = if (bwMatcher.find()) bwMatcher.group(1)?.toIntOrNull() ?: 0 else 0
                val isAvc = line.contains("avc1")
                // Prefer avc1 (H.264), or highest bandwidth
                val effectiveBw = if (isAvc) bw + 10000000 else bw
                if (effectiveBw > maxBandwidth && i + 1 < masterLines.size) {
                    val nextUrl = masterLines[i + 1].trim()
                    if (nextUrl.startsWith("http")) {
                        maxBandwidth = effectiveBw
                        videoSubUrl = nextUrl
                    }
                }
            }
        }

        if (videoSubUrl == null) {
            videoSubUrl = hlsManifestUrl
        }

        // Step 3: Fetch video segment URLs
        val videoSegments = fetchSegmentUrls(videoSubUrl)
        if (videoSegments.isEmpty()) throw IllegalStateException("No video segments found in HLS playlist")

        // Step 4: Fetch audio segment URLs (if separate audio stream exists)
        val audioSegments = if (!audioSubUrl.isNullOrBlank()) {
            try { fetchSegmentUrls(audioSubUrl) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val tempVideoFile = File(context.cacheDir, "temp_vid_${System.currentTimeMillis()}.ts")
        val tempAudioFile = if (audioSegments.isNotEmpty()) File(context.cacheDir, "temp_aud_${System.currentTimeMillis()}.ts") else null
        val tempMuxedFile = File(context.cacheDir, "temp_mux_${System.currentTimeMillis()}.mp4")

        try {
            val totalSteps = (videoSegments.size + audioSegments.size).coerceAtLeast(1)
            var currentStep = 0

            // Step 5: Download video segments sequentially into tempVideoFile
            FileOutputStream(tempVideoFile).use { outVid ->
                for (segUrl in videoSegments) {
                    if (isStopped) throw InterruptedException("Cancelled")
                    downloadUrlToStream(segUrl, outVid)
                    currentStep++
                    val progress = ((currentStep * 80) / totalSteps).coerceIn(0, 80)
                    downloadDao.updateProgress(downloadId, progress)
                    updateNotificationProgress(notificationId, title, progress)
                    setProgressAsync(workDataOf("progress" to progress))
                }
            }

            // Step 6: Download audio segments sequentially into tempAudioFile (if present)
            if (tempAudioFile != null && audioSegments.isNotEmpty()) {
                FileOutputStream(tempAudioFile).use { outAud ->
                    for (segUrl in audioSegments) {
                        if (isStopped) throw InterruptedException("Cancelled")
                        downloadUrlToStream(segUrl, outAud)
                        currentStep++
                        val progress = ((currentStep * 85) / totalSteps).coerceIn(0, 85)
                        downloadDao.updateProgress(downloadId, progress)
                        updateNotificationProgress(notificationId, title, progress)
                        setProgressAsync(workDataOf("progress" to progress))
                    }
                }
            }

            // Step 7: Mux video and audio into tempMuxedFile
            downloadDao.updateProgress(downloadId, 88)
            updateNotificationProgress(notificationId, title, 88)
            setProgressAsync(workDataOf("progress" to 88))

            val finalFileToSave = if (tempAudioFile != null && tempAudioFile.exists() && tempAudioFile.length() > 0) {
                try {
                    muxTsToMp4(tempVideoFile, tempAudioFile, tempMuxedFile)
                    if (tempMuxedFile.exists() && tempMuxedFile.length() > 0) {
                        tempMuxedFile
                    } else {
                        tempVideoFile
                    }
                } catch (_: Exception) {
                    tempVideoFile
                }
            } else {
                tempVideoFile
            }

            // Step 8: Save to MediaStore
            downloadDao.updateProgress(downloadId, 95)
            updateNotificationProgress(notificationId, title, 95)
            setProgressAsync(workDataOf("progress" to 95))

            val saveResult = FileInputStream(finalFileToSave).use { inStream ->
                mediaStoreSaver.saveMediaStream(
                    inputStream = inStream,
                    fileName = targetFilename,
                    mediaType = mediaType,
                    mimeType = "video/mp4",
                    totalBytes = finalFileToSave.length(),
                    onProgress = { _, _ -> }
                )
            }

            updateNotificationProgress(notificationId, title, 100)
            setProgressAsync(workDataOf("progress" to 100))
            return saveResult

        } finally {
            try { tempVideoFile.delete() } catch (_: Exception) {}
            try { tempAudioFile?.delete() } catch (_: Exception) {}
            try { tempMuxedFile.delete() } catch (_: Exception) {}
        }
    }

    private fun fetchSegmentUrls(subPlaylistUrl: String): List<String> {
        val req = Request.Builder()
            .url(subPlaylistUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()
        val content = NetworkClient.okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string().orEmpty()
        }
        val baseUrl = subPlaylistUrl.substringBeforeLast('/') + "/"
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                if (line.startsWith("http://") || line.startsWith("https://")) line
                else baseUrl + line
            }
    }

    private fun downloadUrlToStream(url: String, out: OutputStream) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()
        NetworkClient.okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                resp.body?.byteStream()?.copyTo(out)
            }
        }
    }

    private fun muxTsToMp4(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIdx = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i)
                videoTrackIdx = muxer.addTrack(format)
                break
            }
        }

        var audioTrackIdx = -1
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioExtractor.selectTrack(i)
                audioTrackIdx = muxer.addTrack(format)
                break
            }
        }

        if (videoTrackIdx == -1 && audioTrackIdx == -1) {
            videoExtractor.release()
            audioExtractor.release()
            muxer.release()
            return
        }

        muxer.start()
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodec.BufferInfo()

        if (videoTrackIdx >= 0) {
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIdx, buffer, info)
                videoExtractor.advance()
            }
        }

        if (audioTrackIdx >= 0) {
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = audioExtractor.sampleTime
                info.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackIdx, buffer, info)
                audioExtractor.advance()
            }
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }

    private fun createForegroundInfo(notificationId: Int, title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotificationProgress(notificationId: Int, title: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun showCompletionNotification(notificationId: Int, title: String, success: Boolean, msg: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (success) "Download Complete ✓" else "Download Failed")
            .setContentText(if (success) title else "$title: $msg")
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for InstaSaver media downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
