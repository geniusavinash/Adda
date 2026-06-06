package co.mobilise.adda.ui.qa

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.mobilise.adda.ui.theme.AddaBackground
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaOutline
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSecondary
import co.mobilise.adda.ui.theme.AddaSurface

private sealed interface Segment {
    data class Body(val text: String) : Segment
    data class Code(val text: String) : Segment
}

/**
 * Renders an answer: normal prose in body text, anything inside ``` fences as a
 * monospace code/formula block. Handles a still-open fence while streaming
 * (the trailing unmatched ``` is shown as code so formulas appear live).
 */
@Composable
fun MessageContent(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { parseFenced(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is Segment.Body ->
                    if (seg.text.isNotBlank()) {
                        Text(
                            text = seg.text.trim(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                        )
                    }

                is Segment.Code -> CodeBlock(seg.text)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val text = code.trim('\n')
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    Surface(
        color = AddaBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AddaOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = text,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 38.dp, top = 12.dp, bottom = 12.dp),
                color = AddaSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            // Copy just this block (stays pinned while the code scrolls).
            TapIconButton(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "Copy code",
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                },
                background = true,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

/**
 * Compact, unobtrusive icon button used for answer/code actions.
 * Muted by default ([AddaMuted]), amber ([AddaPrimary]) while pressed.
 */
@Composable
internal fun TapIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .then(if (background) Modifier.background(AddaSurface.copy(alpha = 0.85f)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(if (background) 5.dp else 7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (pressed) AddaPrimary else AddaMuted,
            modifier = Modifier.size(if (background) 15.dp else 18.dp),
        )
    }
}

private fun parseFenced(text: String): List<Segment> {
    if (!text.contains("```")) return listOf(Segment.Body(text))
    val parts = text.split("```")
    return parts.mapIndexedNotNull { i, part ->
        if (i % 2 == 0) {
            Segment.Body(part)
        } else {
            Segment.Code(stripLangLine(part))
        }
    }
}

/** Drop a leading language tag line like "kotlin\n..." but keep one-line formulas. */
private fun stripLangLine(raw: String): String {
    val body = raw.trimStart('\n')
    val nl = body.indexOf('\n')
    if (nl < 0) return body
    val firstLine = body.substring(0, nl).trim()
    val looksLikeLang = firstLine.isNotEmpty() &&
        firstLine.length <= 15 &&
        firstLine.all { it.isLetterOrDigit() || it == '+' || it == '#' }
    return if (looksLikeLang) body.substring(nl + 1) else body
}
