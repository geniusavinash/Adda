package co.mobilise.adda.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory state of the live Adda session, shared by the Ktor routes and the
 * host UI. Thread-safe: question mutations are synchronized, presence uses a
 * concurrent map, and everything observable is a [StateFlow] so both Compose
 * and the /feed WebSocket react to changes.
 */
object SessionStore {

    /** A student is "connected" if seen within this window. */
    private const val PRESENCE_TIMEOUT_MS = 12_000L

    private val lock = Any()
    private val items = mutableListOf<FeedItem>()
    private val students = ConcurrentHashMap<String, Long>() // name -> lastSeen
    private var counter = 0L

    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed.asStateFlow()

    private val _connected = MutableStateFlow(0)
    val connected: StateFlow<Int> = _connected.asStateFlow()

    private val _studentNames = MutableStateFlow<List<String>>(emptyList())
    val studentNames: StateFlow<List<String>> = _studentNames.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _ended = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    private val _adminCount = MutableStateFlow(0) // connected laptop consoles
    val adminCount: StateFlow<Int> = _adminCount.asStateFlow()

    // Session metadata served at /info (set by the host; survives reset()).
    @Volatile var subject: String = "General"
    @Volatile var hostName: String = "Host"

    fun setInfo(subject: String, hostName: String) {
        this.subject = subject.ifBlank { "General" }
        this.hostName = hostName.ifBlank { "Host" }
    }

    fun info(): InfoResponse = InfoResponse(
        subject = subject,
        host = hostName,
        connected = _connected.value,
        paused = _paused.value,
        ended = _ended.value,
    )

    // ---- questions ---------------------------------------------------------

    fun addQuestion(asker: String, question: String): FeedItem {
        val item: FeedItem
        synchronized(lock) {
            item = FeedItem(
                id = counter++,
                asker = asker.ifBlank { "Student" },
                question = question.trim(),
                answer = "",
                status = QStatus.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
            items.add(item)
            publish()
        }
        return item
    }

    fun update(id: Long, transform: (FeedItem) -> FeedItem) {
        synchronized(lock) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = transform(items[idx])
                publish()
            }
        }
    }

    private fun publish() {
        _feed.value = items.toList()
    }

    // ---- presence ----------------------------------------------------------

    fun heartbeat(name: String) {
        students[name.ifBlank { "Student" }] = System.currentTimeMillis()
        recomputeConnected()
    }

    fun prune() {
        val cutoff = System.currentTimeMillis() - PRESENCE_TIMEOUT_MS
        students.entries.removeIf { it.value < cutoff }
        recomputeConnected()
    }

    private fun recomputeConnected() {
        _connected.value = students.size
        _studentNames.value = students.keys.sorted()
    }

    // ---- admin consoles ----------------------------------------------------

    fun adminConnected() {
        _adminCount.value = (_adminCount.value + 1)
    }

    fun adminDisconnected() {
        _adminCount.value = (_adminCount.value - 1).coerceAtLeast(0)
    }

    // ---- control -----------------------------------------------------------

    fun setPaused(value: Boolean) {
        _paused.value = value
    }

    fun end() {
        _ended.value = true
        _paused.value = true
    }

    fun snapshot(): FeedSnapshot = FeedSnapshot(
        connected = _connected.value,
        paused = _paused.value,
        ended = _ended.value,
        items = _feed.value,
    )

    /** Wipe everything for a fresh session. */
    fun reset() {
        synchronized(lock) {
            items.clear()
            counter = 0L
            publish()
        }
        students.clear()
        _connected.value = 0
        _studentNames.value = emptyList()
        _paused.value = false
        _ended.value = false
        _adminCount.value = 0
    }
}
