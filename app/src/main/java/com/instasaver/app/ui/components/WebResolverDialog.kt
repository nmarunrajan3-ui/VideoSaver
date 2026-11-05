package com.instasaver.app.ui.components

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.instasaver.app.data.local.MediaType
import com.instasaver.app.data.model.CarouselItem
import com.instasaver.app.data.model.MediaItem
import com.instasaver.app.network.InstagramUrlParser
import com.instasaver.app.network.NetworkClient

/**
 * Apple Design Inspired In-App Web Sniffer & Story Resolver.
 * Intercepts all media streams (videos and photos) across multi-story highlights and posts,
 * eliminates ERR_UNKNOWN_URL_SCHEME, and collects all 3, 5, or 10 stories in the preview.
 */
@Composable
fun WebResolverDialog(
    targetUrl: String,
    onMediaCaptured: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = remember(targetUrl) { InstagramUrlParser.parse(targetUrl) }
    val shortcode = parsed?.shortcode ?: System.currentTimeMillis().toString()

    val cleanWebUrl = remember(targetUrl, shortcode, parsed) {
        when {
            parsed?.platform == com.instasaver.app.network.Platform.YOUTUBE ->
                "https://m.youtube.com/watch?v=$shortcode"
            parsed?.platform == com.instasaver.app.network.Platform.INSTAGRAM && parsed.suspectedType == MediaType.REEL ->
                "https://www.instagram.com/reel/$shortcode/"
            parsed?.platform == com.instasaver.app.network.Platform.INSTAGRAM && parsed.suspectedType == MediaType.PHOTO ->
                "https://www.instagram.com/p/$shortcode/"
            else -> targetUrl
        }
    }

    val displayDomain = remember(cleanWebUrl, targetUrl) {
        try {
            java.net.URI(cleanWebUrl).host?.removePrefix("www.")?.removePrefix("m.") ?: "media"
        } catch (_: Exception) {
            "media"
        }
    }

    val capturedItems = remember { mutableStateListOf<CarouselItem>() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var pageLoading by remember { mutableStateOf(true) }
    var statusSubtitle by remember {
        mutableStateOf(if (parsed?.suspectedType == MediaType.STORY) "Playing stories... capturing all active items" else "Loading media...")
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                color = Color(0xFF1C1C1E),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Apple Modal Grabber Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color.White.copy(alpha = 0.28f))
                        )
                    }

                    // Apple Navigation Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Cancel",
                                color = Color(0xFF007AFF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        // Center Domain & Security Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C2C2E))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayDomain,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Progress bar
                    if (pageLoading && capturedItems.isEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = Color(0xFF007AFF),
                            trackColor = Color.Transparent
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Web Content Surface
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewInstance = this
                                    layoutParams = FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
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

                                            // Prevent ERR_UNKNOWN_URL_SCHEME
                                            if (urlStr.startsWith("intent://") || urlStr.startsWith("instagram://") || (scheme != "http" && scheme != "https")) {
                                                if (urlStr.contains("applink.instagram.com") || urlStr.contains("instagram.com")) {
                                                    val extractedCode = Regex("""/(?:reel|reels|p)/([A-Za-z0-9_-]+)""").find(urlStr)?.groupValues?.get(1) ?: shortcode
                                                    view?.post {
                                                        view.loadUrl("https://www.instagram.com/reel/$extractedCode/")
                                                    }
                                                }
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            pageLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            pageLoading = false
                                            triggerPlayJs(view)
                                            extractFromDom(view) { items ->
                                                for (it in items) {
                                                    if (capturedItems.none { existing -> existing.url == it.url }) {
                                                        capturedItems.add(it)
                                                    }
                                                }
                                                if (capturedItems.isNotEmpty()) {
                                                    statusSubtitle = "Detected ${capturedItems.size} story item(s)"
                                                }
                                            }
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val reqUrl = request?.url?.toString() ?: return null
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
                                                    reqUrl.contains(".m3u8") ||
                                                    reqUrl.contains("download")

                                            val isPhoto = (reqUrl.contains("/v/t51.") || reqUrl.contains("/e35/") || reqUrl.contains("/s1080x1080/")) &&
                                                    !reqUrl.contains("profile_pic") &&
                                                    !reqUrl.contains("s150x150") &&
                                                    !reqUrl.contains("p50x50") &&
                                                    !reqUrl.contains("44884218_345707102883719") &&
                                                    (reqUrl.contains(".jpg") || reqUrl.contains(".webp") || reqUrl.contains(".jpeg"))

                                            val isNotStatic = !reqUrl.contains(".ico") &&
                                                    !reqUrl.contains(".js") &&
                                                    !reqUrl.contains(".css") &&
                                                    !reqUrl.contains(".woff")

                                            if (isCdn && isNotStatic) {
                                                if (isVideo) {
                                                    view?.post {
                                                        if (capturedItems.none { it.url == reqUrl }) {
                                                            capturedItems.add(CarouselItem(url = reqUrl, isVideo = true, thumbnailUrl = null))
                                                            statusSubtitle = "Captured ${capturedItems.size} media/story item(s)"
                                                        }
                                                    }
                                                } else if (isPhoto) {
                                                    view?.post {
                                                        if (capturedItems.none { it.url == reqUrl }) {
                                                            capturedItems.add(CarouselItem(url = reqUrl, isVideo = false, thumbnailUrl = reqUrl))
                                                            statusSubtitle = "Captured ${capturedItems.size} media/story item(s)"
                                                        }
                                                    }
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }

                                    loadUrl(cleanWebUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Apple Bottom Floating Action Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1C1C1E),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            if (capturedItems.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${capturedItems.size} Media Stories Detected",
                                        color = Color(0xFF34C759),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Horizontal thumbnail strip for multiple stories
                                if (capturedItems.size > 1) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        itemsIndexed(capturedItems) { index, it ->
                                            val isSelected = index == selectedIndex
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF2C2C2E))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.5.dp,
                                                        color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { selectedIndex = index },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = it.thumbnailUrl ?: it.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (it.isVideo) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.6f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                val first = capturedItems.getOrElse(selectedIndex) { capturedItems[0] }
                                val resolvedType = when {
                                    parsed?.suspectedType == MediaType.STORY -> MediaType.STORY
                                    capturedItems.size > 1 -> MediaType.CAROUSEL
                                    first.isVideo -> MediaType.VIDEO
                                    else -> MediaType.PHOTO
                                }
                                val platformName = parsed?.platform?.displayName ?: "Media"

                                val finalMediaItem = MediaItem(
                                    shortcode = shortcode,
                                    originalUrl = targetUrl,
                                    mediaType = resolvedType,
                                    title = if (capturedItems.size > 1) "$platformName Stories ($shortcode)" else "$platformName ${resolvedType.displayName} ($shortcode)",
                                    author = parsed?.storyUsername ?: "$platformName Creator",
                                    downloadUrl = first.url,
                                    thumbnailUrl = first.thumbnailUrl ?: first.url,
                                    carouselItems = capturedItems.toList(),
                                    isVideo = first.isVideo
                                )

                                Button(
                                    onClick = {
                                        onMediaCaptured(finalMediaItem)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF007AFF)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (capturedItems.size > 1) "Save All (${capturedItems.size} ${if (parsed?.suspectedType == MediaType.STORY) "Stories" else "Items"})" else "Save Captured Media",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFA0A0A5)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = statusSubtitle,
                                        color = Color(0xFFA0A0A5),
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        webViewInstance?.let { wv ->
                                            triggerPlayJs(wv)
                                            extractFromDom(wv) { items ->
                                                for (it in items) {
                                                    if (capturedItems.none { existing -> existing.url == it.url }) {
                                                        capturedItems.add(it)
                                                    }
                                                }
                                                if (capturedItems.isNotEmpty()) {
                                                    statusSubtitle = "Detected ${capturedItems.size} story item(s)"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2C2C2E)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (parsed?.suspectedType == MediaType.STORY) "Capture Active Stories" else "Capture Post Items",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerPlayJs(webView: WebView?) {
    if (webView == null) return
    val js = """
        (function() {
            var vids = document.querySelectorAll('video');
            for (var i = 0; i < vids.length; i++) {
                vids[i].muted = false;
                try { vids[i].play(); } catch(e){}
            }
            var plays = document.querySelectorAll('[aria-label*="Play"], [aria-label*="play"], [role="button"]');
            for (var j = 0; j < plays.length; j++) {
                try { plays[j].click(); } catch(e){}
            }
            var nextBtn = document.querySelector('button[aria-label*="Next"], button[aria-label*="next"], button[aria-label*="Next slide"], [aria-label*="Next"], [aria-label*="next"], .coreSpriteRightChevron');
            if (nextBtn) {
                try { nextBtn.click(); } catch(e){}
            }
            try {
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', keyCode: 39, code: 'ArrowRight', which: 39, bubbles: true }));
            } catch(e){}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private fun extractFromDom(
    webView: WebView?,
    onItemsFound: (List<CarouselItem>) -> Unit
) {
    if (webView == null) return
    val js = """
        (function() {
            var items = [];

            // 1. Scan React fiber for direct carousel list
            try {
                var el = document.querySelector('article') || document.querySelector('main') || document.body;
                var fKey = Object.keys(el || {}).find(function(k) { return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'); });
                if (fKey && el) {
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
                                    if (vUrl || imgUrl) items.push({ type: isV ? 'video' : 'photo', url: vUrl || imgUrl });
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
                                        if (u) items.push({ type: isV ? 'video' : 'photo', url: u });
                                    }
                                });
                                return;
                            }
                        }
                        scan(node.child, d + 1);
                        scan(node.sibling, d + 1);
                    }
                    scan(el[fKey], 0);
                }
            } catch(e) {}

            // 2. DOM elements
            var vids = document.querySelectorAll('article video, video');
            for (var i = 0; i < vids.length; i++) {
                var s = vids[i].src || (vids[i].querySelector('source') ? vids[i].querySelector('source').src : '');
                if (s && s.indexOf('blob:') !== 0 && s.indexOf('http') === 0) {
                    if (!items.some(function(it) { return it.url === s; })) {
                        items.push({ type: 'video', url: s });
                    }
                }
            }
            var imgs = document.querySelectorAll('article img, [role="presentation"] img, ul li img, img');
            for (var j = 0; j < imgs.length; j++) {
                var s = imgs[j].src;
                if (s && s.indexOf('http') === 0 && s.indexOf('profile_pic') === -1 && s.indexOf('150x150') === -1 && s.indexOf('rsrc.php') === -1) {
                    if (imgs[j].naturalWidth > 200 || s.indexOf('t51.') !== -1 || s.indexOf('e35') !== -1 || s.indexOf('1080x1080') !== -1) {
                        if (!items.some(function(it) { return it.url === s; })) {
                            items.push({ type: 'photo', url: s });
                        }
                    }
                }
            }
            return JSON.stringify(items);
        })();
    """.trimIndent()

    webView.evaluateJavascript(js) { res ->
        if (res != null && res != "null" && res != "\"null\"") {
            try {
                val clean = if (res.startsWith("\"") && res.endsWith("\"")) {
                    res.substring(1, res.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                } else res
                val array = org.json.JSONArray(clean)
                val list = mutableListOf<CarouselItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val t = obj.optString("type")
                    val u = obj.optString("url")
                    if (u.isNotEmpty() && list.none { it.url == u }) {
                        list.add(CarouselItem(url = u, isVideo = (t == "video"), thumbnailUrl = u))
                    }
                }
                if (list.isNotEmpty()) {
                    onItemsFound(list)
                }
            } catch (_: Exception) {}
        }
    }
}
