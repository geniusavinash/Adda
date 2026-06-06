package co.mobilise.adda.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.mobilise.adda.server.AddaServer
import co.mobilise.adda.server.SessionStore
import co.mobilise.adda.state.AppViewModel
import co.mobilise.adda.ui.components.AddaPrimaryButton
import co.mobilise.adda.ui.components.QrCode
import co.mobilise.adda.ui.components.StatusPill
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSecondary
import co.mobilise.adda.ui.theme.AddaSuccess
import co.mobilise.adda.util.OfflineStatusPill
import co.mobilise.adda.wifi.JoinCode
import co.mobilise.adda.wifi.WifiDirect
import co.mobilise.adda.wifi.WifiDirectState

@Composable
fun HostScreen(
    app: AppViewModel,
    onOpenQa: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val running by AddaServer.running.collectAsState()
    val wifiState by WifiDirect.state.collectAsState()
    val wifiIp by WifiDirect.hostIp.collectAsState()
    val serverIp by AddaServer.hostIp.collectAsState()
    val connected by SessionStore.connected.collectAsState()

    val hostIp = wifiIp.ifBlank { serverIp }
    val url = if (hostIp.isNotBlank()) "http://$hostIp:${AddaServer.PORT}" else ""
    val code = if (hostIp.isNotBlank()) JoinCode.encode(hostIp) else ""

    val wifiPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            WifiDirect.start(context)
        } else {
            Toast.makeText(
                context,
                "Wi-Fi Direct permission nahi mili — same Wi-Fi/hotspot use karo",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        AddaServer.start()
        AddaServer.setSessionInfo(app.subject, app.hostName)
        val granted = ContextCompat.checkSelfPermission(context, wifiPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) WifiDirect.start(context) else permLauncher.launch(wifiPermission)
    }

    // Push the resolved host IP into server + app state.
    LaunchedEffect(hostIp) {
        if (hostIp.isNotBlank()) {
            AddaServer.overrideHostIp(hostIp)
            app.hostIp = hostIp
            app.serverPort = AddaServer.PORT
            app.joinCode = code
        }
    }

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Hosting an Adda",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text("Subject · ${app.subject}", style = MaterialTheme.typography.bodyLarge, color = AddaMuted)
        Spacer(Modifier.height(14.dp))
        StatusPill(
            text = if (running) "AI ready · Gemma 2B · on-device" else "Starting server…",
            dotColor = if (running) AddaSuccess else AddaPrimary,
        )

        Spacer(Modifier.height(22.dp))

        // ---- Step 1: join the host's Wi-Fi (Wi-Fi Direct group) -------------
        when (val s = wifiState) {
            is WifiDirectState.Ready -> {
                WifiJoinCard(
                    ssid = s.ssid,
                    passphrase = s.passphrase,
                    onCopySsid = { copy("Wi-Fi name", s.ssid) },
                    onCopyPass = { copy("Password", s.passphrase) },
                )
                Spacer(Modifier.height(14.dp))
            }

            is WifiDirectState.Error -> {
                InfoNote("Wi-Fi Direct: ${s.message}. Sab ek hi Wi-Fi/hotspot pe rahein.")
                Spacer(Modifier.height(14.dp))
            }

            is WifiDirectState.Unsupported -> {
                InfoNote("Wi-Fi Direct unsupported — sab ek hi Wi-Fi/hotspot pe rahein, phir QR scan karein.")
                Spacer(Modifier.height(14.dp))
            }

            else -> {
                InfoNote("Setting up local Wi-Fi…")
                Spacer(Modifier.height(14.dp))
            }
        }

        // ---- Step 2: scan QR / open URL ------------------------------------
        QrCode(content = url, sizeDp = 224)
        Spacer(Modifier.height(10.dp))
        Text(
            if (url.isNotBlank()) url else "starting…",
            style = MaterialTheme.typography.titleMedium,
            color = AddaSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(enabled = url.isNotBlank()) { copy("URL", url) },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Scan to join · ya browser mein URL kholo",
            style = MaterialTheme.typography.bodySmall,
            color = AddaMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // ---- Join code -----------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("JOIN CODE", style = MaterialTheme.typography.labelMedium, color = AddaMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        code.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy code",
                    tint = AddaPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(enabled = code.isNotBlank()) { copy("Join code", code) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Connected count ----------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = AddaPrimary)
                Column {
                    Text(
                        "$connected student${if (connected == 1) "" else "s"} connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Live · updates as phones join",
                        style = MaterialTheme.typography.bodySmall,
                        color = AddaMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        OfflineStatusPill()
        Spacer(Modifier.height(14.dp))
        AddaPrimaryButton(
            text = "Open Q&A",
            onClick = onOpenQa,
            leadingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WifiJoinCard(
    ssid: String,
    passphrase: String,
    onCopySsid: () -> Unit,
    onCopyPass: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = AddaPrimary, modifier = Modifier.size(18.dp))
                Text("Step 1 · Join this Wi-Fi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            CredRow(label = "Network", value = ssid, onCopy = onCopySsid)
            if (passphrase.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                CredRow(label = "Password", value = passphrase, onCopy = onCopyPass)
            }
        }
    }
}

@Composable
private fun CredRow(label: String, value: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AddaMuted)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = AddaSecondary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Copy $label",
            tint = AddaPrimary,
            modifier = Modifier.size(20.dp).clickable { onCopy() },
        )
    }
}

@Composable
private fun InfoNote(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AddaMuted,
        )
    }
}
