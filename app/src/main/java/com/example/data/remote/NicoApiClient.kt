package com.example.data.remote

import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.NicoSortOrder
import com.example.data.model.NicoVideo
import com.example.data.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                list.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = mutableListOf<Cookie>()
        val now = System.currentTimeMillis()
        for ((_, cookies) in cookieStore) {
            synchronized(cookies) {
                cookies.removeAll { it.expiresAt < now }
                result.addAll(cookies.filter { it.matches(url) })
            }
        }
        return result
    }

    fun buildCookieHeader(url: HttpUrl): String {
        return loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun setUserSession(userSession: String?) {
        val host = "nicovideo.jp"
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it.name == "user_session" }
            if (!userSession.isNullOrBlank()) {
                val cookie = Cookie.Builder()
                    .name("user_session")
                    .value(userSession)
                    .domain("nicovideo.jp")
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                    .build()
                list.add(cookie)
            }
        }
    }

    fun hasUserSession(): Boolean {
        val list = cookieStore["nicovideo.jp"] ?: return false
        synchronized(list) {
            return list.any { it.name == "user_session" && it.value.isNotBlank() }
        }
    }

    fun importRawCookies(rawCookieHeader: String) {
        if (rawCookieHeader.isBlank()) return
        val host = "nicovideo.jp"
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        val expiresAt = System.currentTimeMillis() + 6L * 60 * 60 * 1000
        synchronized(list) {
            rawCookieHeader.split(";").forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val name = part.substring(0, idx).trim()
                    val value = part.substring(idx + 1).trim()
                    if (name.isNotBlank() && value.isNotBlank()) {
                        list.removeAll { it.name == name && it.domain == host }
                        runCatching {
                            Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(host)
                                .path("/")
                                .expiresAt(expiresAt)
                                .build()
                        }.getOrNull()?.let { list.add(it) }
                    }
                }
            }
        }
    }
}

data class WatchPayload(
    val streamInfo: StreamInfo,
    val comments: List<DanmakuComment>
)

class NicoApiClient {
    val cookieJar: InMemoryCookieJar
        get() = NetworkManager.cookieJar

    val okHttpClient: OkHttpClient
        get() = NetworkManager.okHttpClient

