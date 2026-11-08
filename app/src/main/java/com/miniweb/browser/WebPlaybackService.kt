package com.miniweb.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.webkit.WebView

/**
 * Owns the Android media session and foreground notification while HTML5
 * audio/video is playing in Mini Web's normal browsing window.
 *
 * The actual media remains owned by the website/WebView. This service exposes
 * transport controls and keeps the browser process eligible for playback.
 */
class WebPlaybackService : Service() {

    private lateinit var session: MediaSession
    private lateinit var notificationManager: NotificationManager

    private var mediaTitle: String = "Web video"
    private var mediaArtist: String = "Mini Web"
    private var mediaUrl: String = ""
    private var isPlaying: Boolean = true
    private var foregroundStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()

        session = MediaSession(this, "MiniWebMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = dispatchMediaCommand("play")
                override fun onPause() = dispatchMediaCommand("pause")
                override fun onStop() = dispatchMediaCommand("pause")
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlaybackService()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                isPlaying = false
                runMediaCommand("pause")
            }

            ACTION_PLAY -> {
                isPlaying = true
                runMediaCommand("play")
            }

            ACTION_UPDATE, ACTION_START, null -> {
                intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    mediaTitle = it
                }
                intent?.getStringExtra(EXTRA_URL)?.let { mediaUrl = it }
                intent?.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                    mediaArtist = it
                }
                if (intent?.hasExtra(EXTRA_PLAYING) == true) {
                    isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, isPlaying)
                }
            }
        }

        publishSessionState()
        val notification = buildNotification()

        if (!foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun dispatchMediaCommand(command: String) {
        isPlaying = command == "play"
        runMediaCommand(command)
        publishSessionState()
        if (foregroundStarted) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun publishSessionState() {
        if (!::session.isInitialized) return

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, mediaTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, mediaArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Mini Web")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, mediaUrl)
                .build()
        )

        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (isPlaying) 1f else 0f
                )
                .build()
        )
        session.isActive = true
    }

    private fun buildNotification(): Notification {
        val pauseOrPlayAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val pauseOrPlayLabel = if (isPlaying) "Pause" else "Play"
        val pauseOrPlayIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WebPlaybackService::class.java).setAction(pauseOrPlayAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val launchIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mini_web)
            .setContentTitle(mediaTitle)
            .setContentText("$mediaArtist · Mini Web")
            .setSubText("Playing in Mini Web")
            .setContentIntent(launchIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(pauseOrPlayIcon, pauseOrPlayLabel, toggleIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun runMediaCommand(command: String) {
        val view = activeWebView?.get() ?: return
        view.post {
            val action = if (command == "play") {
                """
                (function() {
                  function control(doc) {
                    try {
                      doc.querySelectorAll('video,audio').forEach(function(media) {
                        if (media.paused) {
                          var promise = media.play();
                          if (promise && promise.catch) promise.catch(function(){});
                        }
                      });
                      for (var i = 0; i < doc.querySelectorAll('iframe').length; i++) {
                        try {
                          var child = doc.querySelectorAll('iframe')[i].contentDocument;
                          if (child) control(child);
                        } catch (e) {}
                      }
                    } catch (e) {}
                  }
                  control(document);
                })();
                """.trimIndent()
            } else {
                """
                (function() {
                  function control(doc) {
                    try {
                      doc.querySelectorAll('video,audio').forEach(function(media) {
                        media.pause();
                      });
                      for (var i = 0; i < doc.querySelectorAll('iframe').length; i++) {
                        try {
                          var child = doc.querySelectorAll('iframe')[i].contentDocument;
                          if (child) control(child);
                        } catch (e) {}
                      }
                    } catch (e) {}
                  }
                  control(document);
                })();
                """.trimIndent()
            }
            view.evaluateJavascript(action, null)
        }
    }

    private fun stopPlaybackService() {
        isPlaying = false
        runMediaCommand("pause")
        if (::session.isInitialized) {
            session.isActive = false
        }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Mini Web media playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls for audio and video playing in Mini Web"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.miniweb.browser.media.START"
        const val ACTION_UPDATE = "com.miniweb.browser.media.UPDATE"
        const val ACTION_STOP = "com.miniweb.browser.media.STOP"
        const val ACTION_PLAY = "com.miniweb.browser.media.PLAY"
        const val ACTION_PAUSE = "com.miniweb.browser.media.PAUSE"

        private const val EXTRA_TITLE = "media_title"
        private const val EXTRA_ARTIST = "media_artist"
        private const val EXTRA_URL = "media_url"
        private const val EXTRA_PLAYING = "media_playing"
        private const val CHANNEL_ID = "mini_web_media"
        private const val NOTIFICATION_ID = 3107

        @Volatile
        private var activeWebView: java.lang.ref.WeakReference<WebView>? = null

        fun attach(view: WebView) {
            activeWebView = java.lang.ref.WeakReference(view)
        }

        fun detach(view: WebView) {
            if (activeWebView?.get() === view) activeWebView = null
        }

        fun start(
            context: Context,
            title: String = "Web video",
            url: String = "",
            artist: String = "Mini Web"
        ) {
            val intent = Intent(context, WebPlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PLAYING, true)
            context.startForegroundService(intent)
        }

        fun update(
            context: Context,
            title: String,
            url: String,
            isPlaying: Boolean,
            artist: String = "Mini Web"
        ) {
            val intent = Intent(context, WebPlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PLAYING, isPlaying)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WebPlaybackService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
