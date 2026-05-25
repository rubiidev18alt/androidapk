package chatgptapkexperiments.quirkmusic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    private val tracks = mutableListOf<MusicTrack>()
    private var mediaPlayer: MediaPlayer? = null
    private var selectedIndex = -1
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var listHost: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniTime: TextView
    private lateinit var miniProgress: ProgressBar
    private lateinit var playPauseButton: ImageButton

    private val progressTick = object : Runnable {
        override fun run() {
            updateMiniPlayerProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { TrackMetadataReader.read(this, uri) }
                .onSuccess { track ->
                    if (tracks.none { it.uriString == track.uriString }) tracks.add(track)
                }
                .onFailure {
                    Toast.makeText(this, "Could not read that music file.", Toast.LENGTH_SHORT).show()
                }
        }
        saveTracks()
        renderTrackList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        loadTracks()
        renderTrackList()
        handler.post(progressTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTick)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(23, 8, 18))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(26.dp, 26.dp, 26.dp, 18.dp)
        }
        val title = TextView(this).apply {
            text = "QuirkMusic"
            setTextColor(Color.rgb(255, 241, 247))
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val upload = Button(this).apply {
            text = "Add music"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(216, 27, 125))
            setOnClickListener { audioPicker.launch(arrayOf("audio/*")) }
        }
        header.addView(title)
        header.addView(upload)

        emptyText = TextView(this).apply {
            text = "No music yet. Tap Add music and choose local audio files."
            setTextColor(Color.rgb(255, 210, 231))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(30.dp, 80.dp, 30.dp, 30.dp)
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
            setBackgroundColor(Color.rgb(36, 16, 29))
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
        }
        miniProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4.dp)
        }
        val miniRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp, 0, 0)
        }
        miniArt = ImageView(this).apply {
            setBackgroundColor(Color.rgb(122, 41, 79))
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(52.dp, 52.dp)
        }
        val miniTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 0, 8.dp, 0)
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
        miniTextColumn.addView(miniTitle)
        miniTextColumn.addView(miniTime)
        val back = miniButton("⏮") { skip(-1) }
        playPauseButton = miniButton("▶") { togglePlayback() }
        val forward = miniButton("⏭") { skip(1) }
        miniRow.addView(miniArt)
        miniRow.addView(miniTextColumn)
        miniRow.addView(back)
        miniRow.addView(playPauseButton)
        miniRow.addView(forward)
        miniPlayer.addView(miniProgress)
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
                setPadding(14.dp, 12.dp, 14.dp, 12.dp)
                setBackgroundColor(Color.rgb(36, 16, 29))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 12.dp
                }
                setOnClickListener { playTrack(index, openBig = true) }
            }
            val art = ImageView(this).apply {
                setBackgroundColor(Color.rgb(122, 41, 79))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(TrackMetadataReader.bitmapFrom(track))
                layoutParams = LinearLayout.LayoutParams(58.dp, 58.dp)
            }
            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp, 0, 0, 0)
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
                setTextColor(Color.rgb(255, 190, 222))
                textSize = 13f
                maxLines = 1
            }
            textColumn.addView(name)
            textColumn.addView(meta)
            row.addView(art)
            row.addView(textColumn)
            listHost.addView(row)
        }
    }

    private fun playTrack(index: Int, openBig: Boolean) {
        if (index !in tracks.indices) return
        selectedIndex = index
        val track = tracks[index]
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.parse(track.uriString))
            setOnPreparedListener {
                it.start()
                updateMiniPlayer(track)
                updateMiniPlayerProgress()
                if (openBig) openBigPlayer()
            }
            setOnCompletionListener { skip(1) }
            prepareAsync()
        }
        updateMiniPlayer(track)
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: run {
            if (tracks.isNotEmpty()) playTrack(0, openBig = false)
            return
        }
        if (player.isPlaying) player.pause() else player.start()
        playPauseButton.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun skip(delta: Int) {
        if (tracks.isEmpty()) return
        val next = when {
            selectedIndex < 0 -> 0
            else -> (selectedIndex + delta + tracks.size) % tracks.size
        }
        playTrack(next, openBig = false)
    }

    private fun updateMiniPlayer(track: MusicTrack) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = track.title
        miniTime.text = "0:00 / ${TrackMetadataReader.formatTime(track.durationMs)}"
        miniArt.setImageBitmap(TrackMetadataReader.bitmapFrom(track))
        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun updateMiniPlayerProgress() {
        val player = mediaPlayer ?: return
        val duration = player.duration.coerceAtLeast(1)
        val current = player.currentPosition.coerceAtLeast(0)
        miniProgress.progress = ((current / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
        val left = (duration - current).coerceAtLeast(0)
        miniTime.text = "${TrackMetadataReader.formatTime(current.toLong())} / -${TrackMetadataReader.formatTime(left.toLong())}"
        playPauseButton.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun openBigPlayer() {
        if (selectedIndex !in tracks.indices) return
        val track = tracks[selectedIndex]
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(MusicExtras.URI, track.uriString)
            putExtra(MusicExtras.TITLE, track.title)
            putExtra(MusicExtras.ARTIST, track.artist)
            putExtra(MusicExtras.DURATION, track.durationMs)
        })
    }

    private fun loadTracks() {
        val saved = getSharedPreferences("library", MODE_PRIVATE).getStringSet("uris", emptySet()).orEmpty()
        tracks.clear()
        saved.forEach { uriString ->
            runCatching { TrackMetadataReader.read(this, Uri.parse(uriString)) }.onSuccess { tracks.add(it) }
        }
    }

    private fun saveTracks() {
        getSharedPreferences("library", MODE_PRIVATE).edit {
            putStringSet("uris", tracks.map { it.uriString }.toSet())
        }
    }

    private fun miniButton(label: String, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            contentDescription = label
            setImageResource(
                when (label) {
                    "⏮" -> android.R.drawable.ic_media_previous
                    "⏭" -> android.R.drawable.ic_media_next
                    else -> android.R.drawable.ic_media_play
                }
            )
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(46.dp, 46.dp)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
