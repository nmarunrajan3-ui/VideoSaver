package com.instasaver.app

import com.instasaver.app.data.local.Converters
import com.instasaver.app.data.local.DownloadEntity
import com.instasaver.app.data.local.DownloadStatus
import com.instasaver.app.data.local.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadEntityTest {

    private val converters = Converters()

    @Test
    fun testMediaTypeConversion() {
        for (type in MediaType.entries) {
            val str = converters.fromMediaType(type)
            val converted = converters.toMediaType(str)
            assertEquals(type, converted)
        }
    }

    @Test
    fun testDownloadStatusConversion() {
        for (status in DownloadStatus.entries) {
            val str = converters.fromDownloadStatus(status)
            val converted = converters.toDownloadStatus(str)
            assertEquals(status, converted)
        }
    }

    @Test
    fun testDownloadEntityDefaults() {
        val entity = DownloadEntity(
            id = 1L,
            instagramUrl = "https://www.instagram.com/reel/C123/",
            shortcode = "C123",
            mediaType = MediaType.REEL,
            downloadUrl = "https://scontent.cdninstagram.com/v/123.mp4"
        )

        assertEquals(1L, entity.id)
        assertEquals(DownloadStatus.PENDING, entity.status)
        assertEquals(0, entity.downloadProgress)
        assertEquals(0L, entity.fileSizeBytes)
    }
}
