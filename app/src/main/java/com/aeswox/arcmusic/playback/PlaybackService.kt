package com.aeswox.arcmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaCodecList
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aeswox.arcmusic.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.aeswox.arcmusic.data.SettingsRepository

@AndroidEntryPoint
@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        private const val DOLBY_DIAG_TAG = "ArcDolbyDiag"

        /**
         * Set to true while the player is actively playing.
         * Used by MediaScannerService to defer scanning during playback.
         */
        @Volatile var isCurrentlyPlaying: Boolean = false
    }

    @Inject
    lateinit var equalizerManager: EqualizerManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var usbDacOutputManager: UsbDacOutputManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        usbDacOutputManager.register()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            .build()
            
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000, 
                50000, 
                250, // bufferForPlaybackMs 
                500  // bufferForPlaybackAfterRebufferMs
            )
            .build()
            
        val extractorsFactory = com.aeswox.arcmusic.playback.extractor.CustomExtractorsFactory()
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory)
            
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        // NOTE: Audio offload MUST remain disabled when audio effects (EQ, BassBoost, Virtualizer)
        // are in use. Offload routes audio directly to hardware, bypassing the software DSP chain
        // entirely — effects are attached but receive no audio. This caused EQ to silently do
        // nothing on devices like Motorola Edge 60.
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setAudioOffloadPreferences(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                    .build()
            )
            .build()
            
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
                .also(usbDacOutputManager::attachSink)
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setDeviceVolumeControlEnabled(true)
            .build()

        val ffmpegAudioRendererIndex = (0 until player.rendererCount).firstOrNull { index ->
            player.getRenderer(index).name == "FfmpegAudioRenderer"
        }
        // Keep the new software path inert until the user explicitly enables it.
        ffmpegAudioRendererIndex?.let { rendererIndex ->
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setRendererDisabled(rendererIndex, true)
                .build()
        }

        if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
            val dolbyDecoderSummary = runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                    .filter { codecInfo ->
                        !codecInfo.isEncoder && codecInfo.supportedTypes.any { mime ->
                            mime.equals("audio/eac3", ignoreCase = true) ||
                                mime.equals("audio/eac3-joc", ignoreCase = true) ||
                                mime.equals("audio/ac3", ignoreCase = true)
                        }
                    }
                    .joinToString { codecInfo ->
                        "${codecInfo.name}[${codecInfo.supportedTypes.joinToString()}]"
                    }
                    .ifEmpty { "none" }
            }.getOrElse { "queryFailed:${it.javaClass.simpleName}:${it.message}" }
            Log.i(DOLBY_DIAG_TAG, "platformDolbyDecoders=$dolbyDecoderSummary")
        }
            
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                equalizerManager.attachToAudioSession(audioSessionId)
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
                    Log.i(
                        DOLBY_DIAG_TAG,
                        "audioInput eventWindow=${eventTime.windowIndex}/${eventTime.currentWindowIndex} " +
                            "currentId=${player.currentMediaItem?.mediaId} " +
                            "uri=${player.currentMediaItem?.localConfiguration?.uri} " +
                            "mime=${format.sampleMimeType} codecs=${format.codecs} " +
                            "channels=${format.channelCount} sampleRate=${format.sampleRate} " +
                            "bitrate=${format.averageBitrate}"
                    )
                }
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
                    Log.i(
                        DOLBY_DIAG_TAG,
                        "audioDecoder eventWindow=${eventTime.windowIndex}/${eventTime.currentWindowIndex} " +
                            "currentId=${player.currentMediaItem?.mediaId} " +
                            "uri=${player.currentMediaItem?.localConfiguration?.uri} " +
                            "name=$decoderName initMs=$initializationDurationMs"
                    )
                }
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception
            ) {
                if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
                    Log.e(
                        DOLBY_DIAG_TAG,
                        "audioSinkError eventWindow=${eventTime.windowIndex}/${eventTime.currentWindowIndex} " +
                            "currentId=${player.currentMediaItem?.mediaId} " +
                            "uri=${player.currentMediaItem?.localConfiguration?.uri}",
                        audioSinkError
                    )
                }
            }
        })

        if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.i(
                        DOLBY_DIAG_TAG,
                        "mediaItemTransition id=${mediaItem?.mediaId} " +
                            "uri=${mediaItem?.localConfiguration?.uri} " +
                            "title=${mediaItem?.mediaMetadata?.title} " +
                            "artist=${mediaItem?.mediaMetadata?.artist} reason=$reason"
                    )
                }

                override fun onTracksChanged(tracks: Tracks) {
                    tracks.groups.forEach { group ->
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (trackIndex in 0 until group.length) {
                                val format = group.getTrackFormat(trackIndex)
                                Log.i(
                                    DOLBY_DIAG_TAG,
                                    "audioTrack supported=${group.isTrackSupported(trackIndex)} " +
                                        "selected=${group.isTrackSelected(trackIndex)} " +
                                        "mime=${format.sampleMimeType} codecs=${format.codecs} " +
                                        "channels=${format.channelCount} sampleRate=${format.sampleRate} " +
                                        "bitrate=${format.averageBitrate}"
                                )
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(
                        DOLBY_DIAG_TAG,
                        "playerError code=${error.errorCodeName} message=${error.message} " +
                            "uri=${player.currentMediaItem?.localConfiguration?.uri}",
                        error
                    )
                }
            })
        }
        
        // Attach immediately in case the session ID is already assigned before the listener is added
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET && player.audioSessionId != 0) {
            equalizerManager.attachToAudioSession(player.audioSessionId)
        }
            
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(com.aeswox.arcmusic.R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        serviceScope.launch {
            settingsRepository.skipSilenceEnabled.collectLatest { enabled ->
                player.skipSilenceEnabled = enabled
            }
        }

        serviceScope.launch {
            settingsRepository.usbDacEnabled.collectLatest { enabled ->
                usbDacOutputManager.setEnabled(enabled)
                equalizerManager.setOutputDisabledByUsb(enabled)
            }
        }

        serviceScope.launch {
            settingsRepository.ffmpegEac3Enabled.collectLatest { enabled ->
                if (ffmpegAudioRendererIndex != null) {
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setRendererDisabled(ffmpegAudioRendererIndex, !enabled)
                        .build()
                    if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
                        Log.i(
                            DOLBY_DIAG_TAG,
                            "ffmpegEac3FallbackEnabled=$enabled rendererIndex=$ffmpegAudioRendererIndex"
                        )
                    }
                } else if (com.aeswox.arcmusic.BuildConfig.DEBUG) {
                    Log.e(DOLBY_DIAG_TAG, "ffmpegEac3FallbackUnavailable enabled=$enabled")
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        usbDacOutputManager.detachSink()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
