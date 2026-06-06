package co.mobilise.adda.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WifiDirectState {
    data object Idle : WifiDirectState
    data object Starting : WifiDirectState

    /** Group is up; clients can join this Wi-Fi then hit the host. */
    data class Ready(val ssid: String, val passphrase: String, val hostIp: String) : WifiDirectState

    data class Error(val message: String) : WifiDirectState

    /** No Wi-Fi P2P (e.g. emulator) — caller falls back to plain Wi-Fi/hotspot. */
    data object Unsupported : WifiDirectState
}

/**
 * Makes this phone a Wi-Fi Direct group owner — an internet-free local Wi-Fi
 * that other phones and the laptop can join. The group owner address (usually
 * 192.168.49.1) becomes the host IP the embedded server is reached on.
 *
 * Singleton so the group survives navigation between Host and Q&A.
 * Permissions (NEARBY_WIFI_DEVICES on 13+, else FINE_LOCATION) are ensured by
 * the caller before [start]; hence the @SuppressLint on the P2P calls.
 */
object WifiDirect {

    private lateinit var appContext: Context
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var registered = false

    private var ssid = ""
    private var passphrase = ""

    private val _state = MutableStateFlow<WifiDirectState>(WifiDirectState.Idle)
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val _hostIp = MutableStateFlow("")
    val hostIp: StateFlow<String> = _hostIp.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        appContext = context.applicationContext

        val mgr = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mgr == null) {
            _state.value = WifiDirectState.Unsupported
            return
        }
        manager = mgr
        if (channel == null) {
            channel = mgr.initialize(appContext, appContext.mainLooper, null)
        }
        val ch = channel ?: run {
            _state.value = WifiDirectState.Error("Wi-Fi Direct init fail")
            return
        }

        registerReceiver()
        if (_state.value !is WifiDirectState.Ready) _state.value = WifiDirectState.Starting

        // Reuse an existing group instead of blindly creating one. On OEM Wi-Fi
        // stacks (iQOO/vivo Funtouch) createGroup semantics can deviate from
        // AOSP; querying first is the robust pattern (harmless fast-path on AOSP).
        mgr.requestGroupInfo(ch) { group ->
            if (group != null) {
                ssid = group.networkName.orEmpty()
                passphrase = group.passphrase.orEmpty()
                publishReady()
                refreshGroup()
            } else {
                createGroupInternal(mgr, ch)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createGroupInternal(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = refreshGroup()
            override fun onFailure(reason: Int) {
                // BUSY = a group already exists (AOSP-verified) — reuse it.
                if (reason == WifiP2pManager.BUSY) refreshGroup()
                else _state.value = WifiDirectState.Error(reasonText(reason))
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            runCatching { mgr.removeGroup(ch, null) }
        }
        unregisterReceiver()
        ssid = ""
        passphrase = ""
        _hostIp.value = ""
        _state.value = WifiDirectState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun refreshGroup() {
        val mgr = manager ?: return
        val ch = channel ?: return

        mgr.requestGroupInfo(ch) { group ->
            if (group != null) {
                ssid = group.networkName.orEmpty()
                passphrase = group.passphrase.orEmpty()
                publishReady()
            }
        }
        mgr.requestConnectionInfo(ch) { info ->
            val ip = info?.groupOwnerAddress?.hostAddress
            if (info != null && info.groupFormed && info.isGroupOwner && !ip.isNullOrBlank()) {
                _hostIp.value = ip
                publishReady()
            }
        }
    }

    private fun publishReady() {
        if (ssid.isBlank()) return
        val ip = _hostIp.value.ifBlank { "192.168.49.1" } // GO default if not reported yet
        _state.value = WifiDirectState.Ready(ssid, passphrase, ip)
    }

    private fun registerReceiver() {
        if (registered) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
                    -> refreshGroup()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            appContext, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = r
        registered = true
    }

    private fun unregisterReceiver() {
        if (registered && receiver != null) {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        receiver = null
        registered = false
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Device Wi-Fi Direct support nahi karta"
        WifiP2pManager.BUSY -> "Wi-Fi Direct busy — thodi der baad"
        WifiP2pManager.ERROR -> "Wi-Fi Direct error — Wi-Fi ON karo"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "No service requests"
        else -> "Wi-Fi Direct fail ($reason)"
    }
}
