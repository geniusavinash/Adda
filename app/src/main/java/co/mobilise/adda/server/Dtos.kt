package co.mobilise.adda.server

import kotlinx.serialization.Serializable

/** Lifecycle of a question in the host feed. */
@Serializable
enum class QStatus { QUEUED, ANSWERING, ANSWERED, ERROR }

/** One entry in the shared question feed (broadcast to admin consoles). */
@Serializable
data class FeedItem(
    val id: Long,
    val asker: String,
    val question: String,
    val answer: String,
    val status: QStatus,
    val createdAt: Long,
)

/** Snapshot pushed over the /feed WebSocket on every change. */
@Serializable
data class FeedSnapshot(
    val connected: Int,
    val paused: Boolean,
    val ended: Boolean,
    val items: List<FeedItem>,
    val students: List<String> = emptyList(),
)

// ---- HTTP request/response bodies -----------------------------------------

@Serializable
data class AskRequest(
    val student: String = "Student",
    val question: String,
    /** Optional base64 JPEG for a multimodal (image) question. */
    val image: String? = null,
)

@Serializable
data class AskResponse(val id: Long, val answer: String, val status: String)

@Serializable
data class ControlRequest(val action: String) // pause | resume | end

@Serializable
data class PresenceRequest(val name: String)

@Serializable
data class ResetRequest(val student: String = "Student")

@Serializable
data class SimpleResponse(val ok: Boolean, val message: String = "")

/** Session metadata a joining client fetches from GET /info. */
@Serializable
data class InfoResponse(
    val subject: String,
    val host: String,
    val connected: Int,
    val paused: Boolean,
    val ended: Boolean,
)
