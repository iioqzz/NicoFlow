package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttp NetworkManager with detailed HttpLoggingInterceptor
 * and complete desktop Chrome User-Agent / Sec-CH-UA / Niconico header spoofing.
 */
object NetworkManager {
    private const val TAG = "NicoNetwork"

    const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val cookieJar = InMemoryCookieJar()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.HEADERS
        }
        redactHeader("Cookie")
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host.lowercase()
            val builder = original.newBuilder()

            // Desktop Chrome User-Agent & Client Hints spoofing
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", DESKTOP_CHROME_UA)
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
            }
            if (original.header("Sec-CH-UA") == null) {
                builder.header(
                    "Sec-CH-UA",
                    "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""
                )
                builder.header("Sec-CH-UA-Mobile", "?0")
                builder.header("Sec-CH-UA-Platform", "\"Windows\"")
            }

            // Ensure required Niconico API headers are never missing
            if (host.endsWith("nicovideo.jp")) {
                if (original.header("Referer") == null) {
                    builder.header("Referer", "https://www.nicovideo.jp/")
                }
                if (host.startsWith("nvapi.") || host.startsWith("nvcomment.")) {
                    if (original.header("Origin") == null) {
                        builder.header("Origin", "https://www.nicovideo.jp")
                    }
                    if (original.header("X-Frontend-Id") == null) {
                        builder.header("X-Frontend-Id", "6")
                    }
                    if (original.header("X-Frontend-Version") == null) {
                        builder.header("X-Frontend-Version", "0")
                    }
                    if (original.header("Sec-Fetch-Mode") == null) {
                        builder.header("Sec-Fetch-Mode", "cors")
                        builder.header("Sec-Fetch-Site", "same-site")
                        builder.header("Sec-Fetch-Dest", "empty")
                    }
                }
            }

            chain.proceed(builder.build())
        }
        .addInterceptor(loggingInterceptor)
        .build()
}
