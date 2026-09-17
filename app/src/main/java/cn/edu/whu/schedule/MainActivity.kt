package cn.edu.whu.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.edu.whu.schedule.ui.ScheduleApp
import cn.edu.whu.schedule.ui.theme.LuoJiaTheme
import cn.edu.whu.schedule.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = (application as ScheduleApplication).database
        setContent {
            var theme by remember { mutableStateOf(ThemePreferences.load(this@MainActivity)) }
            LuoJiaTheme(style = theme) {
                ScheduleApp(
                    database = database,
                    theme = theme,
                    onThemeChanged = { selected ->
                        ThemePreferences.save(this@MainActivity, selected)
                        theme = selected
                    },
                )
            }
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
