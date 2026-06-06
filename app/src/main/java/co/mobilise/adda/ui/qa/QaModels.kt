package co.mobilise.adda.ui.qa

import android.graphics.Bitmap

/** One chat entry — either a student's question or Adda's AI answer. */
data class ChatMessage(
    val id: Long,
    val author: String,
    val isAi: Boolean,
    val text: String,
    val streaming: Boolean = false,
    /** For AI messages: the question that prompted this answer (used by Share). */
    val question: String = "",
    /** For a user message: an attached image thumbnail (multimodal question). */
    val image: Bitmap? = null,
    /** For a user message: an attached document's name (PDF/text), shown as a chip. */
    val fileName: String? = null,
    /** When this message was created (used by session export). */
    val createdAt: Long = System.currentTimeMillis(),
)

/** Answer-language toggle shown in the Q&A top bar. */
enum class AnswerLang(val label: String, val sttTag: String) {
    // en-US has the widest on-device speech-pack availability; en-IN was
    // rejected (code 13) on the test device.
    EN("EN", "en-US"),
    HI("हिं", "hi-IN");

    /** Per-question nudge appended to the prompt (base preamble stays Hinglish). */
    val directive: String
        get() = when (this) {
            EN -> "Reply in simple English."
            HI -> "Reply mostly in Hindi using Devanagari script, with simple words."
        }
}
