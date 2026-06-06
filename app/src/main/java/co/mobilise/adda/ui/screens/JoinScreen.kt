package co.mobilise.adda.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.mobilise.adda.client.HostClient
import co.mobilise.adda.server.AddaServer
import co.mobilise.adda.state.AppViewModel
import co.mobilise.adda.ui.components.AddaOutlineButton
import co.mobilise.adda.ui.components.AddaPrimaryButton
import co.mobilise.adda.ui.components.QrScanner
import co.mobilise.adda.ui.theme.AddaError
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaOutline
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSurface
import kotlinx.coroutines.launch

@Composable
fun JoinScreen(
    app: AppViewModel,
    onConnected: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(app.displayName) }
    var code by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanAttempt by remember { mutableIntStateOf(0) }

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) camLauncher.launch(Manifest.permission.CAMERA)
    }

    fun connect(input: String) {
        if (verifying) return
        val base = HostClient.resolveBaseUrl(input)
        if (base == null) {
            error = "Galat QR ya code"
            scanAttempt++ // let the scanner try again
            return
        }
        error = null
        verifying = true
        scope.launch {
            val info = HostClient.info(base)
            verifying = false
            when {
                info == null -> {
                    error = "Host nahi mila — same Wi-Fi pe ho? Code/URL sahi hai?"
                    scanAttempt++
                }
                info.ended -> {
                    error = "Ye Adda session khatam ho chuka hai"
                    scanAttempt++
                }
                else -> {
                    // Parse robustly: handles http/https and a non-default port.
                    val uri = runCatching { java.net.URI(base) }.getOrNull()
                    val ip = uri?.host ?: base.substringAfter("://").substringBefore(":")
                    val port = uri?.port?.takeIf { it != -1 } ?: AddaServer.PORT
                    app.startClient(displayName = name)
                    app.hostIp = ip
                    app.serverPort = port
                    app.hostName = info.host
                    app.subject = info.subject
                    onConnected()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Join an Adda",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Host ka QR scan karo ya JOIN CODE daalo.",
            style = MaterialTheme.typography.bodyMedium,
            color = AddaMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Aapka naam") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = fieldColors(),
        )

        Spacer(Modifier.height(16.dp))

        // ---- Camera scanner ------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(AddaSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                key(scanAttempt) {
                    QrScanner(
                        onQr = { connect(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Scan frame overlay
                Box(
                    Modifier
                        .fillMaxSize(0.62f)
                        .border(2.dp, AddaPrimary, RoundedCornerShape(16.dp)),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Camera band hai",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    AddaOutlineButton(
                        text = "Allow camera",
                        leadingIcon = Icons.Rounded.CameraAlt,
                        onClick = { camLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- Manual code ---------------------------------------------------
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("JOIN CODE") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go,
            ),
            colors = fieldColors(),
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = AddaError,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (verifying) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = AddaPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text("Connecting…", color = AddaMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            AddaPrimaryButton(
                text = "Connect",
                onClick = { connect(code) },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AddaSurface,
    unfocusedContainerColor = AddaSurface,
    focusedBorderColor = AddaPrimary,
    unfocusedBorderColor = AddaOutline,
    cursorColor = AddaPrimary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = AddaMuted,
    unfocusedLabelColor = AddaMuted,
)
