package cn.edu.whu.schedule.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmCreamColors = lightColorScheme(
    primary = Color(0xFF8A6843),
    onPrimary = Color(0xFFFFFBF2),
    primaryContainer = Color(0xFFF2DFC2),
    onPrimaryContainer = Color(0xFF4B351F),
    secondary = Color(0xFF9B6B4E),
    onSecondary = Color(0xFFFFFBF4),
    secondaryContainer = Color(0xFFF4DDCF),
    onSecondaryContainer = Color(0xFF503326),
    tertiary = Color(0xFF6F7650),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8E8),
    onBackground = Color(0xFF3E3427),
    surface = Color(0xFFFFFCF4),
    onSurface = Color(0xFF3E3427),
    surfaceVariant = Color(0xFFF2E7D3),
    onSurfaceVariant = Color(0xFF625443),
    outline = Color(0xFF9B876F),
    outlineVariant = Color(0xFFDCCDB8),
    error = Color(0xFF9B3E32),
)

@Composable
fun LuoJiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // Deliberately stay warm and light even when the phone uses dark mode.
        colorScheme = WarmCreamColors,
        content = content,
    )
}
