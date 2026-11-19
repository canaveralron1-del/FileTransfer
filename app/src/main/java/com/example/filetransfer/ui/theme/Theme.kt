package com.example.filetransfer.ui.theme

import android.animation.ValueAnimator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** True when system animator duration scale is 0 (reduce-motion / a11y). */
val LocalReduceMotion = staticCompositionLocalOf { false }

private fun slateEmeraldScheme(primary: Color = Emerald500) = darkColorScheme(
    primary = primary,
    onPrimary = Slate900,
    primaryContainer = if (primary == Emerald500) Emerald900 else primary.copy(alpha = 0.25f),
    onPrimaryContainer = if (primary == Emerald500) Emerald100 else Color.White,
    secondary = Slate400,
    onSecondary = Slate900,
    secondaryContainer = Slate700,
    onSecondaryContainer = Slate50,
    tertiary = primary,
    background = Slate900,
    onBackground = Slate50,
    surface = Slate800,
    onSurface = Slate50,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    outlineVariant = Slate700.copy(alpha = 0.6f),
    error = ErrorRed,
    onError = Slate900,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Slate50,
    inverseOnSurface = Slate900,
    surfaceTint = Slate700,
    scrim = Color.Black
)

val SlateEmeraldScheme = slateEmeraldScheme(Emerald500)
val SlateVioletScheme = slateEmeraldScheme(Violet500)
val SlateTealScheme = slateEmeraldScheme(Teal500)
val SlateRoseScheme = slateEmeraldScheme(Rose500)
val SlateAmberScheme = slateEmeraldScheme(Amber500)

/** Status colors exposed outside Material roles (idle/transfer/success/error cards). */
data class StatusColors(
    val idle: Color,
    val transfer: Color,
    val success: Color,
    val failed: Color
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Slate400, TransferBlue, SuccessGreen, ErrorRed)
}

@Composable
fun FileTransferTheme(
    palette: String = "default",
    content: @Composable () -> Unit
) {
    val scheme = when (palette) {
        "emerald" -> SlateEmeraldScheme
        "violet" -> SlateVioletScheme
        "teal" -> SlateTealScheme
        "rose" -> SlateRoseScheme
        "amber" -> SlateAmberScheme
        else -> SlateEmeraldScheme
    }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides defaultStatusColors(scheme),
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}

private fun defaultStatusColors(scheme: androidx.compose.material3.ColorScheme) = StatusColors(
    idle = scheme.onSurfaceVariant,
    transfer = TransferBlue,
    success = SuccessGreen,
    failed = ErrorRed
)
