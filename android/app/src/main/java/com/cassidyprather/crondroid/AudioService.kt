package com.cassidyprather.crondroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.Executors

/**
 * Plays a Twitch channel's audio in the background, so it works with the screen off and the phone locked.
 * If the channel isn't live yet, it keeps checking for [WAIT_FOR_LIVE_MS] before giving up.
 */
@OptIn(UnstableApi::class)
class AudioService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val retry = Runnable { lookUp() }
    private lateinit var session: MediaSession
    private var wakeLock: PowerManager.WakeLock? = null

    private var player: ExoPlayer? = null
    private var channel = ""
    private var title = ""
    private var status = ""
    private var giveUpAt = 0L
    private var failures = 0

    /** Bumped whenever we start over or stop, so stale lookups are ignored. */
    private var generation = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "Crondroid").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stop()
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (player?.isPlaying == true) pause() else resume()
            ACTION_STOP -> stop()
            else -> {
                val requested = intent?.getStringExtra(EXTRA_CHANNEL)
                if (requested == null) {
                    stop()
                } else {
                    channel = requested
                    title = intent.getStringExtra(EXTRA_TITLE) ?: requested
                    startOver("Connecting to $requested…", WAIT_FOR_LIVE_MS)
                    // Must happen within seconds of startForegroundService(), before any network I/O.
                    startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    failures = 0
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        session.release()
        io.shutdownNow()
        super.onDestroy()
    }

    /** Drops any current player and looks the stream up again, retrying until [waitMs] from now. */
    private fun startOver(message: String, waitMs: Long) {
        releasePlayer()
        status = message
        giveUpAt = SystemClock.elapsedRealtime() + waitMs
        // Keep the CPU awake while waiting; once playing, ExoPlayer holds its own wake lock.
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "crondroid:audio")
                .apply { acquire(waitMs + 60_000) }
        }
        session.isActive = true
        refresh()
        lookUp()
    }

    private fun lookUp() {
        val gen = ++generation
        val name = channel
        io.execute {
            val result = Twitch.findStream(name)
            main.post { if (gen == generation) onLookup(result) }
        }
    }

    private fun onLookup(result: Twitch.Lookup) {
        if (result is Twitch.Lookup.Live) {
            play(result)
            return
        }
        val offline = result is Twitch.Lookup.Offline
        if (SystemClock.elapsedRealtime() < giveUpAt) {
            status = if (offline) "Waiting for $channel to go live…" else "Can't reach Twitch; retrying…"
            refresh()
            main.postDelayed(retry, RETRY_MS)
        } else {
            finish(if (offline) "$channel isn't live." else "Couldn't play $channel: ${(result as Twitch.Lookup.Failed).reason}")
        }
    }

    private fun play(live: Twitch.Lookup.Live) {
        releasePlayer()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            setHandleAudioBecomingNoisy(true) // Pause when headphones are unplugged.
            setWakeMode(C.WAKE_MODE_NETWORK)
            if (!live.audioOnly) {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
            addListener(listener)
            setMediaItem(MediaItem.Builder().setUri(live.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            prepare()
            playWhenReady = true
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        status = "Live on Twitch · audio only"
        refresh()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) failures = 0
            refresh()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) finish("$channel's stream ended.")
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                current.seekToDefaultPosition()
                current.prepare()
            } else if (++failures > MAX_FAILURES) {
                finish("Couldn't play $channel: ${error.errorCodeName}")
            } else {
                // The playlist may have expired or the stream restarted: fetch a fresh one.
                startOver("Reconnecting…", RECONNECT_MS)
            }
        }
    }

    private fun pause() {
        player?.pause()
    }

    private fun resume() {
        val current = player ?: return
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        current.seekToDefaultPosition() // Jump back to live rather than resuming where we paused.
        current.play()
    }

    private fun stop() {
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Stops and leaves a notification saying why. */
    private fun finish(message: String) {
        stop()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            RESULT_NOTIFICATION_ID,
            Notification.Builder(this, ensureChannel())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun release() {
        generation++
        main.removeCallbacks(retry)
        releasePlayer()
        wakeLock?.let { if (it.isHeld) it.release() }
        session.isActive = false
    }

    private fun releasePlayer() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
    }

    private fun refresh() {
        val playing = player?.isPlaying == true
        val state = when {
            playing -> PlaybackState.STATE_PLAYING
            player != null && player?.playWhenReady == false -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_CONNECTING
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "twitch.tv/$channel")
                .build(),
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val playing = player?.isPlaying == true
        val builder = Notification.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        val compact = mutableListOf<Int>()
        if (player != null) {
            builder.addAction(
                action(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Play", ACTION_TOGGLE),
            )
            compact += 0
        }
        builder.addAction(action(R.drawable.ic_stop, "Stop", ACTION_STOP))
        compact += compact.size
        builder.setStyle(
            Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(*compact.toIntArray()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun action(icon: Int, label: String, action: String): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(this, icon),
        label,
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AudioService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel(): String {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audio playback", NotificationManager.IMPORTANCE_LOW),
        )
        return CHANNEL_ID
    }

    companion object {
        private const val ACTION_TOGGLE = "com.cassidyprather.crondroid.TOGGLE"
        private const val ACTION_STOP = "com.cassidyprather.crondroid.STOP"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "audio"
        // Schedule notifications use schedule ids (small positive numbers), so stay clear of them.
        private const val NOTIFICATION_ID = Int.MAX_VALUE - 1
        private const val RESULT_NOTIFICATION_ID = Int.MAX_VALUE - 2

        private const val WAIT_FOR_LIVE_MS = 15 * 60_000L
        private const val RECONNECT_MS = 2 * 60_000L
        private const val RETRY_MS = 30_000L
        private const val MAX_FAILURES = 3

        /**
         * Starts playing [schedule]'s Twitch channel. Returns false if it isn't a Twitch channel
         * or Android refused to start background playback.
         */
        fun start(context: Context, schedule: Schedule): Boolean {
            val channel = Twitch.channelFromUrl(schedule.url) ?: return false
            val intent = Intent(context, AudioService::class.java)
                .putExtra(EXTRA_CHANNEL, channel)
                .putExtra(EXTRA_TITLE, schedule.name.ifBlank { channel })
            return try {
                context.startForegroundService(intent)
                true
            } catch (_: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: we weren't allowed to start from the background.
                false
            }
        }
    }
}
