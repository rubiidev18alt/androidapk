package chatgptapkexperiments.quirkmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MusicNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MusicPlaybackManager.initialize(context)
        when (intent.action) {
            ACTION_PREVIOUS -> MusicPlaybackManager.previous(context)
            ACTION_TOGGLE -> MusicPlaybackManager.toggle(context)
            ACTION_NEXT -> MusicPlaybackManager.next(context)
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "chatgptapkexperiments.quirkmusic.action.PREVIOUS"
        const val ACTION_TOGGLE = "chatgptapkexperiments.quirkmusic.action.TOGGLE"
        const val ACTION_NEXT = "chatgptapkexperiments.quirkmusic.action.NEXT"
    }
}
