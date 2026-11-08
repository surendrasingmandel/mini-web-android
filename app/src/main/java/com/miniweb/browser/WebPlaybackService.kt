package com.miniweb.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.webkit.WebView
import com.miniweb.browser.R
import java.lang.ref.WeakReference

/** Keeps the browser process in media-playback foreground mode while WebView media is playing. */
class WebPlaybackService : Service() {
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "MiniWebMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { runMediaCommand("play"); publishState(PlaybackState.STATE_PLAYING) }
                override fun onPause() { runMediaCommand("pause"); publishState(PlaybackState.STATE_PAUSED) }
                override fun onStop() { runMediaCommand("pause"); stopSelf() }
            })
            isActive = true
        }
        publishState(PlaybackState.STATE_PLAYING)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> { runMediaCommand("pause"); publishState(PlaybackState.STATE_PAUSED) }
            ACTION_PLAY -> { runMediaCommand("play"); publishState(PlaybackState.STATE_PLAYING) }
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pause = PendingIntent.getService(this, 1, Intent(this, WebPlaybackService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val play = PendingIntent.getService(this, 2, Intent(this, WebPlaybackService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val close = PendingIntent.getService(this, 3, Intent(this, WebPlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val launch = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mini_web)
            .setContentTitle("Mini Web")
            .setContentText("Media playing in browser")
            .setContentIntent(launch)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pause)
            .addAction(android.R.drawable.ic_media_play, "Play", play)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", close)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun publishState(state: Int) {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Mini Web browser media").putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Mini Web").build())
    }

    private fun runMediaCommand(command: String) {
        activeWebView?.get()?.post {
            val js = if (command == "play") "document.querySelectorAll('video,audio').forEach(e=>{if(e.paused)e.play().catch(()=>{});});" else "document.querySelectorAll('video,audio').forEach(e=>e.pause());"
            activeWebView?.get()?.evaluateJavascript(js, null)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Browser media playback", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) { session.isActive = false; session.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.miniweb.browser.media.START"
        const val ACTION_STOP = "com.miniweb.browser.media.STOP"
        const val ACTION_PLAY = "com.miniweb.browser.media.PLAY"
        const val ACTION_PAUSE = "com.miniweb.browser.media.PAUSE"
        private const val CHANNEL_ID = "mini_web_media"
        private const val NOTIFICATION_ID = 3107
        @Volatile private var activeWebView: WeakReference<WebView>? = null
        fun attach(view: WebView) { activeWebView = WeakReference(view) }
        fun detach(view: WebView) { if (activeWebView?.get() === view) activeWebView = null }
        fun start(context: Context) { context.startForegroundService(Intent(context, WebPlaybackService::class.java).setAction(ACTION_START)) }
        fun stop(context: Context) { context.startService(Intent(context, WebPlaybackService::class.java).setAction(ACTION_STOP)) }
    }
}
