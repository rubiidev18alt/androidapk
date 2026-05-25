package chatgptapkexperiments.quirkmusic

import android.graphics.Color
import android.graphics.GradientDrawable
import android.graphics.Typeface
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
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var art: ImageView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var playPause: ImageButton

    private val playbackListener = { updateScreen() }
    private val progressTick = object : Runnable {
        override fun run() {
            updateScreen()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (MusicPlaybackManager.currentTrack == null) {
            finish()
            return
        }
        buildLayout()
        MusicPlaybackManager.addListener(playbackListener)
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
            gravity = Gravity.CENTER_HORIZONTAL
            background = verticalGradient()
            setPadding(22.dp, 22.dp, 22.dp, 28.dp)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            background = rounded(Color.rgb(54, 17, 42), 22.dp)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        val heading = TextView(this).apply {
            text = "Now playing"
            setTextColor(Color.rgb(255, 224, 239))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        top.addView(back, LinearLayout.LayoutParams(48.dp, 48.dp))
        top.addView(heading)
        top.addView(TextView(this), LinearLayout.LayoutParams(48.dp, 1))

        art = ImageView(this).apply {
            background = rounded(Color.rgb(122, 41, 79), 34.dp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            elevation = 12f
            layoutParams = LinearLayout.LayoutParams(300.dp, 300.dp).apply {
                topMargin = 52.dp
                bottomMargin = 34.dp
            }
        }

        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
        }
        artistView = TextView(this).apply {
            setTextColor(Color.rgb(255, 185, 220))
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
                bottomMargin = 30.dp
            }
        }

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) MusicPlaybackManager.seekTo(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        timeText = TextView(this).apply {
            setTextColor(Color.rgb(255, 190, 222))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24.dp
            }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(playerButton(android.R.drawable.ic_media_previous) { MusicPlaybackManager.previous(this) })
        controls.addView(playerButton(android.R.drawable.ic_media_rew) { MusicPlaybackManager.seekTo((MusicPlaybackManager.position - 10_000).coerceAtLeast(0)) })
        playPause = playerButton(android.R.drawable.ic_media_pause) { MusicPlaybackManager.toggle(this) }
        controls.addView(playPause, LinearLayout.LayoutParams(82.dp, 82.dp).apply { setMargins(10.dp, 0, 10.dp, 0) })
        controls.addView(playerButton(android.R.drawable.ic_media_ff) { MusicPlaybackManager.seekTo((MusicPlaybackManager.position + 10_000).coerceAtMost(MusicPlaybackManager.duration)) })
        controls.addView(playerButton(android.R.drawable.ic_media_next) { MusicPlaybackManager.next(this) })

        root.addView(top)
        root.addView(art)
        root.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(artistView)
        root.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(timeText)
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun updateScreen() {
        val track = MusicPlaybackManager.currentTrack ?: return
        titleView.text = track.title
        artistView.text = track.artist
        art.setImageBitmap(TrackMetadataReader.bitmapFrom(track))
        val duration = MusicPlaybackManager.duration.coerceAtLeast(1)
        val current = MusicPlaybackManager.position.coerceIn(0, duration)
        seekBar.max = duration
        seekBar.progress = current
        val left = (duration - current).coerceAtLeast(0)
        timeText.text = "${TrackMetadataReader.formatTime(current.toLong())} / -${TrackMetadataReader.formatTime(left.toLong())}"
        playPause.setImageResource(if (MusicPlaybackManager.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun playerButton(icon: Int, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            background = rounded(Color.rgb(70, 22, 53), 26.dp)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(58.dp, 58.dp).apply { setMargins(4.dp, 0, 4.dp, 0) }
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun verticalGradient() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(42, 9, 33), Color.rgb(23, 8, 18), Color.rgb(10, 2, 8))
    )

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
