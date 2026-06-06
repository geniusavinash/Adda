package co.mobilise.adda.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Which side of the Adda this device is on. */
enum class AppMode { NONE, HOST, CLIENT }

/**
 * Activity-scoped app state shared by every screen in the nav graph.
 * Holds the chosen mode + session subject now; host IP / join code / display
 * name are wired in later steps (server, Wi-Fi Direct, join flow).
 */
class AppViewModel : ViewModel() {

    var mode by mutableStateOf(AppMode.NONE)
        private set

    /** Topic of this Adda session, e.g. "Physics — Class 12". */
    var subject by mutableStateOf("")

    /** This user's display name (asker tag on questions). */
    var displayName by mutableStateOf("")

    // Filled in by later steps -------------------------------------------------
    var hostIp by mutableStateOf("")          // host LAN IP, e.g. 192.168.49.1
    var serverPort by mutableStateOf(8080)
    var joinCode by mutableStateOf("")        // short human-typable code
    var hostName by mutableStateOf("")        // whose Adda the client joined

    val baseUrl: String
        get() = "http://$hostIp:$serverPort"

    fun startHost(subject: String, hostName: String = "Host") {
        mode = AppMode.HOST
        this.subject = subject.ifBlank { "General" }
        this.hostName = hostName
    }

    fun startClient(displayName: String = "") {
        mode = AppMode.CLIENT
        if (displayName.isNotBlank()) this.displayName = displayName
    }

    fun reset() {
        mode = AppMode.NONE
        subject = ""
        hostIp = ""
        joinCode = ""
        hostName = ""
    }
}
