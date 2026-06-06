package co.mobilise.adda.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Tiny TextToSpeech wrapper to read AI answers aloud. Strips fenced code/formula
 * blocks before speaking. Exposes [speakingId] so the UI can flip the button to
 * "Stop" while that message is being read. Fully offline once a voice is present.
 */
class Speaker(context: Context) {

    /** Id of the message currently being spoken (null = idle). Compose state. */
    var speakingId by mutableStateOf<String?>(null)
        private set

    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, ::onInit)

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            runCatching { tts.language = Locale.US }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { speakingId = utteranceId }
                }
                override fun onDone(utteranceId: String?) {
                    main.post { if (speakingId == utteranceId) speakingId = null }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { if (speakingId == utteranceId) speakingId = null }
                }
            })
        }
    }

    /** Toggle: speak [text] for [id], or stop if [id] is already playing. */
    fun toggle(id: String, text: String) {
        if (!ready) return
        if (speakingId == id) {
            stop()
            return
        }
        val spoken = stripCode(text).take(600)
        if (spoken.isBlank()) return
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        speakingId = id // optimistic; onStart/onDone keep it in sync
    }

    fun stop() {
        runCatching { tts.stop() }
        speakingId = null
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        speakingId = null
    }

    private fun stripCode(text: String): String =
        text.split("```")
            .filterIndexed { i, _ -> i % 2 == 0 } // drop code segments
            .joinToString(" ")
            .replace(Regex("[*_#`>]"), "")
            .trim()
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}
