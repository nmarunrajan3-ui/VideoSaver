package com.instasaver.app.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val webKitCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            try {
                val cookieManager = CookieManager.getInstance()
                val urlStr = url.toString()
                for (cookie in cookies) {
                    cookieManager.setCookie(urlStr, cookie.toString())
                }
                cookieManager.flush()
            } catch (_: Exception) {}
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return try {
                val cookieManager = CookieManager.getInstance()
                // If requesting Instagram CDN, attach cookies from main instagram.com domain
                val targetUrl = if (url.host.contains("cdninstagram.com") || url.host.contains("fbcdn.net")) {
                    "https://www.instagram.com/"
                } else {
                    url.toString()
                }
                val cookieHeader = cookieManager.getCookie(targetUrl) ?: return emptyList()
                val result = mutableListOf<Cookie>()
                for (part in cookieHeader.split(";")) {
                    val trimmed = part.trim()
                    Cookie.parse(url, trimmed)?.let { result.add(it) }
                }
                result
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(webKitCookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)      // 2 min for large video files
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
}
