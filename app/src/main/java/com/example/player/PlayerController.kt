package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.NicoVideo
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentVideo: NicoVideo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isOfflinePlayback: Boolean = false,
    val isLive: Boolean = false,
    val isBackgroundAudioMode: Boolean = false,
    val useWebEmbedFallback: Boolean = false,
    val statusMessage: String? = null
)

class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val player: Player?
        get() = mediaController

    init {
        connectToService()
        startPositionTicker()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, NicoPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                runCatching {
                    val controller = controllerFuture?.get() ?: return@runCatching
                    mediaController = controller
                    controller.addListener(playerListener)
                    syncFromPlayer(controller)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: it.durationMs
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    useWebEmbedFallback = !it.isOfflinePlayback,
                    statusMessage = null
                )
            }
        }
    }

    private fun syncFromPlayer(controller: Player) {
        _state.update {
            it.copy(
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L)
            )
        }
    }

    private fun startPositionTicker() {
        scope.launch {
            while (isActive) {
                mediaController?.let { p ->
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.coerceAtLeast(0L)
                    _state.update {
                        if (it.currentPositionMs != pos || it.durationMs != dur) {
                            it.copy(currentPositionMs = pos, durationMs = dur)
                        } else {
                            it
                        }
                    }
                }
                delay(100L)
            }
        }
    }

    fun playStream(
        video: NicoVideo,
        streamUrl: String?,
        isOffline: Boolean,
        statusMessage: String?
    ) {
        val shouldUseEmbed = streamUrl.isNullOrBlank() && !isOffline
        _state.update {
            it.copy(
                currentVideo = video,
                isOfflinePlayback = isOffline,
                isLive = video.isLive,
                isBackgroundAudioMode = false,
                useWebEmbedFallback = shouldUseEmbed,
                statusMessage = if (shouldUseEmbed) null else statusMessage,
                currentPositionMs = 0L,
                durationMs = if (video.isLive) 0L else video.lengthSeconds * 1000L
            )
        }

        val controller = mediaController ?: return
        if (streamUrl.isNullOrBlank()) {
            controller.stop()
            controller.clearMediaItems()
            return
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.ownerName.ifBlank { "ニコニコ動画 (${video.contentId})" })
            .setArtworkUri(video.thumbnailUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(video.contentId)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        seekTo(controller.currentPosition + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setBackgroundAudioMode(enabled: Boolean) {
        _state.update { it.copy(isBackgroundAudioMode = enabled) }
    }

    fun stopAndClear() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _state.value = PlayerUiState()
    }
}
