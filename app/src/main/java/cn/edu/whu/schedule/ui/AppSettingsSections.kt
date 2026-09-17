package cn.edu.whu.schedule.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import cn.edu.whu.schedule.BuildConfig
import cn.edu.whu.schedule.update.UpdateCheckResult
import cn.edu.whu.schedule.update.UpdateChecker
import cn.edu.whu.schedule.ui.theme.AppThemeStyle
import kotlinx.coroutines.launch

@Composable
fun AppearanceSection(
    selected: AppThemeStyle,
    onSelected: (AppThemeStyle) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("外观", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("选择舒服的浅色主题，切换后立即生效。")
            AppThemeStyle.entries.forEach { style ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (style == selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    onClick = { onSelected(style) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(style.label, fontWeight = FontWeight.SemiBold)
                        Text(style.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("检查更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("当前版本：v${BuildConfig.VERSION_NAME}")
            result?.let {
                Text(
                    it.message,
                    color = if (it.updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                            checking = false
                        }
                    },
                ) { Text(if (checking) "正在检查…" else "检查 GitHub 更新") }
                result?.takeIf { it.updateAvailable && it.releaseUrl.isNotBlank() }?.let { update ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri()))
                        },
                    ) { Text("打开下载页") }
                }
            }
            Text("只在点击时访问 GitHub Releases，不上传课表或账号信息。", style = MaterialTheme.typography.bodySmall)
        }
    }
}