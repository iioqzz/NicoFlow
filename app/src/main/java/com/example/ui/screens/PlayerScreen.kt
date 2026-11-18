package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DanmakuSettings
import com.example.data.model.NicoVideo
import com.example.data.model.formatCount
import com.example.data.model.formatDuration
import com.example.player.PlayerUiState
import com.example.ui.components.DanmakuOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    player: Player?,
    playerState: PlayerUiState,
    comments: List<DanmakuComment>,
    danmakuSettings: DanmakuSettings,
    isLoadingVideo: Boolean,
    isInMylist: Boolean,
    isDownloaded: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleDanmaku: () -> Unit,
    onUpdateDanmakuSettings: ((DanmakuSettings) -> DanmakuSettings) -> Unit,
    onPostDanmaku: (String, Long, DanmakuPosition) -> Unit,
    onToggleMylist: () -> Unit,
    onDownloadClick: (NicoVideo) -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val video = playerState.currentVideo ?: return
    var showControls by remember { mutableStateOf(true) }
    var showDanmakuSheet by remember { mutableStateOf(false) }
    var selectedInfoTab by remember { mutableIntStateOf(0) }
    var commentInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(0xFFFFFFFFL) }
    var selectedPosition by remember { mutableStateOf(DanmakuPosition.SCROLL) }

    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val danmakuColors = listOf(
        0xFFFFFFFFL,
        0xFF00D4FFL,
        0xFFFF4D6DL,
        0xFFFFE600L,
        0xFF34C759L,
        0xFFC084FCL
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 16:9 Video + Danmaku + Custom HUD (or Official Niconico Embed WebView Fallback)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .clickable(
                    enabled = !playerState.useWebEmbedFallback,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = !showControls }
        ) {
            if (playerState.useWebEmbedFallback) {
                val embedUrl = if (video.isLive) {
                    "https://live.nicovideo.jp/embed/${video.contentId}"
                } else {
                    "https://embed.nicovideo.jp/watch/${video.contentId}"
                }
                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            val cm = android.webkit.CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = android.webkit.WebViewClient()
                            loadUrl(embedUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != embedUrl) {
                            webView.loadUrl(embedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Back button overlay for WebView embed mode
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            } else if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { view ->
                        if (view.player !== player) {
                            view.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live Danmaku Overlay (for native player mode)
            if (!playerState.useWebEmbedFallback) {
                DanmakuOverlay(
                    comments = comments,
                    currentPositionMs = playerState.currentPositionMs,
                    settings = danmakuSettings,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Loading / Status Overlay
            if (isLoadingVideo || (!playerState.useWebEmbedFallback && playerState.isBuffering)) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (!isLoadingVideo && !playerState.useWebEmbedFallback && playerState.statusMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = playerState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            // Custom HUD Controls (for native player mode)
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls && !playerState.useWebEmbedFallback,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回并后台播放",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onToggleDanmaku,
                            modifier = Modifier.testTag("player_danmaku_toggle")
                        ) {
                            Icon(
                                imageVector = if (danmakuSettings.enabled) Icons.Outlined.Subtitles else Icons.Outlined.SubtitlesOff,
                                contentDescription = "弹幕开关",
                                tint = if (danmakuSettings.enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(
                            onClick = { showDanmakuSheet = true },
                            modifier = Modifier.testTag("player_danmaku_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "弹幕设置",
                                tint = Color.White
                            )
                        }
                    }

                    // Center transport controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSeekBy(-10_000L) }) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "后退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = { onSeekBy(10_000L) }) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "快进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom seek bar + speed (or LIVE badge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playerState.isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE53935))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "LIVE · 生放送中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            Text(
                                text = formatDuration((playerState.currentPositionMs / 1000L).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            val duration = playerState.durationMs.coerceAtLeast(1L)
                            Slider(
                                value = (playerState.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                onValueChange = { fraction ->
                                    onSeekTo((fraction * duration).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = formatDuration((duration / 1000L).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            TextButton(
                                onClick = {
                                    val nextIdx = (speedOptions.indexOf(playerState.playbackSpeed) + 1) % speedOptions.size
                                    onSpeedChange(speedOptions[nextIdx])
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "${playerState.playbackSpeed}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Row (Background Audio, Mylist, Download, Offline badge)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_bg_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Headphones,
                        contentDescription = "后台播放",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("后台播放", style = MaterialTheme.typography.labelLarge)
                }

                TextButton(
                    onClick = onToggleMylist,
                    modifier = Modifier.testTag("player_mylist_button")
                ) {
                    Icon(
                        imageVector = if (isInMylist) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isInMylist) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isInMylist) "已收藏" else "收藏", style = MaterialTheme.typography.labelLarge)
                }

                if (!playerState.isLive) {
                    TextButton(
                        onClick = { onDownloadClick(video) },
                        modifier = Modifier.testTag("player_download_button")
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Outlined.Download,
                            contentDescription = "离线下载",
                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDownloaded) "已离线" else "离线下载", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Tabs: Details vs Danmaku List
        TabRow(selectedTabIndex = selectedInfoTab) {
            Tab(
                selected = selectedInfoTab == 0,
                onClick = { selectedInfoTab = 0 },
                text = { Text("简介") }
            )
            Tab(
                selected = selectedInfoTab == 1,
                onClick = { selectedInfoTab = 1 },
                text = { Text("弹幕 (${comments.size})") }
            )
        }

        // Content Area
        if (selectedInfoTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            if (video.ownerName.isNotBlank()) append("${video.ownerName} · ")
                            append("播放 ${formatCount(video.viewCounter)} · 弹幕 ${formatCount(video.commentCounter)} · 收藏 ${formatCount(video.mylistCounter)}")
                            if (playerState.isOfflinePlayback) append(" · 离线模式")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (video.tags.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            video.tags.take(12).forEach { tag ->
                                AssistChip(
                                    onClick = { onTagClick(tag) },
                                    label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }

                if (video.description.isNotBlank()) {
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = video.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "暂无弹幕，发一条弹幕试试吧！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(c.vposMs) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration((c.vposMs / 1000L).toInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(46.dp)
                            )
                            Text(
                                text = c.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Send Danmaku Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    danmakuColors.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (selectedColor == colorHex) 2.dp else 1.dp,
                                    color = if (selectedColor == colorHex) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    DanmakuPosition.entries.forEach { pos ->
                        val label = when (pos) {
                            DanmakuPosition.SCROLL -> "滚动"
                            DanmakuPosition.TOP -> "顶部"
                            DanmakuPosition.BOTTOM -> "底部"
                        }
                        FilterChip(
                            selected = selectedPosition == pos,
                            onClick = { selectedPosition = pos },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("发送弹幕...", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("danmaku_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                onPostDanmaku(commentInput, selectedColor, selectedPosition)
                                commentInput = ""
                            }
                        },
                        modifier = Modifier.testTag("danmaku_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送弹幕",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showDanmakuSheet) {
        ModalBottomSheet(onDismissRequest = { showDanmakuSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("弹幕显示设置", style = MaterialTheme.typography.titleLarge)

                Text("不透明度: ${(danmakuSettings.opacity * 100).toInt()}%")
                Slider(
                    value = danmakuSettings.opacity,
                    onValueChange = { v -> onUpdateDanmakuSettings { it.copy(opacity = v) } },
                    valueRange = 0.2f..1.0f
                )

                Text("弹幕速度: ${"%.1f".format(danmakuSettings.speedMultiplier)}x")
                Slider(
                    value = danmakuSettings.speedMultiplier,
                    onValueChange = { v -> onUpdateDanmakuSettings { it.copy(speedMultiplier = v) } },
                    valueRange = 0.5f..2.0f
                )

                Text("字体大小: ${danmakuSettings.fontSizeSp.toInt()}sp")
                Slider(
                    value = danmakuSettings.fontSizeSp,
                    onValueChange = { v -> onUpdateDanmakuSettings { it.copy(fontSizeSp = v) } },
                    valueRange = 12f..24f
                )

                Text("显示区域: ${(danmakuSettings.displayAreaFraction * 100).toInt()}%")
                Slider(
                    value = danmakuSettings.displayAreaFraction,
                    onValueChange = { v -> onUpdateDanmakuSettings { it.copy(displayAreaFraction = v) } },
                    valueRange = 0.25f..1.0f
                )
            }
        }
    }
}
