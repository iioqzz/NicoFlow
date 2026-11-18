package com.example.data.repository

import android.content.Context
import com.example.data.local.CachedCommentEntity
import com.example.data.local.DownloadedVideoEntity
import com.example.data.local.MylistVideoEntity
import com.example.data.local.NicoDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DownloadStatus
import com.example.data.model.NicoSortOrder
import com.example.data.model.NicoVideo
import com.example.data.model.StreamInfo
import com.example.data.remote.NicoApiClient
import com.example.data.remote.WatchPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NicoRepository(
    private val context: Context,
    private val db: NicoDatabase,
    val apiClient: NicoApiClient
) {
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    val downloadsFlow: Flow<List<DownloadedVideoEntity>> = db.downloadDao().observeAllDownloads()
    val historyFlow: Flow<List<WatchHistoryEntity>> = db.historyDao().observeHistory()
    val mylistFlow: Flow<List<MylistVideoEntity>> = db.mylistDao().observeMylist()

    fun observeIsInMylist(contentId: String): Flow<Boolean> =
        db.mylistDao().observeIsInMylist(contentId)

    fun observeComments(videoId: String): Flow<List<DanmakuComment>> =
        db.commentDao().observeComments(videoId).map { list -> list.map { it.toDanmakuComment() } }

    suspend fun searchVideos(
        query: String,
        categoryTag: String = "",
        sortOrder: NicoSortOrder = NicoSortOrder.VIEWS,
        limit: Int = 30,
        offset: Int = 0,
        recentDays: Int? = null
    ): Result<List<NicoVideo>> = apiClient.searchVideos(
        query = query,
        categoryTag = categoryTag,
        sortOrder = sortOrder,
        limit = limit,
        offset = offset,
        recentDays = recentDays
    )

    /**
     * Bilibili-style recommendation feed:
     * Combines personalized interest-tag pool + recent trending pool + serendipity offset,
     * filters out already-seen and disliked videos, and interleaves results for freshness.
     */
    suspend fun fetchRecommendedFeed(
        category: com.example.data.model.NicoCategory,
        isHotTab: Boolean,
        pageIndex: Int,
        interestTags: List<String>,
        excludedIds: Set<String>,
        dislikedTags: Set<String>,
        hotRecentDays: Int? = null
    ): Result<List<NicoVideo>> {
        val offset = (pageIndex * 20) % 200

        // Bangumi (番剧) category: official/channel anime (including region-locked licensed anime)
        if (category == com.example.data.model.NicoCategory.BANGUMI) {
            return apiClient.searchVideos(
                query = "アニメ 第1話 OR プレミアム限定動画 OR dアニメストア",
                categoryTag = "",
                sortOrder = NicoSortOrder.VIEWS,
                limit = 30,
                offset = offset,
                recentDays = 60
            ).map { list ->
                val officialAnime = list.filter { v ->
                    v.contentId.startsWith("so") || v.tags.any { it.contains("プレミアム限定") || it.contains("dアニメ") }
                }
                (if (officialAnime.isNotEmpty()) officialAnime else list)
                    .distinctBy { it.contentId }
                    .filterNot { it.contentId in excludedIds }
            }
        }

        val categoryTag = category.queryTag
        if (isHotTab) {
            // Hot tab: manual publish-time filter (null = all-time, or user-specified days)
            val hotOffset = (pageIndex * 40) % 1000
            return apiClient.searchVideos(
                query = "",
                categoryTag = categoryTag,
                sortOrder = NicoSortOrder.VIEWS,
                limit = 50,
                offset = hotOffset,
                recentDays = hotRecentDays
            ).map { list ->
                val userVideos = list.filterNot { it.contentId.startsWith("so") }
                (if (userVideos.isNotEmpty()) userVideos else list)
                    .filterNot { it.contentId in excludedIds }
            }
        }

        // Recommend tab: blend recent active user videos + personalized interest tag videos
        val sortRotation = listOf(
            NicoSortOrder.COMMENTS,
            NicoSortOrder.MYLIST,
            NicoSortOrder.VIEWS,
            NicoSortOrder.NEWEST
        )
        val mainSort = sortRotation[pageIndex % sortRotation.size]
        val mainResult = apiClient.searchVideos(
            query = "",
            categoryTag = categoryTag,
            sortOrder = mainSort,
            limit = 30,
            offset = offset,
            recentDays = 30
        )

        val baseList = mainResult.getOrElse { return mainResult }.toMutableList()

        // If user has interest tags and is on ALL category, mix in personalized recommendations
        if (categoryTag.isBlank() && interestTags.isNotEmpty()) {
            val pickedTag = interestTags[pageIndex % interestTags.size]
            val personalized = apiClient.searchVideos(
                query = pickedTag,
                categoryTag = "",
                sortOrder = NicoSortOrder.VIEWS,
                limit = 10,
                offset = (pageIndex * 10) % 100,
                recentDays = 60
            ).getOrDefault(emptyList())
            baseList.addAll(personalized)
        }

        val filtered = baseList
            .distinctBy { it.contentId }
            .filterNot { it.contentId.startsWith("so") }
            .filterNot { it.contentId in excludedIds }
            .filterNot { video ->
                dislikedTags.isNotEmpty() && video.tags.any { it in dislikedTags }
            }
            .shuffled()

        return Result.success(if (filtered.isNotEmpty()) filtered else baseList.distinctBy { it.contentId }.shuffled())
    }

    suspend fun loadWatchPayload(video: NicoVideo): WatchPayload {
        // 1. Check if downloaded offline
        val local = db.downloadDao().getDownload(video.contentId)
        if (local != null && local.downloadStatus() == DownloadStatus.COMPLETED) {
            val file = File(local.localFilePath)
            if (file.exists()) {
                val cachedComments = db.commentDao().getComments(video.contentId).map { it.toDanmakuComment() }
                return WatchPayload(
                    streamInfo = StreamInfo(
                        videoId = video.contentId,
                        streamUrl = file.toURI().toString(),
                        ownerName = local.ownerName,
                        fullDescription = local.description,
                        isOffline = true
                    ),
                    comments = cachedComments
                )
            }
        }

        // 2. Fetch online
        val payload = apiClient.fetchWatchPayload(video.contentId)
        if (payload.comments.isNotEmpty()) {
            db.commentDao().clearRemoteComments(video.contentId)
            db.commentDao().insertAll(
                payload.comments.map {
                    CachedCommentEntity(
                        id = it.id,
                        videoId = it.videoId,
                        vposMs = it.vposMs,
                        text = it.text,
                        colorHex = it.colorHex,
                        position = it.position.name,
                        isUserPosted = false
                    )
                }
            )
        }
        val mergedComments = db.commentDao().getComments(video.contentId).map { it.toDanmakuComment() }
        return payload.copy(comments = mergedComments)
    }

    suspend fun postLocalDanmaku(
        videoId: String,
        vposMs: Long,
        text: String,
        colorHex: Long = 0xFF00D4FFL,
        position: DanmakuPosition = DanmakuPosition.SCROLL
    ) {
        val comment = CachedCommentEntity(
            id = "user_${UUID.randomUUID()}",
            videoId = videoId,
            vposMs = vposMs,
            text = text.trim(),
            colorHex = colorHex,
            position = position.name,
            isUserPosted = true
        )
        db.commentDao().insert(comment)
    }

    suspend fun recordHistory(video: NicoVideo, positionMs: Long = 0L) {
        db.historyDao().upsert(
            WatchHistoryEntity(
                contentId = video.contentId,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                lengthSeconds = video.lengthSeconds,
                viewCounter = video.viewCounter,
                commentCounter = video.commentCounter,
                lastPositionMs = positionMs,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateHistoryPosition(contentId: String, positionMs: Long) {
        db.historyDao().updatePosition(contentId, positionMs)
    }

    suspend fun clearHistory() {
        db.historyDao().clearAll()
    }

    suspend fun toggleMylist(video: NicoVideo, currentlyInMylist: Boolean) {
        if (currentlyInMylist) {
            db.mylistDao().deleteById(video.contentId)
        } else {
            db.mylistDao().insert(
                MylistVideoEntity(
                    contentId = video.contentId,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    lengthSeconds = video.lengthSeconds,
                    viewCounter = video.viewCounter,
                    commentCounter = video.commentCounter,
                    mylistCounter = video.mylistCounter
                )
            )
        }
    }

    /**
     * Syncs official Niconico account profile, watch history, and watch-later/mylist into local Room DB.
     */
    suspend fun syncAccountData(userSession: String): Result<com.example.data.local.NicoAccount> {
        apiClient.cookieJar.setUserSession(userSession)
        val profileResult = apiClient.fetchMyProfile()
        return profileResult.map { profile ->
            val remoteHistory = apiClient.fetchRemoteWatchHistory()
            val now = System.currentTimeMillis()
            remoteHistory.forEachIndexed { idx, v ->
                db.historyDao().upsert(
                    WatchHistoryEntity(
                        contentId = v.contentId,
                        title = v.title,
                        thumbnailUrl = v.thumbnailUrl,
                        lengthSeconds = v.lengthSeconds,
                        viewCounter = v.viewCounter,
                        commentCounter = v.commentCounter,
                        watchedAt = now - idx * 1000L
                    )
                )
            }
            val remoteWatchLater = apiClient.fetchRemoteWatchLater()
            remoteWatchLater.forEachIndexed { idx, v ->
                db.mylistDao().insert(
                    MylistVideoEntity(
                        contentId = v.contentId,
                        title = v.title,
                        thumbnailUrl = v.thumbnailUrl,
                        lengthSeconds = v.lengthSeconds,
                        viewCounter = v.viewCounter,
                        commentCounter = v.commentCounter,
                        mylistCounter = v.mylistCounter,
                        addedAt = now - idx * 1000L
                    )
                )
            }
            profile.copy(userSession = userSession)
        }
    }

    fun startOfflineDownload(video: NicoVideo) {
        if (activeDownloadJobs.containsKey(video.contentId)) return

        val job = downloadScope.launch {
            val videoDir = File(context.filesDir, "downloads/${video.contentId}")
            videoDir.mkdirs()

            db.downloadDao().upsert(
                DownloadedVideoEntity(
                    contentId = video.contentId,
                    title = video.title,
                    description = video.description,
                    thumbnailUrl = video.thumbnailUrl,
                    ownerName = video.ownerName,
                    lengthSeconds = video.lengthSeconds,
                    viewCounter = video.viewCounter,
                    commentCounter = video.commentCounter,
                    mylistCounter = video.mylistCounter,
                    tagsCsv = video.tags.joinToString(","),
                    localFilePath = "",
                    fileSizeBytes = 0L,
                    status = DownloadStatus.DOWNLOADING.name,
                    progress = 0.02f
                )
            )

            try {
                val payload = apiClient.fetchWatchPayload(video.contentId)

                // Cache danmaku comments for offline playback
                if (payload.comments.isNotEmpty()) {
                    db.commentDao().clearRemoteComments(video.contentId)
                    db.commentDao().insertAll(
                        payload.comments.map {
                            CachedCommentEntity(
                                id = it.id,
                                videoId = it.videoId,
                                vposMs = it.vposMs,
                                text = it.text,
                                colorHex = it.colorHex,
                                position = it.position.name,
                                isUserPosted = false
                            )
                        }
                    )
                }

                val streamUrl = payload.streamInfo.streamUrl
                if (streamUrl.isNullOrBlank()) {
                    db.downloadDao().updateProgress(
                        video.contentId,
                        0f,
                        DownloadStatus.FAILED.name,
                        0L,
                        ""
                    )
                    return@launch
                }

                val headers = payload.streamInfo.headers
                val localMediaFile = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                    downloadHlsStream(video.contentId, streamUrl, headers, videoDir)
                } else {
                    downloadDirectFile(video.contentId, streamUrl, headers, File(videoDir, "video.mp4"))
                }

                if (localMediaFile != null && localMediaFile.exists()) {
                    val totalSize = videoDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    db.downloadDao().updateProgress(
                        contentId = video.contentId,
                        progress = 1f,
                        status = DownloadStatus.COMPLETED.name,
                        sizeBytes = totalSize,
                        localPath = localMediaFile.absolutePath
                    )
                } else {
                    db.downloadDao().updateProgress(
                        video.contentId,
                        0f,
                        DownloadStatus.FAILED.name,
                        0L,
                        ""
                    )
                }
            } catch (_: Exception) {
                db.downloadDao().updateProgress(
                    video.contentId,
                    0f,
                    DownloadStatus.FAILED.name,
                    0L,
                    ""
                )
            } finally {
                activeDownloadJobs.remove(video.contentId)
            }
        }
        activeDownloadJobs[video.contentId] = job
    }

    suspend fun cancelOrDeleteDownload(contentId: String) = withContext(Dispatchers.IO) {
        activeDownloadJobs.remove(contentId)?.cancel()
        val videoDir = File(context.filesDir, "downloads/$contentId")
        if (videoDir.exists()) {
            videoDir.deleteRecursively()
        }
        db.downloadDao().deleteById(contentId)
    }

    private suspend fun downloadDirectFile(
        contentId: String,
        url: String,
        headers: Map<String, String>,
        dest: File
    ): File? = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        apiClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body ?: return@withContext null
            val total = body.contentLength().coerceAtLeast(1L)
            var downloaded = 0L
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1 && isActive) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0.05f, 0.99f)
                        db.downloadDao().updateProgress(
                            contentId,
                            progress,
                            DownloadStatus.DOWNLOADING.name,
                            downloaded,
                            dest.absolutePath
                        )
                    }
                }
            }
            if (isActive) dest else null
        }
    }

    private suspend fun downloadHlsStream(
        contentId: String,
        masterUrl: String,
        headers: Map<String, String>,
        videoDir: File
    ): File? = withContext(Dispatchers.IO) {
        val masterContent = fetchText(masterUrl, headers) ?: return@withContext null
        val masterHttpUrl = masterUrl.toHttpUrlOrNull() ?: return@withContext null

        // If master playlist points to a variant playlist, pick the first variant
        val mediaPlaylistUrl = if (masterContent.contains("#EXT-X-STREAM-INF")) {
            val variantLine = masterContent.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@withContext null
            masterHttpUrl.resolve(variantLine)?.toString() ?: return@withContext null
        } else {
            masterUrl
        }

        val playlistText = if (mediaPlaylistUrl == masterUrl) {
            masterContent
        } else {
            fetchText(mediaPlaylistUrl, headers) ?: return@withContext null
        }
        val playlistHttpUrl = mediaPlaylistUrl.toHttpUrlOrNull() ?: return@withContext null

        val lines = playlistText.lines()
        val segmentUrls = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (segmentUrls.isEmpty()) return@withContext null

        val localPlaylistLines = mutableListOf<String>()
        var segmentIndex = 0
        var totalBytes = 0L

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                localPlaylistLines.add(rawLine)
                continue
            }
            if (!isActive) return@withContext null
            val resolved = playlistHttpUrl.resolve(trimmed)?.toString() ?: continue
            val segName = "seg_%04d.ts".format(segmentIndex)
            val segFile = File(videoDir, segName)
            val bytes = downloadBytesToFile(resolved, headers, segFile)
            if (bytes > 0) {
                totalBytes += bytes
                localPlaylistLines.add(segName)
            }
            segmentIndex++
            val progress = (segmentIndex.toFloat() / segmentUrls.size.toFloat()).coerceIn(0.05f, 0.99f)
            db.downloadDao().updateProgress(
                contentId = contentId,
                progress = progress,
                status = DownloadStatus.DOWNLOADING.name,
                sizeBytes = totalBytes,
                localPath = File(videoDir, "index.m3u8").absolutePath
            )
        }

        val localM3u8 = File(videoDir, "index.m3u8")
        localM3u8.writeText(localPlaylistLines.joinToString("\n"))
        localM3u8
    }

    private fun fetchText(url: String, headers: Map<String, String>): String? {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return apiClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }

    private fun downloadBytesToFile(url: String, headers: Map<String, String>, dest: File): Long {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return apiClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return 0L
            val body = resp.body ?: return 0L
            var written = 0L
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        written += read
                    }
                }
            }
            written
        }
    }
}
