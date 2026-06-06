package co.mobilise.adda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import co.mobilise.adda.ui.theme.AddaOnPrimary
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSecondary

/** The Adda mark: an amber rounded square with a chat bubble. */
@Composable
fun AddaLogo(size: Int = 88, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (size >= 64) Modifier.amberGlow(cornerRadius = size / 4) else Modifier)
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Brush.linearGradient(listOf(AddaSecondary, AddaPrimary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ChatBubble,
            contentDescription = "Adda",
            tint = AddaOnPrimary,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}
