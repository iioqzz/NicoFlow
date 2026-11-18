package com.example.data.model

data class NicoVideo(
    val contentId: String,
    val title: String,
    val description: String = "",
    val viewCounter: Long = 0L,
    val commentCounter: Long = 0L,
    val mylistCounter: Long = 0L,
    val lengthSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val startTime: String = "",
    val tags: List<String> = emptyList(),
    val categoryTag: String = "",
    val ownerName: String = "",
    val isLive: Boolean = false
)

enum class NicoLiveCategory(val label: String, val tabParam: String, val fallbackQuery: String) {
    ALL("综合直播", "common", "雑談 OR ゲーム OR 音楽"),
    OFFICIAL("官方生放送", "official", "公式生放送"),
    GAME("游戏直播", "game", "ゲーム"),
    VTUBER("VTuber", "vtuber", "VTuber"),
    MUSIC("音乐演唱", "music", "歌ってみた OR 音楽"),
    TRY("杂谈企划", "try", "雑談")
}

enum class NicoCategory(val label: String, val queryTag: String) {
    ALL("综合热门", ""),
    BANGUMI("番剧", "アニメ"),
    VOCALOID("VOCALOID", "VOCALOID"),
    GAME("游戏", "ゲーム"),
    ANIME("动画", "アニメ"),
    MUSIC("音乐", "音楽"),
    ENTERTAINMENT("娱乐", "エンターテイメント"),
    TECH("科技", "ニコニコ技術部")
}

enum class NicoSortOrder(val label: String, val apiParam: String) {
    VIEWS("播放最多", "-viewCounter"),
    COMMENTS("弹幕最多", "-commentCounter"),
    NEWEST("最新投稿", "-startTime"),
    MYLIST("收藏最多", "-mylistCounter")
}

enum class DanmakuPosition {
    SCROLL, // naka (right-to-left)
    TOP,    // ue
    BOTTOM  // shita
}

data class DanmakuComment(
    val id: String,
    val videoId: String,
    val vposMs: Long,
    val text: String,
    val colorHex: Long = 0xFFFFFFFFL,
    val position: DanmakuPosition = DanmakuPosition.SCROLL,
    val isUserPosted: Boolean = false
)

data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacity: Float = 0.88f,
    val speedMultiplier: Float = 1.0f,
    val fontSizeSp: Float = 16f,
    val displayAreaFraction: Float = 0.8f
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class StreamInfo(
    val videoId: String,
    val streamUrl: String?,
    val headers: Map<String, String> = emptyMap(),
    val ownerName: String = "",
    val fullDescription: String = "",
    val isOffline: Boolean = false,
    val statusMessage: String? = null
)

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
        count >= 10_000 -> "%.1f万".format(count / 10_000.0)
        else -> count.toString()
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
