package nl.spellenweekend.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import nl.spellenweekend.app.data.MemberStatus

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE9D7),
    onPrimaryContainer = Color(0xFF0B2E1E),
    secondary = Color(0xFFA86F12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B0),
    onSecondaryContainer = Color(0xFF3A2600),
    tertiary = Color(0xFF7D4A72),
    tertiaryContainer = Color(0xFFF6D8EE),
    onTertiaryContainer = Color(0xFF32112C),
    background = Color(0xFFF8F6F0),
    surface = Color(0xFFF8F6F0),
    surfaceVariant = Color(0xFFE3E4DB),
    surfaceContainer = Color(0xFFEEEDE5),
    surfaceContainerHigh = Color(0xFFE8E7DF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3AE),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF175239),
    onPrimaryContainer = Color(0xFFCBE9D7),
    secondary = Color(0xFFF2BE5E),
    onSecondary = Color(0xFF412C00),
    secondaryContainer = Color(0xFF5E4100),
    onSecondaryContainer = Color(0xFFFFE2B0),
    tertiary = Color(0xFFE6B4DA),
    tertiaryContainer = Color(0xFF633459),
    onTertiaryContainer = Color(0xFFF6D8EE),
    background = Color(0xFF121512),
    surface = Color(0xFF121512),
    surfaceVariant = Color(0xFF3F4942),
    surfaceContainer = Color(0xFF1C201C),
    surfaceContainerHigh = Color(0xFF262A26),
)

@Composable
fun SpellenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}

fun statusColor(s: MemberStatus): Color = when (s) {
    MemberStatus.BESCHIKBAAR -> Color(0xFF2E9D5B)
    MemberStatus.RESERVEERBAAR -> Color(0xFFE0A21B)
    MemberStatus.BEZET -> Color(0xFFD0453C)
}
