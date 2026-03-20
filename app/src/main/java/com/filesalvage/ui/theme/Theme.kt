package com.filesalvage.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors
val RedPrimary    = Color(0xFFFF2020)
val RedDark       = Color(0xFF8B0000)
val RedAccent     = Color(0xFFFF4444)
val RedGlow       = Color(0x33FF2020)

val BgDeep        = Color(0xFF0A0000)
val BgCard        = Color(0xFF1A0808)
val BgCardBorder  = Color(0x22FF2020)

val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF)
val TextMuted     = Color(0x44FFFFFF)

val Success       = Color(0xFF34C759)
val Warning       = Color(0xFFFF9500)
val Info          = Color(0xFF147EFB)

private val DarkColorScheme = darkColorScheme(
    primary          = RedPrimary,
    onPrimary        = Color.White,
    primaryContainer = RedDark,
    secondary        = RedAccent,
    background       = BgDeep,
    surface          = BgCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    error            = RedPrimary,
)

@Composable
fun FileSalvageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
