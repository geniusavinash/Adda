package co.mobilise.adda.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Handle returned to the UI: observe [listening], call [toggle] to start/stop. */
interface VoiceInput {
    val listening: Boolean
    fun toggle(langTag: String)
}

/**
 * SpeechRecognizer wrapped for Compose. Prefers the on-device recognizer
 * (API 31+) and sets EXTRA_PREFER_OFFLINE so dictation works in airplane mode
 * when the language pack is installed. Falls back gracefully via [onError].
 *
 * Mic permission is requested on first use.
 */
@Composable
fun rememberVoiceInput(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit = {},
): VoiceInput {
    val context = LocalContext.current

    // Keep callbacks fresh without recreating the recognizer.
    val partial by rememberUpdatedState(onPartial)
    val final by rememberUpdatedState(onFinal)
    val error by rememberUpdatedState(onError)

    var isListening by remember { mutableStateOf(false) }
    var pendingLang by remember { mutableStateOf<String?>(null) }

    val recognizer = remember {
        VoiceRecognizer(
            context = context,
            onPartial = { partial(it) },
            onFinal = { final(it) },
            onListening = { isListening = it },
            onError = { error(it) },
        )
    }

    DisposableEffect(Unit) {
        onDispose { recognizer.destroy() }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val lang = pendingLang
        pendingLang = null
        if (granted && lang != null) recognizer.start(lang)
        else if (!granted) error("Mic permission chahiye voice ke liye")
    }

    return remember {
        object : VoiceInput {
            override val listening: Boolean get() = isListening
            override fun toggle(langTag: String) {
                if (isListening) {
                    recognizer.stop()
                    return
                }
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    recognizer.start(langTag)
                } else {
                    pendingLang = langTag
                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

/** Thin wrapper around SpeechRecognizer; must be driven from the main thread. */
private class VoiceRecognizer(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start(langTag: String) {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.w(TAG, "start lang=$langTag isRecognitionAvailable=$available")
        if (!available) {
            onError("Voice recognition is iss device pe available nahi")
            return
        }
        destroy() // fresh instance each session

        val r = createRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onListening(false)
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                Log.w(TAG, "onError code=$error")
                onListening(false)
                // Language pack missing -> kick off its on-device download (API 33+)
                // so the next attempt works (and works offline afterwards).
                if (Build.VERSION.SDK_INT >= 33 &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                ) {
                    runCatching {
                        recognizer?.triggerModelDownload(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                            },
                        )
                        Log.w(TAG, "triggerModelDownload($langTag) requested")
                    }
                }
                onError(errorText(error))
            }

            override fun onResults(results: Bundle?) {
                onListening(false)
                firstResult(results)?.let(onFinal)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(onPartial)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Only FORCE offline in airplane mode. Otherwise let the recognizer
            // use the network — many devices ship without the on-device language
            // pack and need to fetch/use it, so forcing offline just fails.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isAirplaneOn())
        }
        runCatching { r.startListening(intent) }
            .onFailure { onError("Voice start fail: ${it.message}") }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun isAirplaneOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun createRecognizer(): SpeechRecognizer {
        // Prefer a capable on-device engine (Android System Intelligence / AiAi)
        // over the device's default TTS recognizer, which rejects en-IN (code 13).
        preferredRecognizerComponent()?.let { comp ->
            runCatching { return SpeechRecognizer.createSpeechRecognizer(context, comp) }
                .onSuccess { Log.w(TAG, "using recognizer ${comp.packageName}") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            runCatching { return SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    /** Find the Android System Intelligence (AiAi) recognizer if present. */
    private fun preferredRecognizerComponent(): ComponentName? = runCatching {
        val services = context.packageManager.queryIntentServices(
            Intent("android.speech.RecognitionService"), 0,
        )
        // Prefer com.google.android.as (AiAi) — supports more languages + offline.
        val best = services.firstOrNull { it.serviceInfo.packageName == "com.google.android.as" }
            ?: return@runCatching null
        ComponentName(best.serviceInfo.packageName, best.serviceInfo.name)
    }.getOrNull()

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Samajh nahi aaya, dobara bolo"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch suna nahi"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "Voice pack download ho raha hai — network on rakho, phir mic try karo"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        -> "Iss bhasha ka voice pack nahi — Settings → Voice input → offline pack download karo"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic abhi busy hai, ek pal baad try karo"
        else -> "Voice error ($code) — abhi type karke poochho"
    }
}

private const val TAG = "AddaVoice"
