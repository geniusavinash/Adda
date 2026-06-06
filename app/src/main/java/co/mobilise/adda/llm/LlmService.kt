package co.mobilise.adda.llm

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import co.mobilise.adda.AddaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Thrown when the model can't be found or the engine can't generate. */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Lifecycle of the on-device engine, observed by the splash/host screens. */
sealed interface LlmState {
    data object Idle : LlmState
    data object Loading : LlmState
    data object Ready : LlmState
    data class Error(val message: String) : LlmState
}

/**
 * Singleton wrapper around MediaPipe's on-device Gemma LLM.
 *
 *  - [init] loads the pre-downloaded `.task` from the app files dir (NO network).
 *  - [ask] streams answer tokens as a [Flow] of incremental chunks.
 *  - One engine is shared; each question runs in its own short-lived session,
 *    serialized by [genMutex] because the native engine does one gen at a time.
 *
 * Runs entirely on-device — safe in airplane mode.
 */
object LlmService {

    // Gemma 3n E4B supports long context; 2048 gives room for a fuller answer
    // while staying light on KV-cache memory (device has 16 GB).
    private const val MAX_TOKENS = 2048
    private const val TOP_K = 40
    private const val TEMPERATURE = 0.8f

    /** Keep at most this many recent (question, answer) rounds per thread. */
    private const val MAX_TURNS = 5

    /** Student-tuned system instruction (short Hinglish; formulas/code fenced). */
    private val SYSTEM_PREAMBLE = """
        You are "Adda", a friendly study buddy for Indian students.
        Follow these rules strictly:
        - Reply in short, simple Hinglish (Hindi + English mixed, in Roman script).
        - Keep answers to 2-5 sentences. Be clear, warm and to the point.
        - Put EVERY formula, equation, or code snippet inside a fenced ``` block.
        - Use simple examples a student would get. No long lectures.
        - If you are not sure, say so honestly. Never invent facts.
    """.trimIndent()

    private var engine: LlmInference? = null
    private var visionReady = false
    private val genMutex = Mutex() // only one generation at a time

    /** True if the loaded model + runtime support image (vision) input. */
    val visionSupported: Boolean get() = visionReady

    /** One short conversation history per asker (thread) for multi-turn memory. */
    private data class Turn(val question: String, val answer: String)
    private val histories = ConcurrentHashMap<String, ArrayDeque<Turn>>()

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    val state: StateFlow<LlmState> = _state.asStateFlow()

    val isReady: Boolean get() = engine != null

