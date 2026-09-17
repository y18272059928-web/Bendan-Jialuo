package cn.edu.whu.schedule.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeStyle(val label: String, val description: String) {
    WARM_CREAM("暖米色", "柔和奶油色，当前默认"),
    GREEN_TEA("青茶色", "清爽的珞珈绿与米白"),
    PEACH("樱花色", "温暖的浅粉与杏色"),
}

object ThemePreferences {
    private const val PREFS = "appearance"
    private const val KEY_THEME = "theme"

    fun load(context: Context): AppThemeStyle = runCatching {
        AppThemeStyle.valueOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, AppThemeStyle.WARM_CREAM.name)
                .orEmpty(),
        )
    }.getOrDefault(AppThemeStyle.WARM_CREAM)

    fun save(context: Context, style: AppThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, style.name)
            .apply()
    }
}

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
    onTertiary = Color.White,
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

private val GreenTeaColors = lightColorScheme(
    primary = Color(0xFF477568),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E9DE),
    onPrimaryContainer = Color(0xFF193C33),
    secondary = Color(0xFF82714E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E5C7),
    onSecondaryContainer = Color(0xFF443A24),
    tertiary = Color(0xFF6F7650),
    background = Color(0xFFF8F6E9),
    onBackground = Color(0xFF30352F),
    surface = Color(0xFFFFFEF5),
    onSurface = Color(0xFF30352F),
    surfaceVariant = Color(0xFFE5EBE2),
    onSurfaceVariant = Color(0xFF4E5B54),
    outline = Color(0xFF77877E),
    outlineVariant = Color(0xFFC6D0C8),
    error = Color(0xFF9B3E32),
)

private val PeachColors = lightColorScheme(
    primary = Color(0xFF9A5F58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DCD5),
    onPrimaryContainer = Color(0xFF522D2A),
    secondary = Color(0xFF8A6B47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E2C8),
    onSecondaryContainer = Color(0xFF483722),
    tertiary = Color(0xFF767052),
    background = Color(0xFFFFF7EF),
    onBackground = Color(0xFF3E322F),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF3E322F),
    surfaceVariant = Color(0xFFF4E4DD),
    onSurfaceVariant = Color(0xFF66524C),
    outline = Color(0xFF9B7D73),
    outlineVariant = Color(0xFFDEC9C1),
    error = Color(0xFF9B3E32),
)

private fun colors(style: AppThemeStyle): ColorScheme = when (style) {
    AppThemeStyle.WARM_CREAM -> WarmCreamColors
    AppThemeStyle.GREEN_TEA -> GreenTeaColors
    AppThemeStyle.PEACH -> PeachColors
}

@Composable
fun LuoJiaTheme(
    style: AppThemeStyle = AppThemeStyle.WARM_CREAM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colors(style),
        content = content,
    )
}