package com.instasaver.app.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.CarouselItem
import com.instasaver.app.data.model.MediaItem
import com.instasaver.app.data.model.ResolutionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Collections

class WebViewMediaResolver(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Resolves an Instagram post/reel/carousel URL by rendering it in a headless WebView.
     * Intercepts both video and photo streams, inspects React Fiber for all carousel items,
     * steps through carousel slides with Next button/arrow keys, and collects all items (3, 4, 10+).
     */
    suspend fun resolveWithWebView(
        url: String,
        shortcode: String,
        suspectedType: MediaType,
        isVideoStrict: Boolean = false,
        timeoutMs: Long = 10000L
    ): ResolutionResult = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ResolutionResult>()
        val capturedItems = Collections.synchronizedList(mutableListOf<CarouselItem>())
        var webView: WebView? = null

        fun completeWithCaptured(): Boolean {
            if (deferred.isActive && capturedItems.isNotEmpty()) {
                val list = synchronized(capturedItems) { capturedItems.toList() }
                val first = list[0]
                val isMulti = list.size > 1
                val resolvedType = when {
                    suspectedType == MediaType.REEL -> MediaType.REEL
                    suspectedType == MediaType.STORY -> MediaType.STORY
                    isMulti -> MediaType.CAROUSEL
                    first.isVideo -> MediaType.VIDEO
                    else -> MediaType.PHOTO
                }
                val title = if (isMulti) {
                    if (suspectedType == MediaType.STORY) "Instagram Stories (${list.size})" else "Instagram Post (${list.size} Items)"
                } else if (first.isVideo) {
                    "Instagram Video ($shortcode)"
                } else {
                    "Instagram Photo ($shortcode)"
                }

                deferred.complete(
                    ResolutionResult.Success(
                        MediaItem(
                            shortcode = shortcode,
                            originalUrl = url,
                            mediaType = resolvedType,
                            title = title,
                            author = "Instagram Creator",
                            downloadUrl = first.url,
                            thumbnailUrl = first.thumbnailUrl ?: first.url,
                            carouselItems = if (isMulti) list else emptyList(),
                            isVideo = first.isVideo
                        )
                    )
                )
                return true
            }
            return false
        }

        try {
            webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = NetworkClient.USER_AGENT

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val reqUri = request?.url ?: return false
                        val urlStr = reqUri.toString()
                        val scheme = reqUri.scheme?.lowercase() ?: ""

                        if (urlStr.startsWith("intent://") || urlStr.startsWith("instagram://") || (scheme != "http" && scheme != "https")) {
                            if (urlStr.contains("applink.instagram.com") || urlStr.contains("instagram.com")) {
                                val extractedCode = Regex("""/(?:reel|reels|p)/([A-Za-z0-9_-]+)""").find(urlStr)?.groupValues?.get(1) ?: shortcode
                                view?.post {
                                    val target = if (suspectedType == MediaType.REEL) {
                                        "https://www.instagram.com/reel/$extractedCode/"
                                    } else {
                                        "https://www.instagram.com/p/$extractedCode/"
                                    }
                                    view.loadUrl(target)
                                }
                            }
                            return true
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null

                        if (isVideoStreamUrl(reqUrl)) {
                            val fullStreamUrl = cleanVideoUrl(reqUrl)
                            val base = fullStreamUrl.substringBefore('?')
                            synchronized(capturedItems) {
                                if (capturedItems.none { it.url.substringBefore('?') == base }) {
                                    capturedItems.add(CarouselItem(url = fullStreamUrl, isVideo = true, thumbnailUrl = null))
                                }
                            }
                            // Strict reel completes immediately once stream is captured
                            if (isVideoStrict && suspectedType == MediaType.REEL) {
                                mainHandler.post { completeWithCaptured() }
                            }
                        } else if (isPhotoStreamUrl(reqUrl)) {
                            val base = reqUrl.substringBefore('?')
                            synchronized(capturedItems) {
                                if (capturedItems.none { it.url.substringBefore('?') == base }) {
                                    capturedItems.add(CarouselItem(url = reqUrl, isVideo = false, thumbnailUrl = reqUrl))
                                }
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)

                        val jsScanAndStep = """
                            (function() {
                                var res = { type: 'none', items: [] };
                                try {
                                    // 1. Scan React Fiber for complete carousel data (3, 4, 10 items)
                                    var el = document.querySelector('article') || document.querySelector('main') || document.body;
                                    var fKey = Object.keys(el || {}).find(function(k) { return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'); });
                                    if (fKey && el) {
                                        var fItems = [];
                                        var visited = new Set();
                                        function scan(node, d) {
                                            if (!node || d > 28 || visited.has(node)) return;
                                            visited.add(node);
                                            var p = node.memoizedProps;
                                            if (p) {
                                                var cm = p.carousel_media || (p.post && p.post.carousel_media) || (p.media && p.media.carousel_media);
                                                if (Array.isArray(cm) && cm.length > 0) {
                                                    cm.forEach(function(m) {
                                                        var isV = m.media_type === 2 || (m.video_versions && m.video_versions.length > 0);
                                                        var vUrl = isV && m.video_versions && m.video_versions[0] ? m.video_versions[0].url : null;
                                                        var imgUrl = m.image_versions2 && m.image_versions2.candidates && m.image_versions2.candidates[0] ? m.image_versions2.candidates[0].url : (m.display_url || null);
                                                        if (vUrl || imgUrl) fItems.push({ type: isV ? 'video' : 'photo', url: vUrl || imgUrl, thumb: imgUrl || vUrl });
                                                    });
                                                    return;
                                                }
                                                var sc = p.edge_sidecar_to_children || (p.post && p.post.edge_sidecar_to_children) || (p.media && p.media.edge_sidecar_to_children);
                                                if (sc && Array.isArray(sc.edges) && sc.edges.length > 0) {
                                                    sc.edges.forEach(function(e) {
                                                        var n = e.node;
                                                        if (n) {
                                                            var isV = !!n.is_video;
                                                            var u = isV && n.video_url ? n.video_url : n.display_url;
                                                            if (u) fItems.push({ type: isV ? 'video' : 'photo', url: u, thumb: n.display_url || u });
                                                        }
                                                    });
                                                    return;
                                                }
                                            }
                                            scan(node.child, d + 1);
                                            scan(node.sibling, d + 1);
                                        }
                                        scan(el[fKey], 0);
                                        if (fItems.length > 0) {
                                            return JSON.stringify({ type: 'fiber', items: fItems });
                                        }
                                    }
                                } catch(e) {}

                                // 2. Trigger play on any video
                                var vids = document.querySelectorAll('video');
                                for (var i = 0; i < vids.length; i++) {
                                    vids[i].muted = false;
                                    try { vids[i].play(); } catch(e){}
                                }

                                // 3. Step forward through carousel slides
                                var nextBtn = document.querySelector('button[aria-label*="Next"], button[aria-label*="next"], button[aria-label*="Next slide"], [aria-label*="Next"], [aria-label*="next"], .coreSpriteRightChevron');
                                if (nextBtn) {
                                    try { nextBtn.click(); } catch(e){}
                                }
                                try {
                                    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', keyCode: 39, code: 'ArrowRight', which: 39, bubbles: true }));
                                } catch(e){}

                                // 4. Collect all visible images & videos in article/post
                                var dItems = [];
                                var allV = document.querySelectorAll('article video, [role="presentation"] video, video');
                                for (var vi = 0; vi < allV.length; vi++) {
                                    var s = allV[vi].src || (allV[vi].querySelector('source') ? allV[vi].querySelector('source').src : '');
                                    if (s && s.indexOf('http') === 0 && s.indexOf('blob:') !== 0) {
                                        dItems.push({ type: 'video', url: s, thumb: s });
                                    }
                                }
                                var allI = document.querySelectorAll('article img, [role="presentation"] img, ul li img, img');
                                for (var ii = 0; ii < allI.length; ii++) {
                                    var s = allI[ii].src;
                                    if (s && s.indexOf('http') === 0 && s.indexOf('profile_pic') === -1 && s.indexOf('150x150') === -1 && s.indexOf('p50x50') === -1 && s.indexOf('rsrc.php') === -1) {
                                        if (allI[ii].naturalWidth > 200 || s.indexOf('t51.') !== -1 || s.indexOf('e35') !== -1 || s.indexOf('1080x1080') !== -1) {
                                            dItems.push({ type: 'photo', url: s, thumb: s });
                                        }
                                    }
                                }

                                // 5. Check private account
                                if (document.body && document.body.innerText && document.body.innerText.indexOf('This Account is Private') !== -1) {
                                    return JSON.stringify({ type: 'private', items: [] });
                                }

                                return JSON.stringify({ type: 'dom', items: dItems });
                            })();
                        """.trimIndent()

                        fun runStep(delay: Long, isFinal: Boolean = false) {
                            mainHandler.postDelayed({
                                if (!deferred.isActive || view == null) return@postDelayed
                                view.evaluateJavascript(jsScanAndStep) { res ->
                                    if (res != null && res != "null" && res != "\"null\"") {
                                        try {
                                            val clean = if (res.startsWith("\"") && res.endsWith("\"")) {
                                                res.substring(1, res.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                            } else res
                                            val obj = JSONObject(clean)
                                            val type = obj.optString("type")
                                            if (type == "private") {
                                                deferred.complete(ResolutionResult.PrivateRestricted(shortcode))
                                                return@evaluateJavascript
                                            }
                                            val arr = obj.optJSONArray("items")
                                            if (arr != null && arr.length() > 0) {
                                                for (idx in 0 until arr.length()) {
                                                    val it = arr.getJSONObject(idx)
                                                    val itUrl = it.optString("url")
                                                    val isV = it.optString("type") == "video"
                                                    val thumb = it.optString("thumb").ifEmpty { itUrl }
                                                    if (itUrl.isNotEmpty()) {
                                                        val base = itUrl.substringBefore('?')
                                                        synchronized(capturedItems) {
                                                            if (capturedItems.none { existing -> existing.url.substringBefore('?') == base }) {
                                                                capturedItems.add(CarouselItem(url = itUrl, isVideo = isV, thumbnailUrl = thumb))
                                                            }
                                                        }
                                                    }
                                                }
                                                // If fiber found complete carousel or final step, complete
                                                if (type == "fiber" && capturedItems.isNotEmpty()) {
                                                    completeWithCaptured()
                                                    return@evaluateJavascript
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    if (isFinal) {
                                        completeWithCaptured()
                                    }
                                }
                            }, delay)
                        }

                        // Schedule automated multi-slide progression
                        runStep(600, isFinal = false)
                        runStep(1500, isFinal = false)
                        runStep(2800, isFinal = false)
                        runStep(4200, isFinal = true)
                    }
                }
            }

            webView.loadUrl(url)

            val result = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }

            return@withContext result ?: run {
                // If timed out, check if anything was captured
                val list = synchronized(capturedItems) { capturedItems.toList() }
                if (list.isNotEmpty()) {
                    val first = list[0]
                    val isMulti = list.size > 1
                    val resolvedType = when {
                        suspectedType == MediaType.REEL -> MediaType.REEL
                        suspectedType == MediaType.STORY -> MediaType.STORY
                        isMulti -> MediaType.CAROUSEL
                        first.isVideo -> MediaType.VIDEO
                        else -> MediaType.PHOTO
                    }
                    ResolutionResult.Success(
                        MediaItem(
                            shortcode = shortcode,
                            originalUrl = url,
                            mediaType = resolvedType,
                            title = if (isMulti) "Instagram Post (${list.size} Items)" else if (first.isVideo) "Instagram Video ($shortcode)" else "Instagram Photo ($shortcode)",
                            author = "Instagram Creator",
                            downloadUrl = first.url,
                            thumbnailUrl = first.thumbnailUrl ?: first.url,
                            carouselItems = if (isMulti) list else emptyList(),
                            isVideo = first.isVideo
                        )
                    )
                } else {
                    ResolutionResult.Error(
                        "Media content requires in-app preview. Tap Force Resolver to review and download all post items."
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext ResolutionResult.Error("Resolution error: ${e.message}")
        } finally {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {}
        }
    }

    private fun isPhotoStreamUrl(reqUrl: String): Boolean {
        val isCdn = reqUrl.contains("cdninstagram.com") || reqUrl.contains("fbcdn.net")
        val isPhoto = (reqUrl.contains("/v/t51.") || reqUrl.contains("/e35/") || reqUrl.contains("/s1080x1080/") || reqUrl.contains("1080x1080")) &&
                !reqUrl.contains("profile_pic") &&
                !reqUrl.contains("s150x150") &&
                !reqUrl.contains("p50x50") &&
                !reqUrl.contains("44884218_345707102883719") &&
                (reqUrl.contains(".jpg") || reqUrl.contains(".webp") || reqUrl.contains(".jpeg"))
        val isNotStatic = !reqUrl.contains(".ico") && !reqUrl.contains(".js") && !reqUrl.contains(".css") && !reqUrl.contains(".woff")
        return isCdn && isPhoto && isNotStatic
    }

    private fun isVideoStreamUrl(reqUrl: String): Boolean {
        val isCdn = reqUrl.contains("cdninstagram.com") ||
                reqUrl.contains("fbcdn.net") ||
                reqUrl.contains("instagram.com") ||
                reqUrl.contains("facebook.com") ||
                reqUrl.contains("googlevideo.com") ||
                reqUrl.contains("video.twimg.com") ||
                reqUrl.contains("twimg.com")

        val isVideo = reqUrl.contains(".mp4") ||
                reqUrl.contains("videoplayback") ||
                reqUrl.contains("/v/t50") ||
                reqUrl.contains("/v/t16") ||
                reqUrl.contains("bytestart") ||
                reqUrl.contains("&bytestart=") ||
                reqUrl.contains("/o1/v/") ||
                reqUrl.contains(".m3u8")

        val isNotStatic = !reqUrl.contains(".jpg") &&
                !reqUrl.contains(".webp") &&
                !reqUrl.contains(".png") &&
                !reqUrl.contains(".jpeg") &&
                !reqUrl.contains(".js") &&
                !reqUrl.contains(".css") &&
                !reqUrl.contains(".woff")

        return isCdn && isVideo && isNotStatic
    }

    companion object {
        fun cleanVideoUrl(raw: String): String {
            return raw
        }
    }
}
