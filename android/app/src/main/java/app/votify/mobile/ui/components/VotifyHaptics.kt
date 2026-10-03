package app.votify.mobile.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

object VotifyHaptics {
    fun click(view: View? = null, context: Context? = null) {
        runCatching {
            val viewHandled = view?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            ) == true

            if (!viewHandled && context != null) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(12)
                    }
                }
            }
        }
    }

    fun tick(view: View? = null, context: Context? = null) {
        runCatching {
            val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                HapticFeedbackConstants.CLOCK_TICK
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            val viewHandled = view?.performHapticFeedback(
                feedback,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            ) == true

            if (!viewHandled && context != null) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(6)
                    }
                }
            }
        }
    }
}

/** Composable hook returning a callback to trigger light click haptics. */
@Composable
fun rememberVotifyHaptic(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) {
        { VotifyHaptics.click(view, context) }
    }
}

/** Modifier that performs light haptic feedback automatically upon clicking. */
fun Modifier.votifyClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: androidx.compose.foundation.Indication? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val context = LocalContext.current
    if (interactionSource != null) {
        this.clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClick = {
                VotifyHaptics.click(view, context)
                onClick()
            },
        )
    } else {
        this.clickable(enabled = enabled) {
            VotifyHaptics.click(view, context)
            onClick()
        }
    }
}
