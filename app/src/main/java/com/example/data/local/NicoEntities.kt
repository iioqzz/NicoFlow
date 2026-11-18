package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DownloadStatus
import com.example.data.model.NicoVideo

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val ownerName: String,
    val lengthSeconds: Int,
    val viewCounter: Long,
    val commentCounter: Long,
    val mylistCounter: Long,
    val tagsCsv: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val status: String, // DownloadStatus.name
    val progress: Float, // 0f..1f
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        description = description,
        viewCounter = viewCounter,
        commentCounter = commentCounter,
        mylistCounter = mylistCounter,
        lengthSeconds = lengthSeconds,
        thumbnailUrl = thumbnailUrl,
        tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(","),
        ownerName = ownerName
    )

    fun downloadStatus(): DownloadStatus = runCatching {
        DownloadStatus.valueOf(status)
    }.getOrDefault(DownloadStatus.FAILED)
}

@Entity(tableName = "cached_comments")
data class CachedCommentEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val vposMs: Long,
    val text: String,
    val colorHex: Long,
    val position: String, // DanmakuPosition.name
    val isUserPosted: Boolean = false
) {
    fun toDanmakuComment(): DanmakuComment = DanmakuComment(
        id = id,
        videoId = videoId,
        vposMs = vposMs,
        text = text,
        colorHex = colorHex,
        position = runCatching { DanmakuPosition.valueOf(position) }.getOrDefault(DanmakuPosition.SCROLL),
        isUserPosted = isUserPosted
    )
}

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val lengthSeconds: Int,
    val viewCounter: Long,
    val commentCounter: Long,
    val lastPositionMs: Long = 0L,
    val watchedAt: Long = System.currentTimeMillis()
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        lengthSeconds = lengthSeconds,
        viewCounter = viewCounter,
        commentCounter = commentCounter
    )
}

@Entity(tableName = "mylist_videos")
data class MylistVideoEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val lengthSeconds: Int,
    val viewCounter: Long,
    val commentCounter: Long,
    val mylistCounter: Long,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        lengthSeconds = lengthSeconds,
        viewCounter = viewCounter,
        commentCounter = commentCounter,
        mylistCounter = mylistCounter
    )
}
