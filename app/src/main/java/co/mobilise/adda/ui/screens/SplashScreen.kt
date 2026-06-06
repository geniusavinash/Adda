package co.mobilise.adda.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.mobilise.adda.llm.LlmService
import co.mobilise.adda.llm.LlmState
import co.mobilise.adda.ui.components.AddaLogo
import co.mobilise.adda.ui.components.AddaOutlineButton
import co.mobilise.adda.ui.components.StatusPill
import co.mobilise.adda.ui.theme.AddaError
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaPrimary

/**
 * Brand splash that actually loads the on-device model. When [LlmState.Ready]
 * we navigate Home. If the model is missing / fails to load we show a clear
 * error (Step 8 spec: "na mile to clearly fail") with a dev escape hatch.
 */
@Composable
fun SplashScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val state by LlmService.state.collectAsState()

    // Kick off the load once.
    LaunchedEffect(Unit) {
        val path = LlmService.resolveModelPath(context)
        if (path == null) {
            // resolveModelPath returns null -> surface a not-found error via init
            runCatching { LlmService.init(modelPath = "(missing)", context = context) }
            return@LaunchedEffect
        }
        runCatching { LlmService.init(path, context) }
    }

    // Navigate as soon as the engine is ready.
    LaunchedEffect(state) {
        if (state is LlmState.Ready) onReady()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            AddaLogo(size = 96)
            Spacer(Modifier.height(24.dp))
            Text(
                "Adda",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your group's offline AI",
                style = MaterialTheme.typography.bodyMedium,
                color = AddaMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(44.dp))

            when (val s = state) {
                is LlmState.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AddaError,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    AddaOutlineButton(
                        text = "Continue anyway",
                        onClick = onReady, // dev escape — AI calls will error until model present
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        color = AddaPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading on-device AI…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AddaMuted,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
            AnimatedVisibility(visible = state !is LlmState.Error) {
                StatusPill(text = "Offline · runs fully on-device")
            }
        }
    }
}
