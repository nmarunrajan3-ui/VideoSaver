package com.instasaver.app.network

import android.content.Context
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.CarouselItem
import com.instasaver.app.data.model.MediaItem
import com.instasaver.app.data.model.ResolutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

class InstagramMediaResolver(private val context: Context) {

    private val client = NetworkClient.okHttpClient
    private val webViewResolver = WebViewMediaResolver(context)

    suspend fun resolve(rawInput: String): ResolutionResult = withContext(Dispatchers.IO) {
        val parsed = InstagramUrlParser.parse(rawInput)
            ?: return@withContext ResolutionResult.InvalidUrl()

        // Route by platform
        return@withContext when (parsed.platform) {
            Platform.YOUTUBE -> resolveYouTube(parsed)
            Platform.FACEBOOK -> resolveFacebook(parsed)
            Platform.X_TWITTER -> resolveX(parsed)
            Platform.INSTAGRAM -> resolveInstagram(parsed)
        }
    }

    private suspend fun resolveInstagram(parsed: ParsedMediaUrl): ResolutionResult {
        // Step 0: Ensure session cookies are populated from instagram.com
        ensureSessionInitialized()

        // Step 1: High-Speed Direct HTML & Polaris Media Extraction (~300ms)
        val htmlResult = tryHtmlScrape(parsed)
        if (htmlResult is ResolutionResult.Success) {
            return htmlResult
        }

        // Step 2: Try Media ID API (Official Instagram mobile endpoint)
        val isVideoSuspected = parsed.suspectedType == MediaType.REEL || parsed.suspectedType == MediaType.VIDEO
        val mediaIdResult = tryMediaIdApi(parsed, isVideoSuspected)
        if (mediaIdResult is ResolutionResult.Success) {
            return mediaIdResult
        }

        // Step 3: Try JSON endpoint with IG App ID
        val jsonResult = tryPublicJson(parsed, isVideoSuspected)
        if (jsonResult is ResolutionResult.Success) {
            return jsonResult
        }

        // Step 4: Try Embed endpoint
        val embedResult = tryEmbedEndpoint(parsed)
        if (embedResult is ResolutionResult.Success) {
            return embedResult
        }

        // Step 5: On-device Android WebView Video Sniffer fallback
        val webViewResult = webViewResolver.resolveWithWebView(
            url = parsed.cleanUrl,
            shortcode = parsed.shortcode,
            suspectedType = parsed.suspectedType,
            isVideoStrict = parsed.suspectedType == MediaType.REEL
        )
        if (webViewResult is ResolutionResult.Success) {
            return webViewResult
        }

        if (webViewResult is ResolutionResult.PrivateRestricted) {
            return webViewResult
        }

        return ResolutionResult.Error(
            "Video stream requires browser playback. Tap 'Force' to open the in-app Web Resolver and capture the media with audio."
        )
    }

    private suspend fun resolveYouTube(parsed: ParsedMediaUrl): ResolutionResult = withContext(Dispatchers.IO) {
        val videoId = parsed.shortcode
        val oEmbed = fetchYouTubeOEmbed(videoId)
        val title = oEmbed.title.ifEmpty { "YouTube Video ($videoId)" }
        val author = oEmbed.author.ifEmpty { "YouTube Creator" }
        val thumb = oEmbed.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        // Step 1: Extract VISITOR_DATA from watch page
        var visitorId: String? = null
        try {
            val watchReq = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId")
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(watchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    val matcher = Pattern.compile("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                    if (matcher.find()) {
                        visitorId = matcher.group(1)
                    }
                }
            }
        } catch (_: Exception) {}

