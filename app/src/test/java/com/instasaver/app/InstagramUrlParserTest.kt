package com.instasaver.app

import com.instasaver.app.data.local.MediaType
import com.instasaver.app.network.InstagramUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun testReelUrlParsing() {
        val url = "https://www.instagram.com/reel/C12345abc/?igsh=MWFqZzlnb3Q2"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("C12345abc", parsed?.shortcode)
        assertEquals(MediaType.REEL, parsed?.suspectedType)
        assertEquals("https://www.instagram.com/reel/C12345abc/", parsed?.cleanUrl)
    }

    @Test
    fun testReelsPluralUrlParsing() {
        val url = "https://www.instagram.com/reels/DA789xyz/"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("DA789xyz", parsed?.shortcode)
        assertEquals(MediaType.REEL, parsed?.suspectedType)
    }

    @Test
    fun testPostUrlParsing() {
        val url = "https://www.instagram.com/p/B999photo/?utm_source=ig_web_copy_link"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("B999photo", parsed?.shortcode)
        assertEquals("https://www.instagram.com/p/B999photo/", parsed?.cleanUrl)
    }

    @Test
    fun testTvUrlParsing() {
        val url = "https://www.instagram.com/tv/Cvideo123/"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("Cvideo123", parsed?.shortcode)
        assertEquals(MediaType.VIDEO, parsed?.suspectedType)
    }

    @Test
    fun testStoryUrlParsing() {
        val url = "https://www.instagram.com/stories/test_user/3216549871234567890/"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("3216549871234567890", parsed?.shortcode)
        assertEquals("test_user", parsed?.storyUsername)
        assertEquals(MediaType.STORY, parsed?.suspectedType)
    }

    @Test
    fun testSharedTextExtraction() {
        val shareSheetText = "Take a look at this clip! https://www.instagram.com/reel/C777reel/?igsh=123 shared via Instagram"
        val extracted = InstagramUrlParser.extractUrl(shareSheetText)

        assertNotNull(extracted)
        assertTrue(extracted!!.startsWith("https://www.instagram.com/reel/C777reel/"))

        val parsed = InstagramUrlParser.parse(shareSheetText)
        assertNotNull(parsed)
        assertEquals("C777reel", parsed?.shortcode)
        assertEquals(MediaType.REEL, parsed?.suspectedType)
    }

    @Test
    fun testYouTubeShortParsing() {
        val url = "https://youtube.com/shorts/jNQXAC9IVRw?si=123"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("jNQXAC9IVRw", parsed?.shortcode)
        assertEquals(com.instasaver.app.network.Platform.YOUTUBE, parsed?.platform)
        assertEquals("https://www.youtube.com/shorts/jNQXAC9IVRw", parsed?.cleanUrl)
    }

    @Test
    fun testFacebookReelParsing() {
        val url = "https://www.facebook.com/reel/1047466826966141/?mibextid=rSptEp"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("1047466826966141", parsed?.shortcode)
        assertEquals(com.instasaver.app.network.Platform.FACEBOOK, parsed?.platform)
    }

    @Test
    fun testXVideoParsing() {
        val url = "https://x.com/SpaceX/status/1859007421371511221"
        val parsed = InstagramUrlParser.parse(url)

        assertNotNull(parsed)
        assertEquals("1859007421371511221", parsed?.shortcode)
        assertEquals(com.instasaver.app.network.Platform.X_TWITTER, parsed?.platform)
    }

    @Test
    fun testInvalidUrl() {
        val invalid = "https://www.google.com"
        val parsed = InstagramUrlParser.parse(invalid)
        assertNull(parsed)
    }
}
