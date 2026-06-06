package co.mobilise.adda.server

import co.mobilise.adda.llm.LlmService
import co.mobilise.adda.util.ImageUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val wsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/**
 * The embedded AI server. Started when a device becomes HOST: it serves the
 * student web client, answers questions on the on-device LLM, keeps the live
 * feed, and broadcasts it to admin consoles over WebSocket — all on the LAN,
 * no public internet.
 */
object AddaServer {

    const val PORT = 8080

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: ApplicationEngine? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _hostIp = MutableStateFlow("")
    val hostIp: StateFlow<String> = _hostIp.asStateFlow()

    val url: String get() = "http://${_hostIp.value}:$PORT"

    /**
     * Override the advertised host IP (e.g. the Wi-Fi Direct group-owner
     * address). The server binds 0.0.0.0 so no rebind is needed — this only
     * changes what we show in the QR / join code.
     */
    fun overrideHostIp(ip: String) {
        if (ip.isNotBlank()) _hostIp.value = ip
    }

    /** Publish session subject + host name for joining clients (GET /info). */
    fun setSessionInfo(subject: String, hostName: String) {
        SessionStore.setInfo(subject, hostName)
    }

    fun start() {
        if (_running.value) return
        scope.launch {
            try {
                SessionStore.reset()
                _hostIp.value = NetworkUtils.deviceIpAddress()
                val srv = embeddedServer(CIO, port = PORT, host = "0.0.0.0") { addaModule() }
                srv.start(wait = false)
                engine = srv
                _running.value = true

                // Decay student presence so the connected-count stays honest.
                while (isActive && _running.value) {
                    delay(4_000)
                    SessionStore.prune()
                }
            } catch (_: Throwable) {
                _running.value = false
            }
        }
    }

    fun stop() {
        _running.value = false
        val srv = engine
        engine = null
        scope.launch { srv?.stop(500, 1_500) }
    }
}

/** All routes for the host server. Top-level so it needs no dispatch receiver. */
private fun Application.addaModule() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }
    install(WebSockets)

    routing {
        // Student web client
        get("/") {
            call.respondText(StudentClient.PAGE, ContentType.Text.Html)
        }

        // Session info for joining clients (subject, host, counts, state)
        get("/info") {
            call.respond(SessionStore.info())
        }

        // Laptop big-screen admin console (iQOO Office Kit)
        get("/admin") {
            call.respondText(AdminConsole.PAGE, ContentType.Text.Html)
        }

        // Presence heartbeats from the web client
        post("/join") {
            val req = call.receive<PresenceRequest>()
            SessionStore.heartbeat(req.name)
            call.respond(SimpleResponse(true))
        }
        post("/ping") {
            val req = call.receive<PresenceRequest>()
            SessionStore.heartbeat(req.name)
            call.respond(SimpleResponse(true))
        }

        // Ask a doubt -> on-device LLM -> answer (also streamed into the feed)
        post("/ask") {
            val req = call.receive<AskRequest>()
            when {
                SessionStore.ended.value -> {
                    call.respond(HttpStatusCode.Gone, AskResponse(-1, "Session khatam ho gaya", "ERROR"))
                    return@post
                }
                SessionStore.paused.value -> {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        AskResponse(-1, "Host ne queue pause ki hai", "ERROR"),
                    )
                    return@post
                }
            }

            SessionStore.heartbeat(req.student)
            val item = SessionStore.addQuestion(req.student, req.question)
            SessionStore.update(item.id) { it.copy(status = QStatus.ANSWERING) }

            val sb = StringBuilder()
            try {
                // Image question -> Gemma 3n vision (single-turn); else per-student
                // multi-turn thread (isolated per asker).
                val bitmap = req.image?.let { ImageUtils.fromBase64(it) }
                val stream = if (bitmap != null) {
                    LlmService.askWithImage(req.question, bitmap)
                } else {
                    LlmService.askInThread(req.student, req.question)
                }
                stream.collect { chunk ->
                    sb.append(chunk)
                    SessionStore.update(item.id) { it.copy(answer = LlmService.cleanAnswer(sb.toString())) }
                }
                val finalAnswer = LlmService.cleanAnswer(sb.toString())
                SessionStore.update(item.id) { it.copy(answer = finalAnswer, status = QStatus.ANSWERED) }
                call.respond(AskResponse(item.id, finalAnswer, "ANSWERED"))
            } catch (t: Throwable) {
                val msg = "AI error: ${t.message ?: "unknown"}"
                SessionStore.update(item.id) { it.copy(answer = msg, status = QStatus.ERROR) }
                call.respond(HttpStatusCode.InternalServerError, AskResponse(item.id, msg, "ERROR"))
            }
        }

        // New chat / clear a student's conversation thread
        post("/reset") {
            val req = call.receive<ResetRequest>()
            LlmService.clearThread(req.student)
            call.respond(SimpleResponse(true))
        }

        // Host/admin controls
        post("/control") {
            val req = call.receive<ControlRequest>()
            when (req.action.lowercase()) {
                "pause" -> SessionStore.setPaused(true)
                "resume" -> SessionStore.setPaused(false)
                "end" -> SessionStore.end()
                else -> {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(false, "unknown action"))
                    return@post
                }
            }
            call.respond(SimpleResponse(true, req.action))
        }

        // Live feed for admin consoles (Step 7)
        webSocket("/feed") {
            SessionStore.adminConnected()
            try {
                combine(
                    SessionStore.feed,
                    SessionStore.studentNames,
                    SessionStore.paused,
                    SessionStore.ended,
                ) { items, students, paused, ended ->
                    FeedSnapshot(
                        connected = students.size,
                        paused = paused,
                        ended = ended,
                        items = items,
                        students = students,
                    )
                }.collect { snap ->
                    send(Frame.Text(wsJson.encodeToString(FeedSnapshot.serializer(), snap)))
                }
            } finally {
                SessionStore.adminDisconnected()
            }
        }
    }
}
