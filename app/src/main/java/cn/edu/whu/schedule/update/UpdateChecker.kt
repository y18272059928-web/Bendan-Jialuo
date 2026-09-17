package cn.edu.whu.schedule.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val latestVersion: String = "",
    val releaseUrl: String = "",
    val message: String,
)

object UpdateChecker {
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/y18272059928-web/Bendan-Jialuo/releases/latest"

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Bendan-Jialuo-Android/$currentVersion")
                if (connection.responseCode !in 200..299) {
                    error("GitHub 返回 HTTP ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val latest = json.optString("tag_name").removePrefix("v")
                val url = json.optString("html_url")
                require(latest.isNotBlank() && url.startsWith("https://github.com/")) {
                    "GitHub Release 信息不完整"
                }
                val available = compareVersions(latest, currentVersion) > 0
                UpdateCheckResult(
                    updateAvailable = available,
                    latestVersion = latest,
                    releaseUrl = url,
                    message = if (available) "发现新版本 v$latest。" else "已经是最新版。",
                )
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            UpdateCheckResult(
                updateAvailable = false,
                message = "检查失败：${error.message ?: "请检查网络后重试"}",
            )
        }
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = left.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(a.size, b.size)) { index ->
            val difference = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}