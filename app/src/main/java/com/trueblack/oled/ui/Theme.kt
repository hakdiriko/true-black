package com.trueblack.oled.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)

/**
 * Greys the app itself uses. While True Black is active our own UI passes through the same matrix,
 * so every grey is pre-lifted above the black floor: it then renders at exactly the intended tone.
 * Result: the app looks identical on and off, and nothing in it ever disappears into the floor.
 */
@Immutable
data class Tones(
    val mute: Color,     // secondary text
    val faint: Color,    // tertiary text / disabled
    val hair: Color,     // dividers, outlines
    val track: Color,    // slider inactive track
    val well: Color,     // code block background
)

fun survivingGrey(target: Float, threshold: Float, active: Boolean): Color {
    val v = if (active) target * (1f - threshold) + threshold else target
    return Color(v, v, v)
}

fun tonesFor(threshold: Float, active: Boolean) = Tones(
    mute = survivingGrey(0.56f, threshold, active),
    faint = survivingGrey(0.36f, threshold, active),
    hair = survivingGrey(0.16f, threshold, active),
    track = survivingGrey(0.22f, threshold, active),
    well = survivingGrey(0.07f, threshold, active),
)

private val Scheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    secondary = Color(0xFF8F8F8F),
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Ink,
    onSurfaceVariant = Color(0xFF8F8F8F),
    outline = Color(0xFF292929),
    error = Color(0xFFFF6B6B),
    onError = Ink,
)

private val Type = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

@Composable
fun TrueBlackTheme(content: @Composable () -> Unit) {
    val scheme = remember { Scheme }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
