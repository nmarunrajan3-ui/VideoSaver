package com.instasaver.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["shortcode"]),
        Index(value = ["mediaType"]),
        Index(value = ["status"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val instagramUrl: String,
    val shortcode: String,
    val mediaType: MediaType,
    val title: String = "",
    val author: String = "",
    val thumbnailUrl: String? = null,
    val downloadUrl: String,
    val localFilePath: String? = null,
    val localUriString: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadProgress: Int = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String? = null,
    val carouselItemsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
