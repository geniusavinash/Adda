package co.mobilise.adda.ui.qa

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.mobilise.adda.ui.components.AddaLogo
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaOnPrimary
import co.mobilise.adda.ui.theme.AddaOutline
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSecondary
import co.mobilise.adda.ui.theme.AddaSurface
import kotlin.math.absoluteValue

private val avatarPalette = listOf(
    Color(0xFF6C8CFF), Color(0xFF39D98A), Color(0xFFFF8FB1),
    Color(0xFFFFC15E), Color(0xFF8E7CFF), Color(0xFF4EC6E0),
)

private fun avatarColor(name: String): Color =
    avatarPalette[(name.hashCode().absoluteValue) % avatarPalette.size]

@Composable
private fun Avatar(name: String, isAi: Boolean, size: Int = 26) {
    if (isAi) {
        AddaLogo(size = size)
        return
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** One row in the chat list — student question or Adda answer. */
@Composable
fun ChatRow(message: ChatMessage, speakingId: String?, onSpeak: () -> Unit) {
    if (message.isAi) AiRow(message, speakingId, onSpeak) else UserRow(message)
}

@Composable
private fun UserRow(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        // asker name + avatar above the bubble
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.author,
                style = MaterialTheme.typography.labelMedium,
                color = AddaMuted,
            )
            Spacer(Modifier.width(6.dp))
            Avatar(message.author, isAi = false, size = 22)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                .background(AddaPrimary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                message.image?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(170.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                if (message.image == null && message.fileName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = AddaOnPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            message.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = AddaOnPrimary,
                        )
                    }
                    if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                if (message.text.isNotBlank()) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AddaOnPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiRow(message: ChatMessage, speakingId: String?, onSpeak: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(message.author, isAi = true, size = 22)
            Spacer(Modifier.width(6.dp))
            Text("Adda", style = MaterialTheme.typography.labelMedium, color = AddaSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(AddaSurface)
                .border(1.dp, AddaOutline, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (message.streaming && message.text.isBlank()) {
                ThinkingShimmer()
            } else {
                Column {
                    MessageContent(
                        text = message.text,
                        textColor = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!message.streaming && message.text.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        AnswerActions(message = message, speakingId = speakingId, onSpeak = onSpeak)
                    }
                }
            }
        }
    }
}

/**
 * Compact Copy / Share / Speak actions in the bottom-right of an AI answer.
 * Shown only on completed AI answers (never on user bubbles or the shimmer).
 * Fully offline: clipboard is local, Share uses the system ACTION_SEND intent.
 */
@Composable
private fun ColumnScope.AnswerActions(
    message: ChatMessage,
    speakingId: String?,
    onSpeak: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val speaking = speakingId == message.id.toString()

    Row(
        modifier = Modifier.align(Alignment.End),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapIconButton(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = "Copy answer",
            onClick = {
                clipboard.setText(AnnotatedString(message.text))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            },
        )
        TapIconButton(
            icon = Icons.Rounded.Share,
            contentDescription = "Share answer",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val shareText = "Q: ${message.question}\n\nA: ${message.text}\n\n" +
                    "— via Adda (offline, on-device AI)"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Share answer"))
                }
            },
        )
        TapIconButton(
            icon = if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
            contentDescription = if (speaking) "Stop" else "Speak answer",
            onClick = onSpeak,
        )
    }
}

/** Shimmering placeholder bars while the first token is on its way. */
@Composable
fun ThinkingShimmer() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val x by transition.animateFloat(
        initialValue = -250f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        colors = listOf(AddaOutline, AddaPrimary.copy(alpha = 0.45f), AddaOutline),
        start = Offset(x, 0f),
        end = Offset(x + 250f, 0f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Thinking…", style = MaterialTheme.typography.labelMedium, color = AddaMuted)
        Box(Modifier.fillMaxWidth(0.85f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Box(Modifier.fillMaxWidth(0.6f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
    }
}

/** Compact EN / हिं segmented toggle for the top bar. */
@Composable
fun LangToggle(lang: AnswerLang, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AddaSurface)
            .border(1.dp, AddaOutline, CircleShape)
            .clickable { onToggle() }
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnswerLang.entries.forEach { entry ->
            val selected = entry == lang
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) AddaPrimary else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) AddaOnPrimary else AddaMuted,
                )
            }
        }
    }
}
