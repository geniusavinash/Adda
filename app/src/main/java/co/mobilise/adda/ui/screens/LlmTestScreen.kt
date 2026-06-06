package co.mobilise.adda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.mobilise.adda.llm.LlmService
import co.mobilise.adda.llm.LlmState
import co.mobilise.adda.ui.components.AddaPrimaryButton
import co.mobilise.adda.ui.theme.AddaError
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaSuccess
import kotlinx.coroutines.launch

/**
 * Step 2 verification harness — NOT part of the real nav graph.
 *
 * To run it, temporarily swap `AddaNavHost()` in MainActivity with
 * `LlmTestScreen()`. Push the model first, e.g.:
 *   adb push gemma.task /data/local/tmp/
 *   adb shell run-as co.mobilise.adda cp /data/local/tmp/gemma.task files/
 * (the screen prints the exact files-dir path it's looking at).
 */
@Composable
fun LlmTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by LlmService.state.collectAsState()

    val modelPath = remember { LlmService.resolveModelPath(context) }
    var question by remember { mutableStateOf("Pythagoras theorem kya hai?") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(modelPath) {
        if (modelPath != null) {
            runCatching { LlmService.init(modelPath, context) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "LLM Test · Step 2",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Engine status
        val (statusText, statusColor) = when (val s = state) {
            is LlmState.Idle -> "Idle" to AddaMuted
            is LlmState.Loading -> "Loading model…" to MaterialTheme.colorScheme.primary
            is LlmState.Ready -> "Ready · on-device" to AddaSuccess
            is LlmState.Error -> "Error: ${s.message}" to AddaError
        }
        Text(statusText, color = statusColor, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Model: ${modelPath ?: "NOT FOUND in ${context.filesDir.absolutePath}"}",
            color = AddaMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default,
        )

        AddaPrimaryButton(
            text = if (busy) "Thinking…" else "Ask",
            enabled = state is LlmState.Ready && !busy && question.isNotBlank(),
            onClick = {
                answer = ""
                busy = true
                scope.launch {
                    try {
                        LlmService.ask(question).collect { chunk -> answer += chunk }
                    } catch (t: Throwable) {
                        answer = "⚠️ ${t.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (busy && answer.isEmpty()) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.height(24.dp),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(
                text = answer.ifEmpty { "Answer streams here…" },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                color = if (answer.isEmpty()) AddaMuted else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
    }
}
