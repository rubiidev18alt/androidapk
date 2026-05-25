package chatgptapkexperiments.quirkmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object MusicPlaybackManager {
    private const val CHANNEL_ID = "quirkmusic_playback"
    private const val NOTIFICATION_ID = 42

    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null
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
        appContext = context.applicationContext
        createNotificationChannel(context.applicationContext)
    }

    fun setPlaylist(context: Context, tracks: List<MusicTrack>) {
        initialize(context)
        playlist = tracks.toList()
        notifyChanged()
    }

    fun play(context: Context, index: Int) {
        initialize(context)
        if (index !in playlist.indices) return
        currentIndex = index
        isPrepared = false
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context.applicationContext, Uri.parse(playlist[index].uriString))
            setOnPreparedListener {
                isPrepared = true
                it.start()
                showNotification(context.applicationContext)
                notifyChanged()
            }
            setOnCompletionListener { next(context.applicationContext) }
            setOnErrorListener { _, _, _ ->
                isPrepared = false
                notifyChanged()
                true
            }
            prepareAsync()
        }
        showNotification(context.applicationContext)
        notifyChanged()
    }

    fun toggle(context: Context) {
        initialize(context)
        val player = mediaPlayer
        when {
            player == null && playlist.isNotEmpty() -> play(context, 0)
            player?.isPlaying == true -> player.pause()
            player != null && isPrepared -> player.start()
        }
        showNotification(context.applicationContext)
        notifyChanged()
    }

    fun pause(context: Context) {
        mediaPlayer?.pause()
        showNotification(context.applicationContext)
        notifyChanged()
    }

    fun next(context: Context) {
        if (playlist.isEmpty()) return
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % playlist.size
        play(context, nextIndex)
    }

    fun previous(context: Context) {
        if (playlist.isEmpty()) return
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex - 1 + playlist.size) % playlist.size
        play(context, nextIndex)
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
        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val previousIntent = playbackAction(context, MusicNotificationReceiver.ACTION_PREVIOUS, 1)
        val playPauseIntent = playbackAction(context, MusicNotificationReceiver.ACTION_TOGGLE, 2)
        val nextIntent = playbackAction(context, MusicNotificationReceiver.ACTION_NEXT, 3)
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
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
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