    private fun generateActionTrackId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val prefix = (1..10).map { chars.random() }.joinToString("")
        return "${prefix}_${System.currentTimeMillis()}"
    }

    /**
     * Fetches real Niconico videos via the official public Snapshot Search API v2.
     */
    suspend fun searchVideos(
        query: String,
        categoryTag: String = "",
        sortOrder: NicoSortOrder = NicoSortOrder.VIEWS,
        limit: Int = 30,
        offset: Int = 0,
        recentDays: Int? = null
    ): Result<List<NicoVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val effectiveQuery = when {
                query.isNotBlank() -> query.trim()
                categoryTag.isNotBlank() -> categoryTag
                else -> "VOCALOID OR ゲーム OR アニメ OR 音楽"
            }
            val targets = if (query.isBlank() && categoryTag.isNotBlank()) "tagsExact" else "title,description,tags"

            val urlBuilder = "https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", effectiveQuery)
                .addQueryParameter("targets", targets)
                .addQueryParameter(
                    "fields",
                    "contentId,title,description,viewCounter,commentCounter,mylistCounter,lengthSeconds,thumbnailUrl,startTime,tags,categoryTags"
                )
                .addQueryParameter("_sort", sortOrder.apiParam)
                .addQueryParameter("_offset", offset.toString())
                .addQueryParameter("_limit", limit.toString())
                .addQueryParameter("_context", "NicoFlowAndroid")

            if (recentDays != null && recentDays > 0) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
                cal.add(java.util.Calendar.DAY_OF_YEAR, -recentDays)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'00:00:00+09:00", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Tokyo")
                urlBuilder.addQueryParameter("filters[startTime][gte]", sdf.format(cal.time))
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Niconico API HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val dataArray = root.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val rawTags = item.optString("tags", "")
                    val tags = rawTags.split(" ").filter { it.isNotBlank() }
                    val thumb = item.optString("thumbnailUrl", "")
                        .replace("http://", "https://")
                    list.add(
                        NicoVideo(
                            contentId = item.optString("contentId"),
                            title = item.optString("title"),
                            description = stripHtml(item.optString("description", "")),
                            viewCounter = item.optLong("viewCounter", 0L),
                            commentCounter = item.optLong("commentCounter", 0L),
                            mylistCounter = item.optLong("mylistCounter", 0L),
                            lengthSeconds = item.optInt("lengthSeconds", 0),
                            thumbnailUrl = thumb,
                            startTime = item.optString("startTime", ""),
                            tags = tags,
                            categoryTag = item.optString("categoryTags", "")
                        )
                    )
                }
                list
            }
        }
    }

    /**
     * Fetches real watch metadata, HLS stream URL (via domand access-rights), and real nvcomment danmaku.
     * Uses 3 strategies:
     * 1) watch/{videoId}?responseType=json
     * 2) watch/{videoId} HTML <meta name="server-response" content="...">
     * 3) api/watch/v3 (or v3_guest)
     */
    suspend fun fetchWatchPayload(videoId: String): WatchPayload = withContext(Dispatchers.IO) {
        val fallbackTrackId = generateActionTrackId()
        val isLoggedIn = cookieJar.hasUserSession()

        var ownerName = ""
        var fullDesc = ""
        var streamUrl: String? = null
        var streamHeaders: Map<String, String> = emptyMap()
        var statusMsg: String? = null
        val comments = mutableListOf<DanmakuComment>()
        val debugCodes = mutableListOf<String>()

        try {
            val data: JSONObject? = fetchWatchJsonViaPageJson(videoId, debugCodes)
                ?: fetchWatchJsonViaHtmlMeta(videoId, debugCodes)
                ?: fetchWatchJsonViaV3Api(videoId, fallbackTrackId, isLoggedIn, debugCodes)

            if (data == null) {
                val detail = debugCodes.joinToString(", ")
                statusMsg = "无法获取视频信息 ($detail)，请检查网络或尝试登录官方账号"
            } else {
                val videoObj = data.optJSONObject("video")
                if (videoObj != null) {
                    fullDesc = stripHtml(videoObj.optString("description", ""))
                }
                val ownerObj = data.optJSONObject("owner")
                if (ownerObj != null) {
                    ownerName = ownerObj.optString("nickname", "")
                }
                val channelObj = data.optJSONObject("channel")
                if (ownerName.isBlank() && channelObj != null) {
                    ownerName = channelObj.optString("name", "")
                }

                // Use the exact watchTrackId issued by Niconico server for access-rights/hls
                val serverTrackId = data.optJSONObject("client")
                    ?.optString("watchTrackId", "")
                    .orEmpty()
                    .ifBlank { fallbackTrackId }

                // 1. Fetch real nvComment danmaku
                val commentObj = data.optJSONObject("comment")
                val nvComment = commentObj?.optJSONObject("nvComment")
                if (nvComment != null) {
                    val server = nvComment.optString("server", "https://nvcomment.nicovideo.jp")
                    val threadKey = nvComment.optString("threadKey", "")
                    val params = nvComment.optJSONObject("params")
                    if (threadKey.isNotBlank() && params != null) {
                        comments.addAll(fetchNvComments(videoId, server, threadKey, params))
                    }
                }

                // 2. Request domand HLS access-rights
                val mediaObj = data.optJSONObject("media")
                val domand = mediaObj?.optJSONObject("domand")
                if (domand != null) {
                    val accessRightKey = domand.optString("accessRightKey", "")
                    val videos = domand.optJSONArray("videos")
                    val audios = domand.optJSONArray("audios")
                    val bestVideoId = pickBestAvailableId(videos)
                    val bestAudioId = pickBestAvailableId(audios)
                    if (accessRightKey.isNotBlank() && bestVideoId != null && bestAudioId != null) {
                        val hlsResult = requestDomandHls(
                            videoId = videoId,
                            actionTrackId = serverTrackId,
                            accessRightKey = accessRightKey,
                            videoQualityId = bestVideoId,
                            audioQualityId = bestAudioId
                        )
                        if (hlsResult != null) {
                            streamUrl = hlsResult.first
                            streamHeaders = hlsResult.second
                        } else {
                            statusMsg = "无法获取该视频的流媒体播放权限（可能需要登录或地区限制）"
                        }
                    } else {
                        statusMsg = "该视频暂无可用清晰度"
                    }
                } else {
                    statusMsg = "该视频需要 Niconico 登录/会员或受地区限制"
                }
            }
        } catch (e: Exception) {
            statusMsg = "网络请求失败: ${e.localizedMessage ?: "请检查网络连接"}"
        }

        WatchPayload(
            streamInfo = StreamInfo(
                videoId = videoId,
                streamUrl = streamUrl,
                headers = streamHeaders,
                ownerName = ownerName,
                fullDescription = fullDesc,
                isOffline = false,
                statusMessage = statusMsg
            ),
            comments = comments.sortedBy { it.vposMs }
        )
    }

    /**
     * Parses <meta name="server-response"> JSON extracted from WebView and fetches real nvComment danmaku.
     */
    suspend fun fetchCommentsFromServerResponse(
        videoId: String,
        serverResponseJson: String
    ): List<DanmakuComment> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(serverResponseJson)
            val data = root.optJSONObject("data")?.optJSONObject("response")
                ?: root.optJSONObject("data")
                ?: root
            val nvComment = data.optJSONObject("comment")?.optJSONObject("nvComment")
                ?: return@withContext emptyList()
            val server = nvComment.optString("server", "https://nvcomment.nicovideo.jp")
            val threadKey = nvComment.optString("threadKey", "")
            val params = nvComment.optJSONObject("params") ?: return@withContext emptyList()
            if (threadKey.isBlank()) return@withContext emptyList()
            fetchNvComments(videoId, server, threadKey, params).sortedBy { it.vposMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchWatchJsonViaPageJson(videoId: String, debugCodes: MutableList<String>): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("https://www.nicovideo.jp/watch/$videoId?responseType=json")
                .header("Accept", "*/*")
                .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                .header("Referer", "https://www.nicovideo.jp/")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    debugCodes.add("json:${resp.code}")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val data = root.optJSONObject("data")
                data?.optJSONObject("response") ?: data
            }
        } catch (e: Exception) {
            debugCodes.add("json:err")
            null
        }
    }

    private fun fetchWatchJsonViaHtmlMeta(videoId: String, debugCodes: MutableList<String>): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("https://www.nicovideo.jp/watch/$videoId")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    debugCodes.add("html:${resp.code}")
                    return null
                }
                val html = resp.body?.string().orEmpty()
                val metaRegex = Regex("""<meta\s+name="server-response"\s+content="([^"]+)"""")
                val match = metaRegex.find(html)
                if (match != null) {
                    val unescaped = unescapeHtmlAttr(match.groupValues[1])
                    val root = JSONObject(unescaped)
                    return root.optJSONObject("data")?.optJSONObject("response")
                }
                val legacyRegex = Regex("""data-api-data="([^"]+)"""")
                val legacyMatch = legacyRegex.find(html)
                if (legacyMatch != null) {
                    val unescaped = unescapeHtmlAttr(legacyMatch.groupValues[1])
                    return JSONObject(unescaped)
                }
                debugCodes.add("html:nometa")
                null
            }
        } catch (_: Exception) {
            debugCodes.add("html:err")
            null
        }
    }

    private fun fetchWatchJsonViaV3Api(
        videoId: String,
        actionTrackId: String,
        isLoggedIn: Boolean,
        debugCodes: MutableList<String>
    ): JSONObject? {
        return try {
            val endpoint = if (isLoggedIn) "v3" else "v3_guest"
            val watchUrl = "https://www.nicovideo.jp/api/watch/$endpoint/$videoId?_frontendId=6&_frontendVersion=0&actionTrackId=$actionTrackId&skips=harmful"
            val req = Request.Builder()
                .url(watchUrl)
                .header("Accept", "*/*")
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/watch/$videoId")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    debugCodes.add("$endpoint:${resp.code}")
                    return null
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                root.optJSONObject("data")
            }
        } catch (_: Exception) {
            debugCodes.add("v3:err")
            null
        }
    }

    private fun unescapeHtmlAttr(raw: String): String {
        return raw
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
    }

    /**
     * Fetches the logged-in user's Niconico profile via nvapi.
     */
    suspend fun fetchMyProfile(): Result<com.example.data.local.NicoAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://nvapi.nicovideo.jp/v1/users/me")
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("获取账号信息失败 (HTTP ${resp.code})")
                val root = JSONObject(resp.body?.string().orEmpty())
                val user = root.optJSONObject("data")?.optJSONObject("user")
                    ?: throw IllegalStateException("账号数据解析失败")
                val id = user.optLong("id", 0L).toString()
                val nickname = user.optString("nickname", "ニコニコ用户")
                val isPremium = user.optBoolean("isPremium", false)
                val avatar = user.optJSONObject("icons")?.optString("large", "").orEmpty()
                com.example.data.local.NicoAccount(
                    userId = id,
                    nickname = nickname,
                    avatarUrl = avatar,
                    isPremium = isPremium
                )
            }
        }
    }

    /**
     * Syncs the logged-in user's official Niconico watch history.
     */
    suspend fun fetchRemoteWatchHistory(): List<NicoVideo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://nvapi.nicovideo.jp/v1/users/me/watch/history?pageSize=30&page=1")
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val items = root.optJSONObject("data")?.optJSONArray("items") ?: return@withContext emptyList()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until items.length()) {
                    val v = items.optJSONObject(i)?.optJSONObject("video") ?: continue
                    val id = v.optString("id", "")
                    if (id.isBlank()) continue
                    val count = v.optJSONObject("count")
                    val thumb = v.optJSONObject("thumbnail")?.optString("largeUrl", "")
                        ?.ifBlank { v.optJSONObject("thumbnail")?.optString("url", "") }
                        .orEmpty()
                    list.add(
                        NicoVideo(
                            contentId = id,
                            title = v.optString("title", ""),
                            viewCounter = count?.optLong("view", 0L) ?: 0L,
                            commentCounter = count?.optLong("comment", 0L) ?: 0L,
                            mylistCounter = count?.optLong("mylist", 0L) ?: 0L,
                            lengthSeconds = v.optInt("duration", 0),
                            thumbnailUrl = thumb,
                            ownerName = v.optJSONObject("owner")?.optString("name", "").orEmpty()
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Syncs the logged-in user's official Niconico Watch Later (あとで見る) list.
     */
    suspend fun fetchRemoteWatchLater(): List<NicoVideo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://nvapi.nicovideo.jp/v1/users/me/watch-later?sortKey=addedAt&sortOrder=desc&pageSize=30&page=1")
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val items = root.optJSONObject("data")
                    ?.optJSONObject("watchLater")
                    ?.optJSONArray("items")
                    ?: return@withContext emptyList()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until items.length()) {
                    val v = items.optJSONObject(i)?.optJSONObject("video") ?: continue
                    val id = v.optString("id", "")
                    if (id.isBlank()) continue
                    val count = v.optJSONObject("count")
                    val thumb = v.optJSONObject("thumbnail")?.optString("largeUrl", "")
                        ?.ifBlank { v.optJSONObject("thumbnail")?.optString("url", "") }
                        .orEmpty()
                    list.add(
                        NicoVideo(
                            contentId = id,
                            title = v.optString("title", ""),
                            viewCounter = count?.optLong("view", 0L) ?: 0L,
                            commentCounter = count?.optLong("comment", 0L) ?: 0L,
                            mylistCounter = count?.optLong("mylist", 0L) ?: 0L,
                            lengthSeconds = v.optInt("duration", 0),
                            thumbnailUrl = thumb,
                            ownerName = v.optJSONObject("owner")?.optString("name", "").orEmpty()
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun pickBestAvailableId(array: JSONArray?): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("isAvailable", false)) {
                val id = obj.optString("id", "")
                if (id.isNotBlank()) return id
            }
        }
        return null
    }

    private fun requestDomandHls(
        videoId: String,
        actionTrackId: String,
        accessRightKey: String,
        videoQualityId: String,
        audioQualityId: String
    ): Pair<String, Map<String, String>>? {
        return try {
            val url = "https://nvapi.nicovideo.jp/v1/watch/$videoId/access-rights/hls?actionTrackId=$actionTrackId"
            val outputs = JSONArray().put(
                JSONArray().put(videoQualityId).put(audioQualityId)
            )
            val payload = JSONObject().put("outputs", outputs)
            val req = Request.Builder()
                .url(url)
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("X-Request-With", "https://www.nicovideo.jp")
                .header("X-Access-Right-Key", accessRightKey)
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string().orEmpty())
                val contentUrl = root.optJSONObject("data")?.optString("contentUrl", "").orEmpty()
                if (contentUrl.isBlank()) return null

                val headers = mutableMapOf(
                    "Referer" to "https://www.nicovideo.jp/",
                    "Origin" to "https://www.nicovideo.jp"
                )
                val cookieHeader = cookieJar.buildCookieHeader(contentUrl.toHttpUrl())
                if (cookieHeader.isNotBlank()) {
                    headers["Cookie"] = cookieHeader
                }
                contentUrl to headers
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchNvComments(
        videoId: String,
        server: String,
        threadKey: String,
        params: JSONObject
    ): List<DanmakuComment> {
        val result = mutableListOf<DanmakuComment>()
        try {
            val bodyJson = JSONObject()
                .put("params", params)
                .put("threadKey", threadKey)
                .put("additionals", JSONObject())

            val req = Request.Builder()
                .url("$server/v1/threads")
                .header("X-Frontend-Id", "6")
                .header("X-Frontend-Version", "0")
                .header("Origin", "https://www.nicovideo.jp")
                .header("Referer", "https://www.nicovideo.jp/")
                .post(bodyJson.toString().toRequestBody("text/plain;charset=UTF-8".toMediaType()))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                return parseNvCommentsJson(videoId, resp.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            // Return whatever we parsed
        }
        return result
    }

    fun parseNvCommentsJson(videoId: String, json: String): List<DanmakuComment> {
        val result = mutableListOf<DanmakuComment>()
        try {
            val root = JSONObject(json)
            val threads = root.optJSONObject("data")?.optJSONArray("threads") ?: return emptyList()
            for (t in 0 until threads.length()) {
                val thread = threads.optJSONObject(t) ?: continue
                val comments = thread.optJSONArray("comments") ?: continue
                for (c in 0 until comments.length()) {
                    val cObj = comments.optJSONObject(c) ?: continue
                    val text = cObj.optString("body", "").trim()
                    if (text.isBlank()) continue
                    val id = cObj.optString("id", "${videoId}_${t}_$c")
                    val vposMs = cObj.optLong("vposMs", 0L)
                    val commands = cObj.optJSONArray("commands")
                    val (colorHex, pos) = parseCommands(commands)
                    result.add(
                        DanmakuComment(
                            id = id,
                            videoId = videoId,
                            vposMs = vposMs,
                            text = text,
                            colorHex = colorHex,
                            position = pos
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
        return result.sortedBy { it.vposMs }
    }

    private fun parseCommands(commands: JSONArray?): Pair<Long, DanmakuPosition> {
        var color = 0xFFFFFFFFL
        var position = DanmakuPosition.SCROLL
        if (commands == null) return color to position
        for (i in 0 until commands.length()) {
            when (commands.optString(i).lowercase()) {
                "ue" -> position = DanmakuPosition.TOP
                "shita" -> position = DanmakuPosition.BOTTOM
                "red" -> color = 0xFFFF3B30L
                "pink" -> color = 0xFFFF80BFL
                "orange" -> color = 0xFFFF9500L
                "yellow" -> color = 0xFFFFE600L
                "green" -> color = 0xFF34C759L
                "cyan" -> color = 0xFF00D4FFL
                "blue" -> color = 0xFF3B82F6L
                "purple" -> color = 0xFFC084FCL
                "black" -> color = 0xFF222222L
            }
        }
        return color to position
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }
}
