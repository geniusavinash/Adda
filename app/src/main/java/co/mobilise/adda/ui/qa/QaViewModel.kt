package co.mobilise.adda.ui.qa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.mobilise.adda.llm.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Holds the Q&A chat log and drives the on-device LLM.
 *
 * Step 3 = phone-only path: every question runs through the local [LlmService].
 * Step 6 wires CLIENT mode to POST questions to the host instead — only [send]
 * changes; the UI stays identical.
 */
class QaViewModel : ViewModel() {

    val messages: SnapshotStateList<ChatMessage> = emptyList<ChatMessage>().toMutableStateList()

    var lang by mutableStateOf(AnswerLang.EN)
        private set

    /** True while the model is streaming an answer (locks the send button). */
    var isResponding by mutableStateOf(false)
        private set

    private var nextId = 0L

    fun toggleLang() {
        lang = if (lang == AnswerLang.EN) AnswerLang.HI else AnswerLang.EN
    }

    /** Clear the on-screen chat (caller also resets the LLM/host thread). */
    fun clear() {
        messages.clear()
        nextId = 0L
    }

    /**
     * Add the question + a streaming AI placeholder, then fill it from [responder].
     * HOST passes the local LLM flow; CLIENT passes the host POST /ask flow —
     * the UI is identical either way.
     */
    fun send(question: String, asker: String, responder: (String) -> Flow<String>) {
        val q = question.trim()
        if (q.isEmpty() || isResponding) return

        messages.add(ChatMessage(id = nextId++, author = asker, isAi = false, text = q))
        val aiId = nextId++
        messages.add(
            ChatMessage(id = aiId, author = "Adda", isAi = true, text = "", streaming = true, question = q),
        )
        isResponding = true

        viewModelScope.launch {
            val prompt = "${lang.directive}\n\n$q"
            try {
                responder(prompt).collect { chunk ->
                    updateMessage(aiId) { it.copy(text = it.text + chunk) }
                }
                updateMessage(aiId) { it.copy(streaming = false, text = LlmService.cleanAnswer(it.text)) }
            } catch (t: Throwable) {
                updateMessage(aiId) {
                    val prefix = if (it.text.isBlank()) "" else it.text + "\n\n"
                    it.copy(text = prefix + "⚠️ ${t.message ?: "AI error"}", streaming = false)
                }
            } finally {
                isResponding = false
            }
        }
    }

    /**
     * Send an image (multimodal) question. Shows the thumbnail on the user
     * bubble; [responder] feeds (prompt, image) to the on-device vision model
     * (HOST) or the host's /ask (CLIENT). Image questions are single-turn.
     */
    fun sendImage(
        question: String,
        asker: String,
        image: Bitmap,
        responder: (String, Bitmap) -> Flow<String>,
        fileName: String? = null,
    ) {
        if (isResponding) return
        val q = question.trim()

        messages.add(
            ChatMessage(id = nextId++, author = asker, isAi = false, text = q, image = image, fileName = fileName),
        )
        val aiId = nextId++
        messages.add(
            ChatMessage(
                id = aiId, author = "Adda", isAi = true, text = "", streaming = true,
                question = q.ifBlank { "image" },
            ),
        )
        isResponding = true

        viewModelScope.launch {
            val prompt = "${lang.directive}\n\n$q".trim()
            try {
                responder(prompt, image).collect { chunk ->
                    updateMessage(aiId) { it.copy(text = it.text + chunk) }
                }
                updateMessage(aiId) { it.copy(streaming = false, text = LlmService.cleanAnswer(it.text)) }
            } catch (t: Throwable) {
                updateMessage(aiId) {
                    val prefix = if (it.text.isBlank()) "" else it.text + "\n\n"
                    it.copy(text = prefix + "⚠️ ${t.message ?: "AI error"}", streaming = false)
                }
            } finally {
                isResponding = false
            }
        }
    }

    /**
     * Send a TEXT document (e.g. .txt/.md/.csv): feed its content as context for
     * a normal text answer. The user bubble shows a file chip + the question.
     */
    fun sendDoc(
        question: String,
        fileName: String,
        docText: String,
        asker: String,
        responder: (String) -> Flow<String>,
    ) {
        if (isResponding) return
        val q = question.trim()

        messages.add(ChatMessage(id = nextId++, author = asker, isAi = false, text = q, fileName = fileName))
        val aiId = nextId++
        messages.add(
            ChatMessage(
                id = aiId, author = "Adda", isAi = true, text = "", streaming = true,
                question = q.ifBlank { "document" },
            ),
        )
        isResponding = true

        viewModelScope.launch {
            val ask = q.ifBlank { "Is document ko Hinglish mein short summarise karo." }
            val modelPrompt = "${lang.directive}\n\nNeeche '$fileName' file ka content hai. " +
                "Ise padho aur sawaal ka jawab do.\n\n--- START ---\n$docText\n--- END ---\n\nSawaal: $ask"
            try {
                responder(modelPrompt).collect { chunk ->
                    updateMessage(aiId) { it.copy(text = it.text + chunk) }
                }
                updateMessage(aiId) { it.copy(streaming = false, text = LlmService.cleanAnswer(it.text)) }
            } catch (t: Throwable) {
                updateMessage(aiId) {
                    val prefix = if (it.text.isBlank()) "" else it.text + "\n\n"
                    it.copy(text = prefix + "⚠️ ${t.message ?: "AI error"}", streaming = false)
                }
            } finally {
                isResponding = false
            }
        }
    }

    private inline fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) messages[idx] = transform(messages[idx])
    }
}
