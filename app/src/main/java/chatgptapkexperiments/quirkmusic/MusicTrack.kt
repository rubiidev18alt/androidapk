package chatgptapkexperiments.quirkmusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.concurrent.TimeUnit

data class MusicTrack(
    val uriString: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkBytes: ByteArray?
)

object MusicExtras {
    const val URI = "chatgptapkexperiments.quirkmusic.extra.URI"
    const val TITLE = "chatgptapkexperiments.quirkmusic.extra.TITLE"
    const val ARTIST = "chatgptapkexperiments.quirkmusic.extra.ARTIST"
    const val DURATION = "chatgptapkexperiments.quirkmusic.extra.DURATION"
}

object TrackMetadataReader {
    fun read(context: Context, uri: Uri): MusicTrack {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown artist"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = rawTitle?.takeIf { it.isNotBlank() }
                ?: displayName(context, uri)
                ?: "Unknown track"

            MusicTrack(
                uriString = uri.toString(),
                title = title,
                artist = artist,
                durationMs = duration,
                artworkBytes = retriever.embeddedPicture
            )
        } finally {
            retriever.release()
        }
    }

    fun bitmapFrom(track: MusicTrack): Bitmap? {
        val bytes = track.artworkBytes ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}