    /**
     * Locate the pre-downloaded model. Prefers `gemma.task`, else the first
     * `*.task` in the app's files dir. Returns null if none present.
     */
    fun resolveModelPath(context: Context = AddaApp.instance): String? {
        val dir = context.filesDir
        val preferred = File(dir, "gemma.task")
        if (preferred.exists()) return preferred.absolutePath
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".task") }
            ?.firstOrNull()
            ?.absolutePath
    }

    /**
     * Load the Gemma `.task` from [modelPath] into the MediaPipe engine.
     * Idempotent: a second call is a no-op once ready. Throws [LlmException]
     * on missing file / load failure (and sets [state] to Error).
     */
    suspend fun init(
        modelPath: String,
        context: Context = AddaApp.instance,
    ) = withContext(Dispatchers.IO) {
        if (engine != null) {
            _state.value = LlmState.Ready
            return@withContext
        }
        _state.value = LlmState.Loading

        val file = File(modelPath)
        if (!file.exists()) {
            val msg = "Model not found at $modelPath. Pre-download the Gemma .task " +
                "into the app files dir."
            _state.value = LlmState.Error(msg)
            throw LlmException(msg)
        }

        try {
            engine = try {
                // Gemma 3n is multimodal — enable up to 1 image per query.
                val visionOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxNumImages(1)
                    .build()
                LlmInference.createFromOptions(context, visionOptions).also { visionReady = true }
            } catch (visionFail: Throwable) {
                // Text-only model/runtime: still load, just without image support.
                visionReady = false
                val textOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .build()
                LlmInference.createFromOptions(context, textOptions)
            }
            _state.value = LlmState.Ready
        } catch (t: Throwable) {
            val msg = "Failed to load on-device model: ${t.message ?: t.javaClass.simpleName}"
            _state.value = LlmState.Error(msg)
            throw LlmException(msg, t)
        }
    }

    /**
     * Ask a question. Emits answer tokens as they stream from the model
     * (incremental chunks — collector should append them). Cold flow.
     *
     * If the engine isn't loaded (model not pushed yet), falls back to a
     * pre-cached streamed demo answer so the demo never dead-ends.
     */
    /** Stateless single-shot ask (no memory) — used by the LLM test screen. */
    fun ask(question: String): Flow<String> {
        val inference = engine ?: return DemoCache.stream(question)
        val q = question.trim()
        return generate(inference, promptProvider = { buildConversationPrompt(emptyList(), q) })
    }

    /**
     * Multi-turn ask: answers in the context of [threadId]'s recent turns, then
     * records this round. Each asker gets a SEPARATE thread so unrelated doubts
     * from different students don't bleed into each other. Capped to the last
     * [MAX_TURNS] rounds to stay fast and within the context window.
     */
    fun askInThread(threadId: String, question: String): Flow<String> {
        val inference = engine ?: return DemoCache.stream(question)
        val q = question.trim()
        val history = histories.computeIfAbsent(threadId) { ArrayDeque() }
        return generate(
            inference = inference,
            promptProvider = { buildConversationPrompt(history, q) },
            onComplete = { answer ->
                val clean = cleanAnswer(answer)
                if (clean.isNotBlank()) {
                    history.addLast(Turn(q, clean))
                    while (history.size > MAX_TURNS) history.removeFirst()
                }
            },
        )
    }

    /** Reset a conversation thread ("New chat / Clear"). */
    fun clearThread(threadId: String) {
        histories.remove(threadId)
    }

    /** Strip any chat-template tokens the model may echo into its output. */
    fun cleanAnswer(text: String): String =
        text.replace("<end_of_turn>", "").replace("<start_of_turn>", "").trimEnd()

    /**
     * Multimodal ask: feed [image] + an optional question to Gemma 3n's vision
     * modality and stream the answer. Single-turn (fresh vision session). Falls
     * back gracefully if the model/runtime has no vision. Fully on-device.
     */
    fun askWithImage(question: String, image: Bitmap): Flow<String> {
        val inference = engine ?: return DemoCache.stream(question.ifBlank { "image" })
        if (!visionReady) {
            return flow {
                emit("Is model/runtime pe image samajhna abhi available nahi — text se poochho.")
            }
        }
        val q = question.trim().ifBlank {
            "Is image ko dhyan se dekho aur Hinglish mein samjhao. " +
                "Agar usme text ya formula ho to use bhi padho."
        }
        return generateVision(inference, q, image)
    }

    private fun generateVision(
        inference: LlmInference,
        question: String,
        image: Bitmap,
    ): Flow<String> = callbackFlow {
        genMutex.lock()
        var session: LlmInferenceSession? = null
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                .build()
            session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            // Image sits INSIDE the user turn, right after the question text.
            session.addQueryChunk("<start_of_turn>user\n$SYSTEM_PREAMBLE\n\n$question\n")
            session.addImage(BitmapImageBuilder(image).build())
            session.addQueryChunk("<end_of_turn>\n<start_of_turn>model\n")

            session.generateResponseAsync(
                ProgressListener<String> { partial, done ->
                    if (!partial.isNullOrEmpty()) trySend(partial)
                    if (done) channel.close()
                },
            )
            awaitClose { }
        } catch (t: Throwable) {
            close(LlmException("Image samajhne mein dikkat: ${t.message}", t))
        } finally {
            runCatching { session?.close() }
            genMutex.unlock()
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /**
     * Core streaming generation on a fresh session, serialized by [genMutex].
     * [promptProvider] is evaluated under the lock so it sees a consistent
     * history; [onComplete] gets the full answer (also under the lock) to record.
     */
    private fun generate(
        inference: LlmInference,
        promptProvider: () -> String,
        onComplete: (String) -> Unit = {},
    ): Flow<String> = callbackFlow {
        genMutex.lock()
        var session: LlmInferenceSession? = null
        val sb = StringBuilder()
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .build()
            session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            session.addQueryChunk(promptProvider())

            session.generateResponseAsync(
                ProgressListener<String> { partial, done ->
                    if (!partial.isNullOrEmpty()) {
                        sb.append(partial)
                        trySend(partial)
                    }
                    if (done) channel.close()
                },
            )

            awaitClose { /* session closed in finally */ }
        } catch (t: Throwable) {
            close(LlmException("Generation failed: ${t.message}", t))
        } finally {
            runCatching { session?.close() }
            onComplete(sb.toString())
            genMutex.unlock()
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /** Collect the whole answer into one string (used by the host server, Step 4). */
    suspend fun answer(question: String): String = buildString {
        ask(question).collect { append(it) }
    }

    /** Release native resources (call on real shutdown if needed). */
    fun shutdown() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        _state.value = LlmState.Idle
    }

    /**
     * Builds a Gemma multi-turn prompt: system preamble inside the first user
     * turn, each prior round as user+model turns, then the new user turn left
     * open for the model to complete.
     */
    private fun buildConversationPrompt(history: Collection<Turn>, question: String): String {
        val sb = StringBuilder()
        history.forEachIndexed { i, turn ->
            val user = if (i == 0) "$SYSTEM_PREAMBLE\n\n${turn.question}" else turn.question
            sb.append("<start_of_turn>user\n").append(user).append("<end_of_turn>\n")
            sb.append("<start_of_turn>model\n").append(turn.answer.trim()).append("<end_of_turn>\n")
        }
        val user = if (history.isEmpty()) "$SYSTEM_PREAMBLE\n\n$question" else question
        sb.append("<start_of_turn>user\n").append(user).append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
