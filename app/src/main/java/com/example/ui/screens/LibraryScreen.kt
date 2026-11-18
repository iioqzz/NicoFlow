package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.MylistVideoEntity
import com.example.data.local.NicoAccount
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.NicoVideo
import com.example.ui.components.VideoCard

@Composable
fun LibraryScreen(
    account: NicoAccount,
    isSyncingAccount: Boolean,
    accountSyncMessage: String?,
    history: List<WatchHistoryEntity>,
    mylist: List<MylistVideoEntity>,
    downloadedIds: Set<String>,
    backgroundPlaybackEnabled: Boolean,
    darkTheme: Boolean,
    onLoginClick: () -> Unit,
    onSyncAccountClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onVideoClick: (NicoVideo) -> Unit,
    onDownloadClick: (NicoVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "我的与设置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Official Niconico Account Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (account.isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (account.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = account.avatarUrl,
                                    contentDescription = account.nickname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = account.nickname.ifBlank { "ニコニコ用户" },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (account.isPremium) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Premium",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "ID: ${account.userId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!accountSyncMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = accountSyncMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSyncAccountClick,
                                enabled = !isSyncingAccount,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_account_button")
                            ) {
                                if (isSyncingAccount) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("同步账号数据")
                            }
                            OutlinedButton(
                                onClick = onLogoutClick,
                                modifier = Modifier.testTag("logout_account_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("退出")
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "登录 ニコニコ 官方账号",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "同步官方观看历史、稍后再看列表，并解锁完整视频播放权限",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_nico_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即登录")
                        }
                    }
                }
            }
        }

        // Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "播放与外观",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "后台播放",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "最小化播放器或锁屏时继续播放音频",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backgroundPlaybackEnabled,
                            onCheckedChange = onBackgroundPlaybackChange,
                            modifier = Modifier.testTag("switch_background_playback")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "深色模式",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "更适合夜间观影与高对比度弹幕显示",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = onDarkThemeChange,
                            modifier = Modifier.testTag("switch_dark_theme")
                        )
                    }
                }
            }
        }

        // Mylist Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "收藏与稍后再看 (${mylist.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (mylist.isEmpty()) {
            item {
                Text(
                    text = "暂无收藏视频，可在播放页点击收藏或登录同步官方列表。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(mylist, key = { "mylist_${it.contentId}" }) { item ->
                val video = item.toNicoVideo()
                VideoCard(
                    video = video,
                    isDownloaded = downloadedIds.contains(video.contentId),
                    onClick = { onVideoClick(video) },
                    onDownloadClick = { onDownloadClick(video) }
                )
            }
        }

        // Watch History Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "观看历史 (${history.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("清空历史")
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "还没有观看记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history.take(15), key = { "hist_${it.contentId}" }) { item ->
                val video = item.toNicoVideo()
                VideoCard(
                    video = video,
                    isDownloaded = downloadedIds.contains(video.contentId),
                    onClick = { onVideoClick(video) },
                    onDownloadClick = { onDownloadClick(video) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
