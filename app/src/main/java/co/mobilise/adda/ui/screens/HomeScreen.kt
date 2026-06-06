package co.mobilise.adda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.mobilise.adda.ui.components.AddaLogo
import co.mobilise.adda.ui.components.AddaOutlineButton
import co.mobilise.adda.ui.components.AddaPrimaryButton
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.util.OfflineStatusPill

@Composable
fun HomeScreen(
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        AddaLogo(size = 84)
        Spacer(Modifier.height(20.dp))
        Text(
            "Adda",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "One phone becomes the AI.\nThe whole group asks doubts — no internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = AddaMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        AddaPrimaryButton(
            text = "Host an Adda",
            onClick = onHost,
            leadingIcon = Icons.Rounded.Wifi,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        AddaOutlineButton(
            text = "Join an Adda",
            onClick = onJoin,
            leadingIcon = Icons.Rounded.QrCodeScanner,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        OfflineStatusPill()
        Spacer(Modifier.height(8.dp))
    }
}
