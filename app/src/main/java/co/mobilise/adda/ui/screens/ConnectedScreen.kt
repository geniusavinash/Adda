package co.mobilise.adda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.mobilise.adda.state.AppViewModel
import co.mobilise.adda.ui.components.AddaPrimaryButton
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaSecondary
import co.mobilise.adda.ui.theme.AddaSuccess

@Composable
fun ConnectedScreen(
    app: AppViewModel,
    onEnterQa: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AddaSuccess.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = AddaSuccess,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Connected!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You're in ${app.hostName.ifBlank { "the host" }}'s Adda.",
            style = MaterialTheme.typography.bodyLarge,
            color = AddaMuted,
            textAlign = TextAlign.Center,
        )
        if (app.subject.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Subject · ${app.subject}",
                style = MaterialTheme.typography.bodyMedium,
                color = AddaSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))

        AddaPrimaryButton(
            text = "Enter Q&A",
            onClick = onEnterQa,
            leadingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
