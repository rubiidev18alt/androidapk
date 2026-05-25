package chatgptapkexperiments.quirkmusic

import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var playPause: ImageButton

    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent.getStringExtra(MusicExtras.URI)
        if (uriString == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(MusicExtras.TITLE) ?: "Unknown track"
        val artist = intent.getStringExtra(MusicExtras.ARTIST) ?: "Unknown artist"
        val duration = intent.getLongExtra(MusicExtras.DURATION, 0L)
        buildLayout(uriString, title, artist, duration)
        preparePlayer(Uri.parse(uriString))
        handler.post(progressTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTick)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun buildLayout(uriString: String, title: String, artist: String, duration: Long) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(23, 8, 18))
            setPadding(22.dp, 22.dp, 22.dp, 28.dp)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Back"
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
        }
        val screenTitle = TextView(this).apply {
            text = "Now playing"
            setTextColor(Color.rgb(255, 241, 247))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val spacer = TextView(this).apply { layoutParams = LinearLayout.LayoutParams(48.dp, 1) }
        top.addView(back)
        top.addView(screenTitle)
        top.addView(spacer)

        val art = ImageView(this).apply {
            setBackgroundColor(Color.rgb(122, 41, 79))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(readArtwork(uriString))
            layoutParams = LinearLayout.LayoutParams(280.dp, 280.dp).apply {
                topMargin = 58.dp
                bottomMargin = 34.dp
            }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val artistView = TextView(this).apply {
            text = artist
            setTextColor(Color.rgb(255, 190, 222))
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
                bottomMargin = 26.dp
            }
        }

        seekBar = SeekBar(this).apply {
            max = duration.toInt().coerceAtLeast(1)
            progress = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) mediaPlayer?.seekTo(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        timeText = TextView(this).apply {
            text = "0:00 / ${TrackMetadataReader.formatTime(duration)}"
            setTextColor(Color.rgb(255, 190, 222))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 22.dp
            }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val rewind = playerButton(android.R.drawable.ic_media_rew) { mediaPlayer?.seekTo(((mediaPlayer?.currentPosition ?: 0) - 10_000).coerceAtLeast(0)) }
        playPause = playerButton(android.R.drawable.ic_media_pause) { togglePlayback() }
        val forward = playerButton(android.R.drawable.ic_media_ff) {
            val player = mediaPlayer ?: return@playerButton
            player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
        }
        controls.addView(rewind)
        controls.addView(playPause)
        controls.addView(forward)

        root.addView(top)
        root.addView(art)
        root.addView(titleView)
        root.addView(artistView)
        root.addView(seekBar)
        root.addView(timeText)
        root.addView(controls)
        setContentView(root)
    }

    private fun preparePlayer(uri: Uri) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@PlayerActivity, uri)
            setOnPreparedListener {
                seekBar.max = it.duration.coerceAtLeast(1)
                it.start()
                playPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            setOnCompletionListener {
                playPause.setImageResource(android.R.drawable.ic_media_play)
            }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@PlayerActivity, "Could not play this track.", Toast.LENGTH_SHORT).show()
                true
            }
            prepareAsync()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause() else player.start()
        playPause.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun updateProgress() {
        val player = mediaPlayer ?: return
        val current = player.currentPosition
        val duration = player.duration.coerceAtLeast(1)
        seekBar.max = duration
        seekBar.progress = current.coerceIn(0, duration)
        val left = (duration - current).coerceAtLeast(0)
        timeText.text = "${TrackMetadataReader.formatTime(current.toLong())} / -${TrackMetadataReader.formatTime(left.toLong())}"
        playPause.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun readArtwork(uriString: String) = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, Uri.parse(uriString))
            retriever.embeddedPicture?.let { bytes -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun playerButton(icon: Int, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(74.dp, 74.dp)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
