package com.instasaver.app

import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.CarouselItem
import com.instasaver.app.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselPostTest {

    @Test
    fun testTenItemCarouselStructure() {
        val count = 10
        val items = (1..count).map { index ->
            val isVid = index % 3 == 0 // Slide 3, 6, 9 are videos, others are photos
            CarouselItem(
                url = if (isVid) "https://scontent.cdninstagram.com/v/t50/item_$index.mp4" else "https://scontent.cdninstagram.com/v/t51/item_$index.jpg",
                isVideo = isVid,
                thumbnailUrl = "https://scontent.cdninstagram.com/v/t51/thumb_$index.jpg"
            )
        }

        val parentMedia = MediaItem(
            shortcode = "C_TEST_POST_10",
            originalUrl = "https://www.instagram.com/p/C_TEST_POST_10/",
            mediaType = MediaType.CAROUSEL,
            title = "Test 10-Item Instagram Post",
            author = "creative_creator",
            downloadUrl = items[0].url,
            thumbnailUrl = items[0].thumbnailUrl,
            carouselItems = items,
            isVideo = items[0].isVideo
        )

        assertEquals(10, parentMedia.carouselItems.size)
        assertEquals(MediaType.CAROUSEL, parentMedia.mediaType)

        // Verify individual slide derivation (separate downloads)
        for (i in 0 until count) {
            val cItem = parentMedia.carouselItems[i]
            val separateItem = parentMedia.copy(
                shortcode = "${parentMedia.shortcode}_${i + 1}",
                originalUrl = "${parentMedia.originalUrl}#${i + 1}",
                title = "${parentMedia.title} (#${i + 1})",
                downloadUrl = cItem.url,
                thumbnailUrl = cItem.thumbnailUrl ?: cItem.url,
                isVideo = cItem.isVideo,
                mediaType = if (cItem.isVideo) MediaType.VIDEO else MediaType.PHOTO,
                carouselItems = emptyList()
            )

            assertNotNull(separateItem)
            assertEquals("C_TEST_POST_10_${i + 1}", separateItem.shortcode)
            assertEquals("https://www.instagram.com/p/C_TEST_POST_10/#${i + 1}", separateItem.originalUrl)
            assertTrue(separateItem.carouselItems.isEmpty())

            // Verify extension and media type
            if (cItem.isVideo) {
                assertEquals(MediaType.VIDEO, separateItem.mediaType)
                assertTrue(separateItem.downloadUrl.endsWith(".mp4"))
            } else {
                assertEquals(MediaType.PHOTO, separateItem.mediaType)
                assertTrue(separateItem.downloadUrl.endsWith(".jpg"))
            }
        }
    }

    @Test
    fun testFourItemPostReview() {
        val fourItems = listOf(
            CarouselItem(url = "https://cdninstagram.com/img1.jpg", isVideo = false),
            CarouselItem(url = "https://cdninstagram.com/img2.jpg", isVideo = false),
            CarouselItem(url = "https://cdninstagram.com/vid3.mp4", isVideo = true),
            CarouselItem(url = "https://cdninstagram.com/img4.jpg", isVideo = false)
        )

        val media = MediaItem(
            shortcode = "DFmBvFkyXoY",
            originalUrl = "https://www.instagram.com/p/DFmBvFkyXoY/",
            mediaType = MediaType.CAROUSEL,
            title = "Trip Album",
            author = "traveler",
            downloadUrl = fourItems[0].url,
            thumbnailUrl = fourItems[0].url,
            carouselItems = fourItems,
            isVideo = false
        )

        assertEquals(4, media.carouselItems.size)
        assertFalse(media.carouselItems[0].isVideo)
        assertFalse(media.carouselItems[1].isVideo)
        assertTrue(media.carouselItems[2].isVideo)
        assertFalse(media.carouselItems[3].isVideo)
    }
}
