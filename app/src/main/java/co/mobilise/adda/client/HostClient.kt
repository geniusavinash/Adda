package co.mobilise.adda.client

import co.mobilise.adda.server.AddaServer
import co.mobilise.adda.server.AskRequest
import co.mobilise.adda.server.AskResponse
import co.mobilise.adda.server.InfoResponse
import co.mobilise.adda.server.ResetRequest
import co.mobilise.adda.wifi.JoinCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * CLIENT-side networking to the host's embedded server. Plain HttpURLConnection
 * (no extra deps) on Dispatchers.IO. Cleartext http is allowed via the manifest.
 */
object HostClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Turn a scanned QR / typed code / raw IP into a base URL, or null. */
    fun resolveBaseUrl(input: String): String? {
        val t = input.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t.trimEnd('/')
            t.split(".").size == 4 && t.split(".").all { it.toIntOrNull() in 0..255 } ->
                "http://$t:${AddaServer.PORT}"
            else -> JoinCode.decode(t)?.let { "http://$it:${AddaServer.PORT}" }
        }
    }

    /** Fetch session info; null if the host isn't reachable. Doubles as a ping. */
    suspend fun info(baseUrl: String): InfoResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("$baseUrl/info", connectMs = 4000, readMs = 4000)
            try {
                if (conn.responseCode in 200..299) {
                    json.decodeFromString(InfoResponse.serializer(), conn.inputStream.readText())
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** POST a question (optionally with a base64 image) to the host. */
    suspend fun ask(
        baseUrl: String,
        student: String,
        question: String,
        imageBase64: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val conn = open("$baseUrl/ask", connectMs = 5000, readMs = 180_000).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val payload = json.encodeToString(
                AskRequest.serializer(),
                AskRequest(
                    student = student.ifBlank { "Student" },
                    question = question,
                    image = imageBase64,
                ),
            )
            conn.outputStream.use { it.write(payload.toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.readText().orEmpty()
            val parsed = runCatching {
                json.decodeFromString(AskResponse.serializer(), text)
            }.getOrNull()
            parsed?.answer ?: throw IOException("Host se jawab nahi mila ($code)")
        } finally {
            conn.disconnect()
        }
    }

    /** Streaming-shaped wrapper so the Q&A UI can reuse one code path. */
    fun askFlow(baseUrl: String, student: String, question: String): Flow<String> = flow {
        emit(ask(baseUrl, student, question))
    }.flowOn(Dispatchers.IO)

    /** Send an image (base64 JPEG) + optional question to the host's vision model. */
    fun askImageFlow(
        baseUrl: String,
        student: String,
        question: String,
        imageBase64: String,
    ): Flow<String> = flow {
        emit(ask(baseUrl, student, question, imageBase64))
    }.flowOn(Dispatchers.IO)

    /** Reset this student's conversation thread on the host ("New chat"). */
    suspend fun reset(baseUrl: String, student: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open("$baseUrl/reset", connectMs = 4000, readMs = 4000).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    val payload = json.encodeToString(ResetRequest.serializer(), ResetRequest(student))
                    conn.outputStream.use { it.write(payload.toByteArray()) }
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun open(urlStr: String, connectMs: Int, readMs: Int): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
        }

    private fun java.io.InputStream.readText(): String =
        bufferedReader().use { it.readText() }
}
