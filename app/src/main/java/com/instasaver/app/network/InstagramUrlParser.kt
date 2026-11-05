package com.instasaver.app.network

import com.instasaver.app.data.local.MediaType
import java.util.regex.Pattern

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    X_TWITTER("X / Twitter")
}

data class ParsedMediaUrl(
    val cleanUrl: String,
    val shortcode: String,
    val suspectedType: MediaType,
    val platform: Platform = Platform.INSTAGRAM,
    val storyUsername: String? = null,
    val storyId: String? = null
)

typealias ParsedInstagramUrl = ParsedMediaUrl

object InstagramUrlParser {

    private val GENERIC_URL_REGEX = Pattern.compile("(https?://[^\\s]+)")

    private val IG_HIGHLIGHT_PATTERN = Pattern.compile("instagram\\.com/stories/highlights/([0-9]+)")
    private val IG_STORY_ITEM_PATTERN = Pattern.compile("instagram\\.com/stories/([A-Za-z0-9_.-]+)/([0-9]+)")
    private val IG_STORY_USER_PATTERN = Pattern.compile("instagram\\.com/stories/([A-Za-z0-9_.-]+)")
    private val IG_REEL_PATTERN = Pattern.compile("instagram\\.com/(?:reel|reels)/([A-Za-z0-9_-]+)")
    private val IG_POST_PATTERN = Pattern.compile("instagram\\.com/p/([A-Za-z0-9_-]+)")
    private val IG_TV_PATTERN = Pattern.compile("instagram\\.com/tv/([A-Za-z0-9_-]+)")

    private val YT_PATTERN = Pattern.compile("(?:youtube\\.com/shorts/|youtu\\.be/|youtube\\.com/watch\\?v=)([A-Za-z0-9_-]{11})")
    private val FB_PATTERN = Pattern.compile("(?:facebook\\.com/(?:reel/|watch/\\?v=|.*?/videos/)|fb\\.watch/)([0-9A-Za-z_-]+)")
    private val X_PATTERN = Pattern.compile("(?:twitter\\.com|x\\.com)/([A-Za-z0-9_]+)/status/([0-9]+)")

    /**
     * Extracts an authorized URL from raw input (which may contain extra text from Android Share sheet).
     */
    fun extractUrl(text: String): String? {
        val matcher = GENERIC_URL_REGEX.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trimEnd('.', ',', ';', ')', ']')
        } else {
            null
        }
    }

    /**
     * Parses the media URL into a structured format.
     */
    fun parse(input: String): ParsedMediaUrl? {
        val extracted = extractUrl(input) ?: input.trim()

        // 1. Check Instagram Story Highlights
        val hlMatcher = IG_HIGHLIGHT_PATTERN.matcher(extracted)
        if (hlMatcher.find()) {
            val hlId = hlMatcher.group(1) ?: ""
            val clean = "https://www.instagram.com/stories/highlights/$hlId/"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = hlId,
                suspectedType = MediaType.STORY,
                platform = Platform.INSTAGRAM,
                storyUsername = "highlights",
                storyId = hlId
            )
        }

        // 2. Check Specific Instagram Story Item (User + Story ID)
        val storyItemMatcher = IG_STORY_ITEM_PATTERN.matcher(extracted)
        if (storyItemMatcher.find()) {
            val username = storyItemMatcher.group(1) ?: ""
            val storyId = storyItemMatcher.group(2) ?: ""
            if (username != "highlights") {
                val clean = "https://www.instagram.com/stories/$username/$storyId/"
                return ParsedMediaUrl(
                    cleanUrl = clean,
                    shortcode = storyId,
                    suspectedType = MediaType.STORY,
                    platform = Platform.INSTAGRAM,
                    storyUsername = username,
                    storyId = storyId
                )
            }
        }

        // 3. Check Instagram User Stories (All active stories of user)
        val storyUserMatcher = IG_STORY_USER_PATTERN.matcher(extracted)
        if (storyUserMatcher.find()) {
            val username = storyUserMatcher.group(1) ?: ""
            if (username != "highlights") {
                val clean = "https://www.instagram.com/stories/$username/"
                return ParsedMediaUrl(
                    cleanUrl = clean,
                    shortcode = username,
                    suspectedType = MediaType.STORY,
                    platform = Platform.INSTAGRAM,
                    storyUsername = username,
                    storyId = null
                )
            }
        }

        // 2. Check Instagram Reel
        val reelMatcher = IG_REEL_PATTERN.matcher(extracted)
        if (reelMatcher.find()) {
            val code = reelMatcher.group(1) ?: return null
            val clean = "https://www.instagram.com/reel/$code/"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = code,
                suspectedType = MediaType.REEL,
                platform = Platform.INSTAGRAM
            )
        }

        // 3. Check Instagram TV
        val tvMatcher = IG_TV_PATTERN.matcher(extracted)
        if (tvMatcher.find()) {
            val code = tvMatcher.group(1) ?: return null
            val clean = "https://www.instagram.com/tv/$code/"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = code,
                suspectedType = MediaType.VIDEO,
                platform = Platform.INSTAGRAM
            )
        }

        // 4. Check Instagram Post (Photo / Video / Carousel)
        val postMatcher = IG_POST_PATTERN.matcher(extracted)
        if (postMatcher.find()) {
            val code = postMatcher.group(1) ?: return null
            val clean = "https://www.instagram.com/p/$code/"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = code,
                suspectedType = MediaType.PHOTO, // Defaults to photo, resolved to video or carousel during fetch
                platform = Platform.INSTAGRAM
            )
        }

        // 5. Check YouTube (Shorts or Videos)
        val ytMatcher = YT_PATTERN.matcher(extracted)
        if (ytMatcher.find()) {
            val code = ytMatcher.group(1) ?: return null
            val isShort = extracted.contains("/shorts/")
            val clean = if (isShort) "https://www.youtube.com/shorts/$code" else "https://www.youtube.com/watch?v=$code"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = code,
                suspectedType = if (isShort) MediaType.REEL else MediaType.VIDEO,
                platform = Platform.YOUTUBE
            )
        }

        // 6. Check Facebook (Reel or Video)
        val fbMatcher = FB_PATTERN.matcher(extracted)
        if (fbMatcher.find()) {
            val code = fbMatcher.group(1) ?: return null
            val isReel = extracted.contains("/reel/")
            val clean = if (isReel) "https://www.facebook.com/reel/$code" else "https://www.facebook.com/watch/?v=$code"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = code,
                suspectedType = if (isReel) MediaType.REEL else MediaType.VIDEO,
                platform = Platform.FACEBOOK
            )
        }

        // 7. Check X / Twitter
        val xMatcher = X_PATTERN.matcher(extracted)
        if (xMatcher.find()) {
            val author = xMatcher.group(1) ?: ""
            val tweetId = xMatcher.group(2) ?: return null
            val clean = "https://x.com/$author/status/$tweetId"
            return ParsedMediaUrl(
                cleanUrl = clean,
                shortcode = tweetId,
                suspectedType = MediaType.VIDEO,
                platform = Platform.X_TWITTER,
                storyUsername = author
            )
        }

        return null
    }

    fun isSupportedUrl(input: String): Boolean {
        return parse(input) != null
    }

    fun isInstagramUrl(input: String): Boolean {
        return parse(input)?.platform == Platform.INSTAGRAM
    }
}
