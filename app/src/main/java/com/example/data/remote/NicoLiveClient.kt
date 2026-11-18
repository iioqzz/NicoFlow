package com.example.data.remote

import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.NicoLiveCategory
import com.example.data.model.NicoVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Handles Niconico Live (生放送) discovery and WebSocket watch sessions.
 */
class NicoLiveClient(private val okHttpClient: OkHttpClient) {

    /**
     * Fetches currently on-air Niconico Live programs.
     * Tries the official live front API first, falling back to Snapshot Live Search API v2.
     */
    suspend fun fetchOnAirLives(
        category: NicoLiveCategory = NicoLiveCategory.ALL,
        limit: Int = 30
    ): Result<List<NicoVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val frontResult = fetchFromFrontApi(category.tabParam, limit)
            if (frontResult.isNotEmpty()) {
                return@runCatching frontResult
            }
            fetchFromSnapshotLiveApi(category.fallbackQuery, limit)
        }
    }

    private fun fetchFromFrontApi(tab: String, limit: Int): List<NicoVideo> {
        return try {
            val url = "https://live.nicovideo.jp/front/api/pages/recent/v1/programs"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("tab", tab)
                .addQueryParameter("offset", "0")
                .addQueryParameter("limit", limit.toString())
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://live.nicovideo.jp/")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val dataArr = root.optJSONArray("data") ?: return emptyList()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    if (id.isBlank()) continue
                    val title = item.optString("title", "")
                    val desc = item.optString("description", "")
                    val provider = item.optJSONObject("programProvider")?.optString("name", "").orEmpty()
                    val stats = item.optJSONObject("statistics")
                    val viewers = stats?.optLong("viewers", 0L) ?: 0L
                    val comments = stats?.optLong("comments", 0L) ?: 0L
                    val thumb = item.optJSONObject("thumbnail")?.optString("screenshot", "")
                        ?.ifBlank { item.optJSONObject("thumbnail")?.optString("huge", "") }
                        ?.ifBlank { item.optJSONObject("thumbnail")?.optString("small", "") }
                        .orEmpty()
                        .replace("http://", "https://")
                    list.add(
                        NicoVideo(
                            contentId = id,
                            title = title,
                            description = desc,
                            viewCounter = viewers,
                            commentCounter = comments,
                            thumbnailUrl = thumb,
                            ownerName = provider,
                            isLive = true
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchFromSnapshotLiveApi(query: String, limit: Int): List<NicoVideo> {
        val url = "https://snapshot.search.nicovideo.jp/api/v2/snapshot/live/contents/search"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("targets", "title,description,tags")
            .addQueryParameter("fields", "contentId,title,description,viewCounter,commentCounter,thumbnailUrl,startTime,communityText")
            .addQueryParameter("filters[liveStatus][0]", "onair")
            .addQueryParameter("_sort", "-viewCounter")
            .addQueryParameter("_limit", limit.toString())
            .addQueryParameter("_context", "NicoFlowAndroid")
            .build()

        val req = Request.Builder().url(url).get().build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Live API HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val dataArr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<NicoVideo>()
            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val id = item.optString("contentId", "")
                if (id.isBlank()) continue
                list.add(
                    NicoVideo(
                        contentId = id,
                        title = item.optString("title", ""),
                        description = item.optString("description", ""),
                        viewCounter = item.optLong("viewCounter", 0L),
                        commentCounter = item.optLong("commentCounter", 0L),
                        thumbnailUrl = item.optString("thumbnailUrl", "").replace("http://", "https://"),
                        ownerName = item.optString("communityText", ""),
                        isLive = true
                    )
                )
            }
            return list
        }
    }

    /**
     * Fetches the live watch page to extract the Niconico Live WebSocket URL.
     */
    suspend fun fetchLiveWebSocketUrl(liveId: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://live.nicovideo.jp/watch/$liveId")
                .header("Referer", "https://live.nicovideo.jp/")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext null to "无法加载直播页 (HTTP ${resp.code})"
                }
                val html = resp.body?.string().orEmpty()
                val regex = Regex("""data-props="([^"]+)"""")
                val match = regex.find(html) ?: return@withContext null to "该直播已结束或需要登录观看"
                val jsonText = match.groupValues[1]
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                val props = JSONObject(jsonText)
                val wsUrl = props.optJSONObject("site")
                    ?.optJSONObject("relive")
                    ?.optString("webSocketUrl", "")
                    .orEmpty()
                if (wsUrl.isBlank()) {
                    null to "该生放送当前无访客流（可能已结束或仅限会员）"
                } else {
                    wsUrl to null
                }
            }
        } catch (e: Exception) {
            null to "直播连接失败: ${e.localizedMessage ?: "网络错误"}"
        }
    }

    /**
     * Opens a live WebSocket session to obtain the HLS stream URI and maintain keep-alive pings.
     */
    fun openLiveSession(
        liveId: String,
        wsUrl: String,
        onStreamReady: (String) -> Unit,
        onLiveComment: (DanmakuComment) -> Unit,
        onError: (String) -> Unit
    ): WebSocket {
        val request = Request.Builder()
            .url(wsUrl)
            .header("Origin", "https://live.nicovideo.jp")
            .build()

        return okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val startWatching = JSONObject().apply {
                    put("type", "startWatching")
                    put("data", JSONObject().apply {
                        put("stream", JSONObject().apply {
                            put("quality", "high")
                            put("protocol", "hls")
                            put("latency", "low")
                            put("chasePlay", false)
                        })
                        put("room", JSONObject().apply {
                            put("protocol", "webSocket")
                            put("commentable", true)
                        })
                        put("reconnect", false)
                    })
                }
                webSocket.send(startWatching.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "ping" -> {
                            webSocket.send("""{"type":"pong"}""")
                            webSocket.send("""{"type":"keepSeat"}""")
                        }

                        "stream" -> {
                            val uri = msg.optJSONObject("data")?.optString("uri", "").orEmpty()
                            if (uri.isNotBlank()) {
                                onStreamReady(uri)
                            }
                        }

                        "seat" -> {
                            webSocket.send("""{"type":"keepSeat"}""")
                        }

                        "disconnect" -> {
                            val reason = msg.optJSONObject("data")?.optString("reason", "连接已断开").orEmpty()
                            onError("直播连接断开: $reason")
                        }

                        "error" -> {
                            val code = msg.optJSONObject("data")?.optString("code", "未知错误").orEmpty()
                            onError("直播错误: $code")
                        }

                        "comment" -> {
                            val body = msg.optJSONObject("data")?.optString("body", "").orEmpty()
                            if (body.isNotBlank()) {
                                onLiveComment(
                                    DanmakuComment(
                                        id = "live_${UUID.randomUUID()}",
                                        videoId = liveId,
                                        vposMs = 0L,
                                        text = body,
                                        colorHex = 0xFFFFFFFFL,
                                        position = DanmakuPosition.SCROLL
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("直播 WebSocket 连接失败: ${t.localizedMessage ?: "请重试"}")
            }
        })
    }
}
