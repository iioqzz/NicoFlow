package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.NicoCategory
import com.example.data.model.NicoLiveCategory
import com.example.data.model.NicoVideo
import com.example.data.model.formatCount
import com.example.data.model.formatDuration
import com.example.ui.HomeFeedTab
import com.example.ui.HomeUiState
import com.example.ui.LiveUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    liveState: LiveUiState,
    downloadedIds: Set<String>,
    onSelectFeedTab: (HomeFeedTab) -> Unit,
    onSelectCategory: (NicoCategory) -> Unit,
    onSelectLiveCategory: (NicoLiveCategory) -> Unit,
    onHotDaysChange: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNotInterested: (NicoVideo) -> Unit,
    onVideoClick: (NicoVideo) -> Unit,
    onDownloadClick: (NicoVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    var showDaysDialog by remember { mutableStateOf(false) }
    var daysInputText by remember { mutableStateOf("") }

    // Trigger infinite scroll when approaching the bottom of the grid
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 4 && lastVisible >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.feedTab) {
        if (shouldLoadMore && state.feedTab != HomeFeedTab.LIVE) {
            onLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Bilibili-style centered top tab bar: 直播 | 推荐 | 热门
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeFeedTab.entries.forEach { tab ->
                    val selected = state.feedTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectFeedTab(tab) }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("home_tab_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (selected) 17.sp else 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent
                                )
                        )
                    }
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("home_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "换一批",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Category chips row
        if (state.feedTab == HomeFeedTab.LIVE) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(NicoLiveCategory.entries) { category ->
                    val selected = liveState.selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLiveCategory(category) },
                        label = { Text(category.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(NicoCategory.entries) { category ->
                    val selected = state.selectedCategory == category
                    val chipLabel = if (
                        category == NicoCategory.ALL &&
                        state.feedTab == HomeFeedTab.HOT &&
                        state.hotRecentDays != null
                    ) {
                        "${category.label} · ${state.hotRecentDays}天"
                    } else {
                        category.label
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (category == NicoCategory.ALL && selected && state.feedTab == HomeFeedTab.HOT) {
                                daysInputText = state.hotRecentDays?.toString().orEmpty()
                                showDaysDialog = true
                            } else {
                                onSelectCategory(category)
                            }
                        },
                        label = { Text(chipLabel, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val isLoading = if (state.feedTab == HomeFeedTab.LIVE) liveState.isLoading else state.isLoading
        val isRefreshing = if (state.feedTab == HomeFeedTab.LIVE) liveState.isLoading else state.isRefreshing
        val errorMessage = if (state.feedTab == HomeFeedTab.LIVE) liveState.errorMessage else state.errorMessage
        val itemsList = if (state.feedTab == HomeFeedTab.LIVE) liveState.lives else state.videos

        when {
            isLoading && itemsList.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            errorMessage != null && itemsList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRefresh) {
                            Text("重新加载")
                        }
                    }
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_video_grid")
                    ) {
                        items(itemsList, key = { it.contentId }) { video ->
                            BiliStyleVideoCard(
                                video = video,
                                isDownloaded = downloadedIds.contains(video.contentId),
                                onClick = { onVideoClick(video) },
                                onDownloadClick = { onDownloadClick(video) },
                                onNotInterested = { onNotInterested(video) }
                            )
                        }

                        if (state.isLoadingMore && state.feedTab != HomeFeedTab.LIVE) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDaysDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDaysDialog = false },
            title = { Text("热门发布时间范围（天数）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "输入最近天数以筛选热门视频，留空或设为 0 表示不限时间。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = daysInputText,
                        onValueChange = { input ->
                            daysInputText = input.filter { it.isDigit() }.take(4)
                        },
                        placeholder = { Text("例如: 7 / 30 / 365（留空不限）") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hot_days_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "不限", 7 to "7天", 30 to "30天", 365 to "1年").forEach { (d, label) ->
                            FilterChip(
                                selected = daysInputText == (d?.toString() ?: ""),
                                onClick = { daysInputText = d?.toString().orEmpty() },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val parsed = daysInputText.toIntOrNull()?.takeIf { it > 0 }
                        onHotDaysChange(parsed)
                        showDaysDialog = false
                    },
                    modifier = Modifier.testTag("hot_days_confirm")
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDaysDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BiliStyleVideoCard(
    video: NicoVideo,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onNotInterested: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_card_${video.contentId}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:10 Cover with bottom gradient stats bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (video.isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE53935))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Bottom gradient overlay with views, comments, and duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (video.isLive) Icons.Outlined.Visibility else Icons.Outlined.PlayCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatCount(video.viewCounter),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatCount(video.commentCounter),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    if (!video.isLive && video.lengthSeconds > 0) {
                        Text(
                            text = formatDuration(video.lengthSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // Title + Author/Tag footer + Bilibili-style More (⋮) menu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val subtitle = video.ownerName.ifBlank {
                            video.tags.firstOrNull() ?: video.contentId
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!video.isLive) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("more_btn_${video.contentId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isDownloaded) "已离线缓存" else "离线下载") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Outlined.Download,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDownloadClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("不感兴趣") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Block,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onNotInterested()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
