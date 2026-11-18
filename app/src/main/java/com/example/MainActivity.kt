package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.DownloadStatus
import com.example.ui.MainTab
import com.example.ui.NicoViewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LiveScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.NicoFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NicoFlowRoot()
        }
    }
}

@Composable
fun NicoFlowRoot(viewModel: NicoViewModel = viewModel()) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isPlayerOpen by viewModel.isPlayerScreenOpen.collectAsStateWithLifecycle()
    val isLoginOpen by viewModel.isLoginScreenOpen.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val isSyncingAccount by viewModel.isSyncingAccount.collectAsStateWithLifecycle()
    val accountSyncMessage by viewModel.accountSyncMessage.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val comments by viewModel.currentComments.collectAsStateWithLifecycle()
    val danmakuSettings by viewModel.danmakuSettings.collectAsStateWithLifecycle()
    val isLoadingVideo by viewModel.isLoadingVideo.collectAsStateWithLifecycle()
    val isInMylist by viewModel.isCurrentInMylist.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val mylist by viewModel.mylist.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled by viewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()

    val downloadedIds = remember(downloads) {
        downloads.filter { it.downloadStatus() == DownloadStatus.COMPLETED }
            .map { it.contentId }
            .toSet()
    }

    // Request POST_NOTIFICATIONS on Android 13+ for background playback media notification
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NicoFlowTheme(darkTheme = darkTheme) {
        if (isLoginOpen) {
            LoginScreen(
                onLoginSuccess = viewModel::onLoginSuccess,
                onBack = viewModel::closeLoginScreen
            )
        } else if (isPlayerOpen && playerState.currentVideo != null) {
            val currentId = playerState.currentVideo?.contentId
            PlayerScreen(
                player = viewModel.playerController.player,
                playerState = playerState,
                comments = comments,
                danmakuSettings = danmakuSettings,
                isLoadingVideo = isLoadingVideo,
                isInMylist = isInMylist,
                isDownloaded = currentId != null && downloadedIds.contains(currentId),
                onBack = viewModel::minimizePlayerScreen,
                onTogglePlayPause = viewModel.playerController::togglePlayPause,
                onSeekTo = viewModel.playerController::seekTo,
                onSeekBy = viewModel.playerController::seekBy,
                onSpeedChange = viewModel.playerController::setPlaybackSpeed,
                onToggleDanmaku = viewModel::toggleDanmakuEnabled,
                onUpdateDanmakuSettings = viewModel::updateDanmakuSettings,
                onPostDanmaku = viewModel::postDanmaku,
                onWebServerResponse = viewModel::onWebServerResponse,
                onWebStreamResolved = viewModel::onWebStreamResolved,
                onToggleMylist = viewModel::toggleCurrentMylist,
                onDownloadClick = viewModel::downloadVideo,
                onTagClick = viewModel::searchByTag
            )
        } else {
            Scaffold(
                bottomBar = {
                    Column {
                        if (playerState.currentVideo != null) {
                            MiniPlayerBar(
                                playerState = playerState,
                                onOpenPlayer = viewModel::openPlayerScreen,
                                onTogglePlayPause = viewModel.playerController::togglePlayPause,
                                onClose = viewModel.playerController::stopAndClear
                            )
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.HOME,
                                onClick = { viewModel.selectTab(MainTab.HOME) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == MainTab.HOME) Icons.Rounded.Home else Icons.Outlined.Home,
                                        contentDescription = "首页"
                                    )
                                },
                                label = { Text("首页") },
                                modifier = Modifier.testTag("nav_home")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.LIVE,
                                onClick = { viewModel.selectTab(MainTab.LIVE) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == MainTab.LIVE) Icons.Rounded.LiveTv else Icons.Outlined.LiveTv,
                                        contentDescription = "直播"
                                    )
                                },
                                label = { Text("直播") },
                                modifier = Modifier.testTag("nav_live")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.SEARCH,
                                onClick = { viewModel.selectTab(MainTab.SEARCH) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == MainTab.SEARCH) Icons.Rounded.Search else Icons.Outlined.Search,
                                        contentDescription = "搜索"
                                    )
                                },
                                label = { Text("搜索") },
                                modifier = Modifier.testTag("nav_search")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.DOWNLOADS,
                                onClick = { viewModel.selectTab(MainTab.DOWNLOADS) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == MainTab.DOWNLOADS) Icons.Rounded.DownloadForOffline else Icons.Outlined.DownloadForOffline,
                                        contentDescription = "离线"
                                    )
                                },
                                label = { Text("离线") },
                                modifier = Modifier.testTag("nav_downloads")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.LIBRARY,
                                onClick = { viewModel.selectTab(MainTab.LIBRARY) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == MainTab.LIBRARY) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = "我的"
                                    )
                                },
                                label = { Text("我的") },
                                modifier = Modifier.testTag("nav_library")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        MainTab.HOME -> HomeScreen(
                            state = homeState,
                            liveState = liveState,
                            downloadedIds = downloadedIds,
                            onSelectFeedTab = viewModel::selectHomeFeedTab,
                            onSelectCategory = { viewModel.loadCategory(it) },
                            onSelectLiveCategory = viewModel::loadLiveCategory,
                            onHotDaysChange = viewModel::setHotRecentDays,
                            onRefresh = viewModel::refreshHomeFeed,
                            onLoadMore = viewModel::loadMoreHomeFeed,
                            onNotInterested = viewModel::markNotInterested,
                            onVideoClick = viewModel::playVideo,
                            onDownloadClick = viewModel::downloadVideo
                        )

                        MainTab.LIVE -> LiveScreen(
                            state = liveState,
                            onSelectCategory = viewModel::loadLiveCategory,
                            onRefresh = { viewModel.loadLiveCategory(liveState.selectedCategory) },
                            onLiveClick = viewModel::playLive
                        )

                        MainTab.SEARCH -> SearchScreen(
                            state = searchState,
                            downloadedIds = downloadedIds,
                            onQueryChange = viewModel::updateSearchQuery,
                            onSearch = viewModel::performSearch,
                            onSortChange = viewModel::updateSearchSort,
                            onVideoClick = viewModel::playVideo,
                            onDownloadClick = viewModel::downloadVideo
                        )

                        MainTab.DOWNLOADS -> DownloadsScreen(
                            downloads = downloads,
                            onPlayOffline = viewModel::playVideo,
                            onDeleteDownload = viewModel::deleteDownload
                        )

                        MainTab.LIBRARY -> LibraryScreen(
                            account = account,
                            isSyncingAccount = isSyncingAccount,
                            accountSyncMessage = accountSyncMessage,
                            history = history,
                            mylist = mylist,
                            downloadedIds = downloadedIds,
                            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                            darkTheme = darkTheme,
                            onLoginClick = viewModel::openLoginScreen,
                            onSyncAccountClick = viewModel::syncAccountNow,
                            onLogoutClick = viewModel::logoutAccount,
                            onBackgroundPlaybackChange = viewModel::setBackgroundPlaybackEnabled,
                            onDarkThemeChange = viewModel::setDarkTheme,
                            onClearHistory = viewModel::clearHistory,
                            onVideoClick = viewModel::playVideo,
                            onDownloadClick = viewModel::downloadVideo
                        )
                    }
                }
            }
        }
    }
}
