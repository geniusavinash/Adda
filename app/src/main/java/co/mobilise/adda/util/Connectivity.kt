package co.mobilise.adda.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import co.mobilise.adda.client.HostClient
import co.mobilise.adda.ui.components.StatusPill
import co.mobilise.adda.ui.theme.AddaSuccess
import kotlinx.coroutines.delay

/** True while the device is in airplane mode; live-updates on toggle. */
@Composable
fun rememberAirplaneMode(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(isAirplaneOn(context)) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                on = isAirplaneOn(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

private fun isAirplaneOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

/**
 * Polls the host while [enabled] (CLIENT mode). Returns false when the host
 * stops responding so the UI can show a "reconnecting" state. Polls faster
 * while down so recovery is snappy.
 */
@Composable
fun rememberHostReachable(baseUrl: String, enabled: Boolean): Boolean {
    var reachable by remember { mutableStateOf(true) }
    LaunchedEffect(baseUrl, enabled) {
        if (!enabled || baseUrl.isBlank()) {
            reachable = true
            return@LaunchedEffect
        }
        while (true) {
            val ok = HostClient.info(baseUrl) != null
            reachable = ok
            delay(if (ok) 5_000 else 2_000)
        }
    }
    return reachable
}

/** Offline status pill that reflects real airplane-mode state. */
@Composable
fun OfflineStatusPill(modifier: Modifier = Modifier) {
    val airplane = rememberAirplaneMode()
    StatusPill(
        text = if (airplane) "✈ Airplane mode · fully offline" else "Offline · runs on-device",
        dotColor = AddaSuccess,
        modifier = modifier,
    )
}
