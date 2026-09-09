package app.votify.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/**
 * «Цвета интерфейса» controls (Настройки → Интерфейс): quick palette swatches, manual
 * per-slot colors and the HSV picker dialog. Shared by the Interface settings screen.
 */

/** Round palette swatch for the quick-pick row. */
@Composable
fun PaletteSwatch(color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            border = androidx.compose.foundation.BorderStroke(2.dp, VotifyColors.BorderSubtle),
            modifier = Modifier.size(42.dp),
        ) {}
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
    }
}

/** One interface color: name, hex value and a preview dot; opens the picker. */
@Composable
fun ColorSlotRow(label: String, hex: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray),
                )
                .border(androidx.compose.foundation.BorderStroke(1.dp, VotifyColors.BorderSubtle), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VotifyColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(hex.uppercase(), style = MaterialTheme.typography.labelMedium, color = VotifyColors.TextMuted)
    }
}

/** HSV color picker: hue / saturation / brightness sliders + hex input + live preview. */
@Composable
fun ColorPickerDialog(title: String, initialHex: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = runCatching { Color(android.graphics.Color.parseColor(initialHex)) }.getOrDefault(Color.White)
    val hsv = remember { floatArrayOf(0f, 0f, 1f) }
    android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var bri by remember { mutableFloatStateOf(hsv[2]) }
    var hexDraft by remember(initialHex) { mutableStateOf(initialHex.uppercase()) }

    fun hsvHex() = "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri)) and 0xFFFFFF)
    val preview = runCatching { Color(android.graphics.Color.parseColor(hexDraft)) }
        .getOrDefault(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri))))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VotifyColors.SurfaceContainerHigh,
        titleContentColor = VotifyColors.TextPrimary,
        textContentColor = VotifyColors.TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(preview),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.color_hue), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
                Slider(value = hue, onValueChange = { hue = it; hexDraft = hsvHex() }, valueRange = 0f..360f)
                Text(stringResource(R.string.color_saturation), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
                Slider(value = sat, onValueChange = { sat = it; hexDraft = hsvHex() })
                Text(stringResource(R.string.color_brightness), style = MaterialTheme.typography.labelSmall, color = VotifyColors.TextMuted)
                Slider(value = bri, onValueChange = { bri = it; hexDraft = hsvHex() })
                Spacer(Modifier.height(8.dp))
                VotifyTextField(hexDraft, { hexDraft = it.uppercase() }, "#RRGGBB")
            }
        },
        confirmButton = {
            Text(
                stringResource(R.string.action_save),
                color = VotifyColors.Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onApply("#" + hexDraft.trim().removePrefix("#")) }
                    .padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.action_cancel),
                color = VotifyColors.TextMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        },
    )
}
