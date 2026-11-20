package com.musicfree.demo.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val currentUrl: String? = null
)

/**
 * 基于 Media3 ExoPlayer 的最小播放验证器，支持将插件返回的 headers / userAgent 透传给 OkHttpDataSource。
 */
class DemoAudioPlayer(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private var exoPlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    @OptIn(UnstableApi::class)
    fun play(
        title: String,
        url: String,
        headers: Map<String, String>,
        userAgent: String?
    ) {
        release()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            val merged = headers.toMutableMap()
            if (!userAgent.isNullOrBlank()) {
                merged["User-Agent"] = userAgent
            }
            if (merged.isNotEmpty()) {
                setDefaultRequestProperties(merged)
            }
        }
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()

        exoPlayer = player
        _state.value = PlaybackState(
            isPlaying = true,
            currentTitle = title,
            currentUrl = url
        )
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _state.value = PlaybackState()
    }
}
