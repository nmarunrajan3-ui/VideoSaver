package com.instasaver.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE mediaType = :type ORDER BY createdAt DESC")
    fun getDownloadsByType(type: MediaType): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentDownloads(limit: Int = 10): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun getDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadByIdDirect(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE shortcode = :shortcode LIMIT 1")
    suspend fun findByShortcode(shortcode: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE instagramUrl = :url LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'RESOLVING', 'DOWNLOADING') ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM downloads WHERE status = 'COMPLETED'")
    fun getTotalDownloadedBytes(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadProgress = :progress, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Int, error: String? = null)

    @Query("UPDATE downloads SET downloadProgress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query("UPDATE downloads SET downloadProgress = :progress WHERE id = :id")
    fun updateProgressSync(id: Long, progress: Int)

    @Query("UPDATE downloads SET downloadUrl = :url WHERE id = :id")
    suspend fun updateDownloadUrl(id: Long, url: String)

    @Query("UPDATE downloads SET status = :status, downloadProgress = 100, localFilePath = :filePath, localUriString = :uriString, fileSizeBytes = :fileSize, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, status: DownloadStatus, filePath: String, uriString: String, fileSize: Long, completedAt: Long)

    @Delete
    suspend fun delete(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