        // Step 2: Query Innertube Player API with VISIONOS client
        try {
            val playerUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice17,1")
                        put("userAgent", ua)
                        put("osName", "visionOS")
                        put("osVersion", "26.5.23O471")
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val reqBuilder = Request.Builder()
                .url(playerUrl)
                .post(requestBody)
                .header("User-Agent", ua)
                .header("Content-Type", "application/json")
                .header("X-Youtube-Client-Name", "101")
                .header("X-Youtube-Client-Version", "1.02")
                .header("Origin", "https://www.youtube.com")

            if (!visitorId.isNullOrEmpty()) {
                reqBuilder.header("X-Goog-Visitor-Id", visitorId!!)
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val streamingData = json.optJSONObject("streamingData")
                    val hlsManifestUrl = streamingData?.optString("hlsManifestUrl", "") ?: ""

                    if (hlsManifestUrl.isNotBlank()) {
                        return@withContext ResolutionResult.Success(
                            MediaItem(
                                shortcode = videoId,
                                originalUrl = parsed.cleanUrl,
                                mediaType = MediaType.VIDEO,
                                title = title,
                                author = author,
                                downloadUrl = decodeHtml(hlsManifestUrl),
                                thumbnailUrl = thumb,
                                isVideo = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Step 3: Fallback to in-app WebView sniffer if needed
        val webViewResult = webViewResolver.resolveWithWebView(
            url = "https://m.youtube.com/watch?v=$videoId",
            shortcode = videoId,
            suspectedType = parsed.suspectedType,
            isVideoStrict = true
        )

        if (webViewResult is ResolutionResult.Success) {
            return@withContext ResolutionResult.Success(
                webViewResult.mediaItem.copy(
                    title = title,
                    author = author,
                    thumbnailUrl = thumb
                )
            )
        }

        return@withContext ResolutionResult.Error("Could not capture YouTube video stream. Tap 'Force' to open the in-app Web Resolver.")
    }

    private suspend fun resolveFacebook(parsed: ParsedMediaUrl): ResolutionResult {
        // Try direct HTML scrape
        val htmlRes = tryFacebookHtmlScrape(parsed)
        if (htmlRes is ResolutionResult.Success) return htmlRes

        // Fallback to in-app WebView sniffer
        val webViewResult = webViewResolver.resolveWithWebView(
            url = parsed.cleanUrl,
            shortcode = parsed.shortcode,
            suspectedType = parsed.suspectedType,
            isVideoStrict = true
        )

        if (webViewResult is ResolutionResult.Success) {
            return ResolutionResult.Success(
                webViewResult.mediaItem.copy(
                    title = "Facebook Video (${parsed.shortcode})",
                    author = "Facebook Creator"
                )
            )
        }

        return ResolutionResult.Error("Could not capture Facebook video stream.")
    }

    private suspend fun resolveX(parsed: ParsedMediaUrl): ResolutionResult {
        // Try direct syndication API
        val syndRes = tryXSyndicationScrape(parsed)
        if (syndRes is ResolutionResult.Success) return syndRes

        // Fallback to in-app WebView sniffer
        val webViewResult = webViewResolver.resolveWithWebView(
            url = parsed.cleanUrl,
            shortcode = parsed.shortcode,
            suspectedType = MediaType.VIDEO,
            isVideoStrict = true
        )

        if (webViewResult is ResolutionResult.Success) {
            return ResolutionResult.Success(
                webViewResult.mediaItem.copy(
                    title = "X Video (${parsed.shortcode})",
                    author = parsed.storyUsername ?: "X Creator"
                )
            )
        }

        return ResolutionResult.Error("Could not capture X video stream.")
    }

    /**
     * HTTP-only resolution (no WebView needed). Used in background workers where
     * WebView cannot run. Gets a fresh CDN URL from HTTP endpoints.
     */
    suspend fun resolveHttpOnly(rawInput: String): ResolutionResult = withContext(Dispatchers.IO) {
        val parsed = InstagramUrlParser.parse(rawInput)
            ?: return@withContext ResolutionResult.InvalidUrl()

        if (parsed.platform == Platform.FACEBOOK) {
            return@withContext tryFacebookHtmlScrape(parsed)
        }
        if (parsed.platform == Platform.X_TWITTER) {
            return@withContext tryXSyndicationScrape(parsed)
        }
        if (parsed.platform == Platform.YOUTUBE) {
            return@withContext resolveYouTube(parsed)
        }

        ensureSessionInitialized()

        val htmlResult = tryHtmlScrape(parsed)
        if (htmlResult is ResolutionResult.Success) return@withContext htmlResult

        val isVideoSuspected = parsed.suspectedType == MediaType.REEL || parsed.suspectedType == MediaType.VIDEO
        val mediaIdResult = tryMediaIdApi(parsed, isVideoSuspected)
        if (mediaIdResult is ResolutionResult.Success) return@withContext mediaIdResult

        val jsonResult = tryPublicJson(parsed, isVideoSuspected)
        if (jsonResult is ResolutionResult.Success) return@withContext jsonResult

        val embedResult = tryEmbedEndpoint(parsed)
        if (embedResult is ResolutionResult.Success) return@withContext embedResult

        return@withContext ResolutionResult.Error("Could not obtain a fresh download URL via HTTP.")
    }

    private fun ensureSessionInitialized() {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://www.instagram.com")
            if (cookies.isNullOrBlank() || !cookies.contains("mid")) {
                val initReq = Request.Builder()
                    .url("https://www.instagram.com/")
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Mode", "navigate")
                    .build()
                client.newCall(initReq).execute().close()
            }
        } catch (_: Exception) {}
    }

    private fun tryHtmlScrape(parsed: ParsedMediaUrl): ResolutionResult {
        // Step 1: For reels, query /reel/{shortcode}/ first; for photos/carousels query /p/{shortcode}/ first
        val primaryTargetUrl = when {
            parsed.suspectedType == MediaType.REEL -> "https://www.instagram.com/reel/${parsed.shortcode}/"
            parsed.suspectedType == MediaType.STORY -> parsed.cleanUrl
            else -> "https://www.instagram.com/p/${parsed.shortcode}/"
        }

        val primaryResult = scrapeInstagramHtml(primaryTargetUrl, parsed, isStrictVideo = parsed.suspectedType == MediaType.REEL)
        if (primaryResult is ResolutionResult.Success) {
            return primaryResult
        }

        // Step 2: Try alternate endpoint if first attempt didn't succeed
        if (parsed.suspectedType != MediaType.STORY) {
            val secondaryTargetUrl = if (primaryTargetUrl.contains("/p/")) {
                "https://www.instagram.com/reel/${parsed.shortcode}/"
            } else {
                "https://www.instagram.com/p/${parsed.shortcode}/"
            }
            val secondaryResult = scrapeInstagramHtml(secondaryTargetUrl, parsed, isStrictVideo = false)
            if (secondaryResult is ResolutionResult.Success) {
                return secondaryResult
            }
        }

        return primaryResult
    }

    private fun scrapeInstagramHtml(targetUrl: String, parsed: ParsedMediaUrl, isStrictVideo: Boolean): ResolutionResult {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.instagram.com/")
            .header("Sec-Fetch-Mode", "navigate")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolutionResult.Error("HTTP ${response.code}")
                val html = response.body?.string() ?: return ResolutionResult.Error("Empty page response")
                if (html.contains("This Account is Private") && !html.contains("video_versions") && !html.contains("display_uri")) {
                    return ResolutionResult.PrivateRestricted(parsed.shortcode)
                }

                // 1. Extract progressive MP4 video stream with stereo AAC audio
                val videoUrl = extractVideoUrlFromHtml(html)

                // 2. Extract best image/thumbnail
                val imageUrl = extractImageUrlFromHtml(html)

                // 3. Extract title and author
                val title = extractTitleFromHtml(html, parsed.shortcode)
                val author = extractAuthorFromHtml(html)

                // 4. Extract carousel items
                val carouselItems = extractCarouselItemsFromHtml(html)

                // If Reel was strictly requested, require a video stream
                if (isStrictVideo && videoUrl.isNullOrEmpty()) {
                    return ResolutionResult.Error("No progressive video stream found for Reel")
                }

                // If Story was requested, DO NOT accept avatar or profile picture as a story!
                if (parsed.suspectedType == MediaType.STORY) {
                    if (videoUrl.isNullOrEmpty() && (imageUrl.isNullOrEmpty() || isAvatarOrThumbnail(imageUrl))) {
                        return ResolutionResult.Error("Instagram Stories and Highlights require viewing session. Opening Web Sniffer to capture all active stories.")
                    }
                }

                val hasVideo = !videoUrl.isNullOrEmpty()
                val finalDownloadUrl = if (hasVideo) videoUrl!! else (imageUrl ?: "")

                if (finalDownloadUrl.isEmpty()) {
                    return ResolutionResult.Error("Could not extract media download URL from page")
                }

                val resolvedType = when {
                    parsed.suspectedType == MediaType.REEL -> MediaType.REEL
                    parsed.suspectedType == MediaType.STORY -> MediaType.STORY
                    carouselItems.isNotEmpty() -> MediaType.CAROUSEL
                    hasVideo -> MediaType.VIDEO
                    else -> MediaType.PHOTO
                }

                ResolutionResult.Success(
                    MediaItem(
                        shortcode = parsed.shortcode,
                        originalUrl = parsed.cleanUrl,
                        mediaType = resolvedType,
                        title = title.ifEmpty { "Instagram ${resolvedType.displayName} ($author)" },
                        author = author,
                        downloadUrl = decodeHtml(finalDownloadUrl),
                        thumbnailUrl = imageUrl?.let { decodeHtml(it) },
                        carouselItems = carouselItems,
                        isVideo = hasVideo
                    )
                )
            }
        } catch (e: Exception) {
            ResolutionResult.Error("Page request failed: ${e.message}")
        }
    }

    private fun tryFacebookHtmlScrape(parsed: ParsedMediaUrl): ResolutionResult {
        val request = Request.Builder()
            .url(parsed.cleanUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolutionResult.Error("FB HTTP ${response.code}")
                val html = response.body?.string() ?: return ResolutionResult.Error("Empty FB HTML")
                val patterns = listOf(
                    Pattern.compile("\"browser_native_hd_url\"\\s*:\\s*\"([^\"]+)\""),
                    Pattern.compile("\"playable_url_quality_hd\"\\s*:\\s*\"([^\"]+)\""),
                    Pattern.compile("\"browser_native_sd_url\"\\s*:\\s*\"([^\"]+)\""),
                    Pattern.compile("\"playable_url\"\\s*:\\s*\"([^\"]+)\""),
                    Pattern.compile("<meta\\s+(?:property|name)=\"og:video\"\\s+content=\"([^\"]+)\"")
                )
                var videoUrl: String? = null
                for (pat in patterns) {
                    val m = pat.matcher(html)
                    if (m.find()) {
                        videoUrl = decodeHtml(m.group(1) ?: "")
                        break
                    }
                }
                val ogTitle = extractMetaTag(html, "og:title") ?: "Facebook Video"
                val ogImage = extractMetaTag(html, "og:image")
                if (!videoUrl.isNullOrEmpty()) {
                    ResolutionResult.Success(
                        MediaItem(
                            shortcode = parsed.shortcode,
                            originalUrl = parsed.cleanUrl,
                            mediaType = MediaType.VIDEO,
                            title = ogTitle,
                            author = "Facebook Creator",
                            downloadUrl = videoUrl,
                            thumbnailUrl = ogImage?.let { decodeHtml(it) },
                            isVideo = true
                        )
                    )
                } else {
                    ResolutionResult.Error("No direct Facebook video found in HTML")
                }
            }
        } catch (e: Exception) {
            ResolutionResult.Error("FB Scrape failed: ${e.message}")
        }
    }

