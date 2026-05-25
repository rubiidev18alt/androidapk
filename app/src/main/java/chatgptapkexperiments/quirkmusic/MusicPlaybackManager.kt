package chatgptapkexperiments.quirkmusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle

object MusicPlaybackManager {
    private const val CHANNEL_ID = "quirkmusic_playback"
    private const val NOTIFICATION_ID = 42

    private var mediaPlayer: MediaPlayer? = null
    private val listeners = mutableSetOf<() -> Unit>()

    var playlist: List<MusicTrack> = emptyList()
        private set
    var currentIndex: Int = -1
        private set
    var isPrepared: Boolean = false
        private set

    val currentTrack: MusicTrack?
        get() = playlist.getOrNull(currentIndex)

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    val duration: Int
        get() = runCatching { mediaPlayer?.duration ?: currentTrack?.durationMs?.toInt() ?: 0 }.getOrDefault(0)

    val position: Int
        get() = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)

    fun initialize(context: Context) {
        createNotificationChannel(context.applicationContext)
    }

    fun setPlaylist(context: Context, tracks: List<MusicTrack>) {
        initialize(context)
        playlist = tracks.toList()
        notifyChanged()
    }

    fun play(context: Context, index: Int) {
        val appContext = context.applicationContext
        initialize(appContext)
        if (index !in playlist.indices) return
        currentIndex = index
        isPrepared = false
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(appContext, Uri.parse(playlist[index].uriString))
            setOnPreparedListener {
                isPrepared = true
                it.start()
                showNotification(appContext)
                notifyChanged()
            }
            setOnCompletionListener { next(appContext) }
            setOnErrorListener { _, _, _ ->
                isPrepared = false
                notifyChanged()
                true
            }
            prepareAsync()
        }
        showNotification(appContext)
        notifyChanged()
    }

    fun toggle(context: Context) {
        val appContext = context.applicationContext
        initialize(appContext)
        val player = mediaPlayer
        when {
            player == null && playlist.isNotEmpty() -> play(appContext, 0)
            player?.isPlaying == true -> player.pause()
            player != null && isPrepared -> player.start()
        }
        showNotification(appContext)
        notifyChanged()
    }

    fun next(context: Context) {
        val appContext = context.applicationContext
        if (playlist.isEmpty()) return
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % playlist.size
        play(appContext, nextIndex)
    }

    fun previous(context: Context) {
        val appContext = context.applicationContext
        if (playlist.isEmpty()) return
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex - 1 + playlist.size) % playlist.size
        play(appContext, nextIndex)
    }

    fun seekTo(positionMs: Int) {
        if (isPrepared) mediaPlayer?.seekTo(positionMs)
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
        listener()
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun refreshNotification(context: Context) {
        showNotification(context.applicationContext)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.invoke() }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "QuirkMusic playback controls"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context) {
        val track = currentTrack ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val artwork = track.artworkBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music_note)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText("QuirkMusic")
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setColor(0xFFD81B7D.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Previous", playbackAction(context, MusicNotificationReceiver.ACTION_PREVIOUS, 1))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playbackAction(context, MusicNotificationReceiver.ACTION_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", playbackAction(context, MusicNotificationReceiver.ACTION_NEXT, 3))
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun playbackAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicNotificationReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
