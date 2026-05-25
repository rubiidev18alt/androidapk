package chatgptapkexperiments.quirkmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    private val tracks = mutableListOf<MusicTrack>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var listHost: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniTime: TextView
    private lateinit var miniProgress: ProgressBar
    private lateinit var playPauseButton: ImageButton

    private val playbackListener = { updateMiniPlayer() }
    private val progressTick = object : Runnable {
        override fun run() {
            updateMiniPlayer()
            handler.postDelayed(this, 500)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) MusicPlaybackManager.refreshNotification(this)
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { TrackMetadataReader.read(this, uri) }
                .onSuccess { track -> if (tracks.none { it.uriString == track.uriString }) tracks.add(track) }
                .onFailure { Toast.makeText(this, "Could not read that music file.", Toast.LENGTH_SHORT).show() }
        }
        saveTracks()
        MusicPlaybackManager.setPlaylist(this, tracks)
        renderTrackList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        buildLayout()
        loadTracks()
        MusicPlaybackManager.setPlaylist(this, tracks)
        MusicPlaybackManager.addListener(playbackListener)
        renderTrackList()
        handler.post(progressTick)
    }

    override fun onDestroy() {
        MusicPlaybackManager.removeListener(playbackListener)
        handler.removeCallbacks(progressTick)
        super.onDestroy()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = verticalGradient()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 28.dp, 24.dp, 18.dp)
        }
        val eyebrow = TextView(this).apply {
            text = "LOCAL LIBRARY"
            setTextColor(Color.rgb(255, 160, 210))
            textSize = 12f
            letterSpacing = 0.18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "QuirkMusic"
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val upload = Button(this).apply {
            text = "+ Add"
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(216, 27, 125), 22.dp)
            setOnClickListener { audioPicker.launch(arrayOf("audio/*")) }
        }
        row.addView(title)
        row.addView(upload, LinearLayout.LayoutParams(96.dp, 48.dp))
        header.addView(eyebrow)
        header.addView(row)

        emptyText = TextView(this).apply {
            text = "No tracks yet. Add local audio files and QuirkMusic will read names, artists, durations, and album art."
            setTextColor(Color.rgb(255, 210, 231))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32.dp, 92.dp, 32.dp, 32.dp)
        }

        listHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 0, 18.dp, 18.dp)
        }
        val scroll = ScrollView(this).apply {
            addView(listHost)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        miniPlayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(Color.rgb(42, 15, 34), 26.dp)
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(14.dp, 0, 14.dp, 14.dp)
            }
        }
        miniProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
        }
        val miniRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp, 0, 0)
        }
        miniArt = ImageView(this).apply {
            background = rounded(Color.rgb(122, 41, 79), 14.dp)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 0, 6.dp, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        miniTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        miniTime = TextView(this).apply {
            setTextColor(Color.rgb(255, 190, 222))
            textSize = 12f
        }
        textColumn.addView(miniTitle)
        textColumn.addView(miniTime)
        miniRow.addView(miniArt, LinearLayout.LayoutParams(54.dp, 54.dp))
        miniRow.addView(textColumn)
        miniRow.addView(miniButton(android.R.drawable.ic_media_previous) { MusicPlaybackManager.previous(this) })
        playPauseButton = miniButton(android.R.drawable.ic_media_play) { MusicPlaybackManager.toggle(this) }
        miniRow.addView(playPauseButton)
        miniRow.addView(miniButton(android.R.drawable.ic_media_next) { MusicPlaybackManager.next(this) })
        miniPlayer.addView(miniProgress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5.dp))
        miniPlayer.addView(miniRow)
        miniPlayer.setOnClickListener { openBigPlayer() }

        root.addView(header)
        root.addView(emptyText)
        root.addView(scroll)
        root.addView(miniPlayer)
        setContentView(root)
    }

    private fun renderTrackList() {
        listHost.removeAllViews()
        emptyText.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        tracks.forEachIndexed { index, track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
                background = rounded(Color.rgb(38, 14, 31), 22.dp)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 12.dp
                }
                setOnClickListener {
                    MusicPlaybackManager.setPlaylist(this@MainActivity, tracks)
                    MusicPlaybackManager.play(this@MainActivity, index)
                    openBigPlayer()
                }
            }
            val art = ImageView(this).apply {
                background = rounded(Color.rgb(122, 41, 79), 16.dp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(TrackMetadataReader.bitmapFrom(track))
            }
            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp, 0, 8.dp, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val name = TextView(this).apply {
                text = track.title
                setTextColor(Color.WHITE)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            val meta = TextView(this).apply {
                text = "${track.artist} • ${TrackMetadataReader.formatTime(track.durationMs)}"
                setTextColor(Color.rgb(255, 181, 218))
                textSize = 13f
                maxLines = 1
            }
            textColumn.addView(name)
            textColumn.addView(meta)
            row.addView(art, LinearLayout.LayoutParams(62.dp, 62.dp))
            row.addView(textColumn)
            row.addView(miniButton(android.R.drawable.ic_media_play) { MusicPlaybackManager.play(this, index) })
            listHost.addView(row)
        }
    }

    private fun updateMiniPlayer() {
        val track = MusicPlaybackManager.currentTrack ?: run {
            miniPlayer.visibility = View.GONE
            return
        }
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = track.title
        miniArt.setImageBitmap(TrackMetadataReader.bitmapFrom(track))
        val duration = MusicPlaybackManager.duration.coerceAtLeast(1)
        val current = MusicPlaybackManager.position.coerceAtLeast(0)
        miniProgress.progress = ((current / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
        val left = (duration - current).coerceAtLeast(0)
        miniTime.text = "${TrackMetadataReader.formatTime(current.toLong())} / -${TrackMetadataReader.formatTime(left.toLong())}"
        playPauseButton.setImageResource(if (MusicPlaybackManager.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun openBigPlayer() {
        if (MusicPlaybackManager.currentTrack == null) return
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun loadTracks() {
        val saved = getSharedPreferences("library", MODE_PRIVATE).getStringSet("uris", emptySet()).orEmpty()
        tracks.clear()
        saved.forEach { uriString ->
            runCatching { TrackMetadataReader.read(this, Uri.parse(uriString)) }.onSuccess { tracks.add(it) }
        }
    }

    private fun saveTracks() {
        getSharedPreferences("library", MODE_PRIVATE).edit { putStringSet("uris", tracks.map { it.uriString }.toSet()) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun miniButton(icon: Int, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            background = rounded(Color.rgb(70, 22, 53), 18.dp)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp).apply { leftMargin = 6.dp }
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun verticalGradient() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(32, 8, 25), Color.rgb(23, 8, 18), Color.rgb(12, 3, 10))
    )

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