    private fun tryXSyndicationScrape(parsed: ParsedMediaUrl): ResolutionResult {
        val twid = parsed.shortcode
        val token = generateSyndicationToken(twid)
        val syndUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$twid&token=$token"
        val request = Request.Builder()
            .url(syndUrl)
            .header("User-Agent", "Googlebot")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val text = root.optString("text", "X Video")
                    val userObj = root.optJSONObject("user")
                    val authorName = userObj?.optString("name") ?: userObj?.optString("screen_name") ?: parsed.storyUsername ?: "X Creator"
                    val mediaDetails = root.optJSONArray("mediaDetails")
                    if (mediaDetails != null && mediaDetails.length() > 0) {
                        for (i in 0 until mediaDetails.length()) {
                            val m = mediaDetails.getJSONObject(i)
                            val vInfo = m.optJSONObject("video_info")
                            val variants = vInfo?.optJSONArray("variants")
                            if (variants != null) {
                                var bestUrl: String? = null
                                var maxBitrate = -1
                                for (j in 0 until variants.length()) {
                                    val v = variants.getJSONObject(j)
                                    val cType = v.optString("content_type", "")
                                    if (cType.contains("video/mp4")) {
                                        val br = v.optInt("bitrate", 0)
                                        if (br >= maxBitrate) {
                                            maxBitrate = br
                                            bestUrl = v.optString("url")
                                        }
                                    }
                                }
                                if (!bestUrl.isNullOrEmpty()) {
                                    val poster = m.optString("media_url_https", "")
                                    return ResolutionResult.Success(
                                        MediaItem(
                                            shortcode = parsed.shortcode,
                                            originalUrl = parsed.cleanUrl,
                                            mediaType = MediaType.VIDEO,
                                            title = text.take(120),
                                            author = authorName,
                                            downloadUrl = bestUrl,
                                            thumbnailUrl = poster.ifEmpty { null },
                                            isVideo = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                ResolutionResult.Error("No direct video from X syndication")
            }
        } catch (e: Exception) {
            ResolutionResult.Error("X syndication failed: ${e.message}")
        }
    }

    private fun generateSyndicationToken(twid: String): String {
        return try {
            val num = (twid.toDouble() / 1e15) * Math.PI
            java.lang.Long.toString(num.toLong(), 36).replace("0", "")
        } catch (_: Exception) {
            "token"
        }
    }

    private data class YouTubeOEmbed(val title: String, val author: String, val thumbnailUrl: String?)

    private fun fetchYouTubeOEmbed(videoId: String): YouTubeOEmbed {
        val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
        val req = Request.Builder()
            .url(oEmbedUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    YouTubeOEmbed(
                        title = json.optString("title", ""),
                        author = json.optString("author_name", ""),
                        thumbnailUrl = json.optString("thumbnail_url", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                    )
                } else {
                    YouTubeOEmbed("", "", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                }
            }
        } catch (_: Exception) {
            YouTubeOEmbed("", "", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        }
    }

    private fun extractVideoUrlFromHtml(html: String): String? {
        // Strategy A: Direct video_versions JSON array (highest quality progressive MP4 with audio)
        val vvPattern = Pattern.compile("\"video_versions\"\\s*:\\s*(\\[[^\\]]+\\])")
        val vvMatcher = vvPattern.matcher(html)
        if (vvMatcher.find()) {
            val jsonArrayStr = vvMatcher.group(1)
            if (!jsonArrayStr.isNullOrEmpty()) {
                try {
                    val array = org.json.JSONArray(jsonArrayStr)
                    var bestUrl: String? = null
                    var maxArea = -1
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val u = item.optString("url", "")
                        if (u.isNotEmpty()) {
                            val w = item.optInt("width", 0)
                            val h = item.optInt("height", 0)
                            val area = w * h
                            if (area > maxArea) {
                                maxArea = area
                                bestUrl = u
                            }
                        }
                    }
                    if (!bestUrl.isNullOrEmpty()) {
                        return decodeHtml(bestUrl)
                    }
                } catch (_: Exception) {}
            }
        }

        // Strategy B: DASH manifest unescaped BaseURL tags
        val unescaped = html.replace("\\u003C", "<").replace("\\u003c", "<")
            .replace("\\u003E", ">").replace("\\u003e", ">")
            .replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&")
        val basePattern = Pattern.compile("<BaseURL>(https?://[^<>]+\\.mp4[^<>]*)", Pattern.CASE_INSENSITIVE)
        val baseMatcher = basePattern.matcher(unescaped)
        if (baseMatcher.find()) {
            val url = baseMatcher.group(1)
            if (!url.isNullOrEmpty()) {
                return url
            }
        }

        // Strategy C: OpenGraph video tags
        val ogVideo = extractMetaTag(html, "og:video") ?: extractMetaTag(html, "og:video:secure_url")
        if (!ogVideo.isNullOrEmpty()) {
            return decodeHtml(ogVideo)
        }

        // Strategy D: Direct signed CDN MP4 regex
        val cdnPattern = Pattern.compile("\"(https?:(?:\\\\/\\\\/|//)[a-zA-Z0-9_\\-\\.]+(?:cdninstagram|fbcdn)[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*)\"")
        val cdnMatcher = cdnPattern.matcher(html)
        if (cdnMatcher.find()) {
            val url = cdnMatcher.group(1)
            if (!url.isNullOrEmpty()) {
                return decodeHtml(url)
            }
        }

        return null
    }

    private fun extractImageUrlFromHtml(html: String): String? {
        // Strategy A: display_uri or display_url in JSON
        val displayPattern = Pattern.compile("\"(?:display_uri|display_url)\"\\s*:\\s*\"([^\"]+)\"")
        val displayMatcher = displayPattern.matcher(html)
        if (displayMatcher.find()) {
            val url = displayMatcher.group(1)
            if (!url.isNullOrEmpty()) {
                return decodeHtml(url)
            }
        }

        // Strategy B: OpenGraph image
        val ogImage = extractMetaTag(html, "og:image")
        if (!ogImage.isNullOrEmpty()) {
            return decodeHtml(ogImage)
        }

        // Strategy C: image_versions2 candidates
        val candPattern = Pattern.compile("\"candidates\"\\s*:\\s*\\[\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
        val candMatcher = candPattern.matcher(html)
        if (candMatcher.find()) {
            val url = candMatcher.group(1)
            if (!url.isNullOrEmpty()) {
                return decodeHtml(url)
            }
        }

        return null
    }

    private fun extractCarouselItemsFromHtml(html: String): List<CarouselItem> {
        val list = mutableListOf<CarouselItem>()
        try {
            // Strategy 1: Find carousel_media array
            val cmIdx = html.indexOf("\"carousel_media\"")
            if (cmIdx != -1) {
                val arrayStr = extractJsonArrayStartingAt(html, cmIdx)
                if (!arrayStr.isNullOrEmpty()) {
                    val array = org.json.JSONArray(arrayStr)
                    for (i in 0 until array.length()) {
                        val m = array.optJSONObject(i) ?: continue
                        val isVid = m.optInt("media_type", 0) == 2 || m.has("video_versions")
                        val vUrl = getBestVideoVersion(m)
                        val dUrl = getBestImageVersion(m) ?: m.optString("display_url", "")
                        val finalUrl = if (isVid && !vUrl.isNullOrEmpty()) vUrl else dUrl
                        if (finalUrl.isNotEmpty()) {
                            list.add(CarouselItem(url = decodeHtml(finalUrl), isVideo = isVid && !vUrl.isNullOrEmpty(), thumbnailUrl = decodeHtml(dUrl)))
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            }

            // Strategy 2: Find edge_sidecar_to_children edges array
            val edgeIdx = html.indexOf("\"edge_sidecar_to_children\"")
            if (edgeIdx != -1) {
                val edgesKeyIdx = html.indexOf("\"edges\"", edgeIdx)
                if (edgesKeyIdx != -1) {
                    val arrayStr = extractJsonArrayStartingAt(html, edgesKeyIdx)
                    if (!arrayStr.isNullOrEmpty()) {
                        val array = org.json.JSONArray(arrayStr)
                        for (i in 0 until array.length()) {
                            val node = array.optJSONObject(i)?.optJSONObject("node") ?: continue
                            val isVid = node.optBoolean("is_video", false)
                            val vUrl = node.optString("video_url", "")
                            val dUrl = node.optString("display_url", "")
                            val url = if (isVid && vUrl.isNotEmpty()) vUrl else dUrl
                            if (url.isNotEmpty()) {
                                list.add(CarouselItem(url = decodeHtml(url), isVideo = isVid, thumbnailUrl = decodeHtml(dUrl)))
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun extractJsonArrayStartingAt(str: String, startIndex: Int): String? {
        val startBracket = str.indexOf('[', startIndex)
        if (startBracket == -1) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in startBracket until str.length) {
            val c = str[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        return str.substring(startBracket, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractTitleFromHtml(html: String, shortcode: String): String {
        val captionPattern = Pattern.compile("\"caption\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val captionMatcher = captionPattern.matcher(html)
        if (captionMatcher.find()) {
            val text = captionMatcher.group(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
            if (!text.isNullOrBlank()) {
                return text.take(120)
            }
        }

        val ogDesc = extractMetaTag(html, "og:description")
        if (!ogDesc.isNullOrBlank()) {
            return ogDesc.take(120)
        }

        val ogTitle = extractMetaTag(html, "og:title")
        if (!ogTitle.isNullOrBlank()) {
            return ogTitle.take(120)
        }

        return "Instagram Media ($shortcode)"
    }

    private fun extractAuthorFromHtml(html: String): String {
        val userPattern = Pattern.compile("\"user\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"([a-zA-Z0-9_\\.]+)\"")
        val userMatcher = userPattern.matcher(html)
        if (userMatcher.find()) {
            val username = userMatcher.group(1)
            if (!username.isNullOrBlank()) return username
        }

        val ownerPattern = Pattern.compile("\"owner\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"([a-zA-Z0-9_\\.]+)\"")
        val ownerMatcher = ownerPattern.matcher(html)
        if (ownerMatcher.find()) {
            val username = ownerMatcher.group(1)
            if (!username.isNullOrBlank()) return username
        }

        val ogTitle = extractMetaTag(html, "og:title")
        if (!ogTitle.isNullOrBlank()) {
            return extractAuthorFromTitle(ogTitle)
        }

        return "Instagram Creator"
    }

    private fun shortcodeToMediaId(code: String): Long {
        val short = if (code.length > 28) code.dropLast(28) else code
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var id = 0L
        for (char in short) {
            val index = table.indexOf(char)
            if (index != -1) {
                id = id * 64 + index
            }
        }
        return id
    }

    private fun tryMediaIdApi(parsed: ParsedMediaUrl, isVideoSuspected: Boolean): ResolutionResult {
        val mediaId = shortcodeToMediaId(parsed.shortcode)
        if (mediaId <= 0L) return ResolutionResult.Error("Invalid shortcode conversion")

        val url = "https://www.instagram.com/api/v1/media/$mediaId/info/"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("X-IG-App-ID", "936619743392459")
            .header("X-ASBD-ID", "359341")
            .header("X-IG-WWW-Claim", "0")
            .header("Origin", "https://www.instagram.com")
            .header("Referer", "https://www.instagram.com/")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolutionResult.Error("Media ID API HTTP ${response.code}")
                val body = response.body?.string() ?: return ResolutionResult.Error("Empty body")
                parseInstagramJson(body, parsed, isVideoSuspected)
            }
        } catch (e: Exception) {
            ResolutionResult.Error("Media ID API failed: ${e.message}")
        }
    }

    private fun tryPublicJson(parsed: ParsedMediaUrl, isVideoSuspected: Boolean): ResolutionResult {
        val url = "https://www.instagram.com/p/${parsed.shortcode}/?__a=1&__d=dis"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("X-IG-App-ID", "936619743392459")
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolutionResult.Error("HTTP Error: ${response.code}")
                val body = response.body?.string() ?: return ResolutionResult.Error("Empty response")
                if (body.contains("Login • Instagram") || body.contains("login_required")) {
                    return ResolutionResult.Error("Login required")
                }
                parseInstagramJson(body, parsed, isVideoSuspected)
            }
        } catch (e: Exception) {
            ResolutionResult.Error(e.message ?: "Failed to query Instagram API")
        }
    }

    private fun parseInstagramJson(jsonString: String, parsed: ParsedMediaUrl, isVideoSuspected: Boolean): ResolutionResult {
        return try {
            val root = JSONObject(jsonString)

            val mediaObj = when {
                root.has("graphql") -> root.getJSONObject("graphql").optJSONObject("shortcode_media")
                root.has("items") && root.getJSONArray("items").length() > 0 -> root.getJSONArray("items").getJSONObject(0)
                else -> null
            } ?: return ResolutionResult.Error("Unsupported JSON response format")

            val isVideo = mediaObj.optBoolean("is_video", false) ||
                    mediaObj.optInt("media_type", 0) == 2 ||
                    isVideoSuspected

            val videoUrl = getBestVideoVersion(mediaObj)
                ?: if (mediaObj.has("video_url")) mediaObj.optString("video_url") else null

            val displayUrl = if (mediaObj.has("display_url")) mediaObj.optString("display_url") else (getBestImageVersion(mediaObj) ?: "")

            val owner = mediaObj.optJSONObject("owner")
            val author = owner?.optString("username", "") ?: owner?.optString("full_name", "") ?: ""

            val caption = extractCaption(mediaObj)
            val carouselItems = extractCarouselItems(mediaObj)

            val isStrictReel = parsed.suspectedType == MediaType.REEL
            if (isStrictReel && videoUrl.isNullOrEmpty()) {
                return ResolutionResult.Error("No video stream found in JSON data")
            }

            val finalDownloadUrl = if (isVideo && !videoUrl.isNullOrEmpty()) videoUrl else displayUrl

            if (finalDownloadUrl.isEmpty()) {
                return ResolutionResult.Error("No valid media stream URL found")
            }

            val resolvedType = when {
                parsed.suspectedType == MediaType.REEL -> MediaType.REEL
                carouselItems.isNotEmpty() -> MediaType.CAROUSEL
                isVideo && !videoUrl.isNullOrEmpty() -> MediaType.VIDEO
                else -> MediaType.PHOTO
            }

            ResolutionResult.Success(
                MediaItem(
                    shortcode = parsed.shortcode,
                    originalUrl = parsed.cleanUrl,
                    mediaType = resolvedType,
                    title = caption.ifEmpty { "Instagram ${resolvedType.displayName} by $author" },
                    author = author,
                    downloadUrl = finalDownloadUrl,
                    thumbnailUrl = displayUrl.ifEmpty { null },
                    carouselItems = carouselItems,
                    isVideo = isVideo && !videoUrl.isNullOrEmpty()
                )
            )
        } catch (e: Exception) {
            ResolutionResult.Error("Error parsing Instagram media data: ${e.message}")
        }
    }

    private fun tryEmbedEndpoint(parsed: ParsedMediaUrl): ResolutionResult {
        val embedUrl = "https://www.instagram.com/p/${parsed.shortcode}/embed/captioned/"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolutionResult.Error("Embed HTTP ${response.code}")
                val html = response.body?.string() ?: return ResolutionResult.Error("Empty embed HTML")

                // NOTE: Embed HTML intentionally serves MUTED videos (<video class="EmbeddedMediaVideo">).
                // Downloading muted videos breaks user experience. We only extract EmbeddedMediaImage for static photos!
                val imgPattern = Pattern.compile("<img\\s+class=[\"']EmbeddedMediaImage[\"'][^>]*src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val imgMatcher = imgPattern.matcher(html)
                val directImg = if (imgMatcher.find()) decodeHtml(imgMatcher.group(1) ?: "") else null

                if (directImg.isNullOrEmpty()) {
                    return ResolutionResult.Error("No photo found in embed")
                }

                ResolutionResult.Success(
                    MediaItem(
                        shortcode = parsed.shortcode,
                        originalUrl = parsed.cleanUrl,
                        mediaType = MediaType.PHOTO,
                        title = "Instagram Photo (${parsed.shortcode})",
                        author = "Instagram Creator",
                        downloadUrl = directImg,
                        thumbnailUrl = directImg,
                        isVideo = false
                    )
                )
            }
        } catch (e: Exception) {
            ResolutionResult.Error("Embed request failed: ${e.message}")
        }
    }

    private fun findMp4InHtml(html: String): String? {
        val pattern = Pattern.compile("(https?:(?:\\\\/\\\\/|//)[^\"'\\s<>]+(?:\\.mp4|bytestart)[^\"'\\s<>]*)")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val raw = matcher.group(1) ?: return null
            return decodeHtml(raw)
        }
        return null
    }

    private fun extractMetaTag(html: String, property: String): String? {
        val pattern = Pattern.compile("<meta\\s+(?:property|name)=[\"']$property[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) return matcher.group(1)
        val altPattern = Pattern.compile("<meta\\s+content=[\"']([^\"']+)[\"']\\s+(?:property|name)=[\"']$property[\"']", Pattern.CASE_INSENSITIVE)
        val altMatcher = altPattern.matcher(html)
        return if (altMatcher.find()) altMatcher.group(1) else null
    }

    private fun extractCaption(mediaObj: JSONObject): String {
        return try {
            val edgeCaption = mediaObj.optJSONObject("edge_media_to_caption")
            val edges = edgeCaption?.optJSONArray("edges")
            if (edges != null && edges.length() > 0) {
                edges.getJSONObject(0).getJSONObject("node").optString("text", "")
            } else {
                mediaObj.optJSONObject("caption")?.optString("text", "") ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractCarouselItems(mediaObj: JSONObject): List<CarouselItem> {
        val items = mutableListOf<CarouselItem>()
        try {
            // Check 1: carousel_media (Standard Instagram JSON)
            val cmArray = mediaObj.optJSONArray("carousel_media")
            if (cmArray != null && cmArray.length() > 0) {
                for (i in 0 until cmArray.length()) {
                    val m = cmArray.optJSONObject(i) ?: continue
                    val isVid = m.optInt("media_type", 0) == 2 || m.has("video_versions")
                    val vUrl = getBestVideoVersion(m)
                    val dUrl = getBestImageVersion(m) ?: m.optString("display_url", "")
                    val finalUrl = if (isVid && !vUrl.isNullOrEmpty()) vUrl else dUrl
                    if (finalUrl.isNotEmpty()) {
                        items.add(CarouselItem(url = decodeHtml(finalUrl), isVideo = isVid && !vUrl.isNullOrEmpty(), thumbnailUrl = decodeHtml(dUrl)))
                    }
                }
                if (items.isNotEmpty()) return items
            }

            // Check 2: edge_sidecar_to_children (GraphQL format)
            val sidecar = mediaObj.optJSONObject("edge_sidecar_to_children")
            val edges = sidecar?.optJSONArray("edges")
            if (edges != null && edges.length() > 0) {
                for (i in 0 until edges.length()) {
                    val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                    val isVideo = node.optBoolean("is_video", false)
                    val videoUrl = if (node.has("video_url")) node.optString("video_url") else null
                    val displayUrl = node.optString("display_url", "")
                    val url = if (isVideo && !videoUrl.isNullOrEmpty()) videoUrl else displayUrl
                    if (url.isNotEmpty()) {
                        items.add(CarouselItem(url = decodeHtml(url), isVideo = isVideo && !videoUrl.isNullOrEmpty(), thumbnailUrl = decodeHtml(displayUrl)))
                    }
                }
            }
        } catch (_: Exception) {}
        return items
    }

    private fun getBestVideoVersion(mediaObj: JSONObject): String? {
        val videoVersions = mediaObj.optJSONArray("video_versions") ?: return null
        var bestUrl: String? = null
        var maxArea = -1

        for (i in 0 until videoVersions.length()) {
            val v = videoVersions.optJSONObject(i) ?: continue
            val url = v.optString("url", "")
            if (url.isNotEmpty()) {
                val width = v.optInt("width", 0)
                val height = v.optInt("height", 0)
                val area = width * height
                if (area > maxArea) {
                    maxArea = area
                    bestUrl = url
                }
            }
        }
        return bestUrl
    }

    private fun getBestImageVersion(mediaObj: JSONObject): String? {
        val imageVersions = mediaObj.optJSONObject("image_versions2")
        val candidates = imageVersions?.optJSONArray("candidates") ?: return null
        if (candidates.length() > 0) {
            val url = candidates.getJSONObject(0).optString("url")
            return if (url.isNotEmpty()) url else null
        }
        return null
    }

    private fun extractAuthorFromTitle(title: String): String {
        val pattern = Pattern.compile("on Instagram:\\s*\"?(.*?)\"?$")
        val matcher = pattern.matcher(title)
        if (matcher.find()) {
            val authorPart = title.substringBefore("on Instagram:").trim()
            val clean = authorPart.removePrefix("Photos from").removePrefix("Video by").trim()
            return clean.ifEmpty { "Instagram user" }
        }
        return "Instagram user"
    }

    private fun isAvatarOrThumbnail(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("s150x150") ||
                url.contains("p50x50") ||
                url.contains("150x150") ||
                url.contains("profile_pic") ||
                url.contains("44884218_345707102883719")
    }

    private fun decodeHtml(url: String): String {
        return url.replace("&amp;", "&")
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\u003C", "<")
            .replace("\\u003c", "<")
            .replace("\\u003E", ">")
            .replace("\\u003e", ">")
    }
}
