package cn.edu.whu.schedule.ui

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Message
import android.provider.Settings
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cn.edu.whu.schedule.data.CourseOccurrence
import cn.edu.whu.schedule.BuildConfig
import cn.edu.whu.schedule.data.ScheduleDatabase
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.domain.OccurrenceEngine
import cn.edu.whu.schedule.importer.AdaptiveWhuScheduleImporter
import cn.edu.whu.schedule.importer.ImportResult
import cn.edu.whu.schedule.importer.WHU_CAPTURE_SCRIPT
import cn.edu.whu.schedule.importer.WHU_PORTAL_COLLECT_SCRIPT
import cn.edu.whu.schedule.importer.WHU_PORTAL_READ_SCRIPT
import cn.edu.whu.schedule.notification.ReminderScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

private enum class Destination(val label: String) {
    TODAY("今天"), WEEK("周课表"), MINE("我的"),
}

// The mobile portal is designed for the official Chaoxing container and can
// leave its application centre blank when the native JS bridge is absent.
// WHU also supports the regular PC information portal, so use an ordinary
// desktop-browser identity instead of impersonating the official application.
private const val DESKTOP_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

// Authentication may land on either the mobile or PC portal. Import supports
// both because the signed-in HTTPS timetable endpoint is shared by the origin.
private const val WHU_PORTAL_URL = "https://zhlj.whu.edu.cn/casLogin"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(database: ScheduleDatabase) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Destination.TODAY) }
    var showImport by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(database.read()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("笨蛋珈珞", fontWeight = FontWeight.Bold)
                        Text(snapshot.semester.name, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = {
                            selected = item
                            if (item != Destination.MINE) showImport = false
                        },
                        icon = {
                            Icon(
                                when (item) {
                                    Destination.TODAY -> Icons.Outlined.Today
                                    Destination.WEEK -> Icons.Outlined.CalendarMonth
                                    Destination.MINE -> Icons.Outlined.Person
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Destination.TODAY -> TodayScreen(snapshot)
                Destination.WEEK -> WeekScreen(snapshot)
                Destination.MINE -> if (showImport) {
                    ImportScreen(
                        onClose = { showImport = false },
                        onImported = {
                            database.replace(it)
                            snapshot = database.read()
                            if (!snapshot.semester.name.contains("首周待确认")) {
                                ReminderScheduler.rescheduleAll(context, snapshot)
                            } else {
                                ReminderScheduler.pauseAll(context)
                            }
                            showImport = false
                        },
                    )
                } else {
                    MyScreen(
                        snapshot = snapshot,
                        onImport = { showImport = true },
                        onScheduleChanged = { updated ->
                            database.replace(updated)
                            snapshot = database.read()
                            ReminderScheduler.rescheduleAll(context, snapshot)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(snapshot: ScheduleSnapshot) {
    val today = LocalDate.now()
    val occurrences = OccurrenceEngine.onDate(snapshot, today)
    val week = OccurrenceEngine.teachingWeek(snapshot, today)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE")), style = MaterialTheme.typography.headlineMedium)
        Text(week?.let { "教学第 $it 周" } ?: "当前不在教学周内", color = MaterialTheme.colorScheme.secondary)
        if (occurrences.isEmpty()) {
            EmptyDay()
        } else {
            occurrences.forEach { CourseCard(it) }
        }
    }
}

@Composable
private fun EmptyDay() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp)) {
            Text("今天没有课程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("把时间留给阅读、实验和休息。")
        }
    }
}

@Composable
private fun CourseCard(occurrence: CourseOccurrence) {
    val accent = Color(occurrence.course.colorArgb)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(Modifier.width(5.dp).height(68.dp).background(accent, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(occurrence.course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${occurrence.meeting.startTime}–${occurrence.meeting.endTime}  ·  ${occurrence.meeting.room}")
                Text("${occurrence.course.teacher}  ·  第${occurrence.meeting.startPeriod}–${occurrence.meeting.endPeriod}节", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WeekScreen(snapshot: ScheduleSnapshot) {
    val today = LocalDate.now()
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var weekOffset by remember { mutableIntStateOf(0) }
    val monday = currentMonday.plusWeeks(weekOffset.toLong())
    var selectedDay by remember { mutableIntStateOf(today.dayOfWeek.value - 1) }
    val date = monday.plusDays(selectedDay.toLong())
    val courses = OccurrenceEngine.onDate(snapshot, date)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
            }
            Text(
                OccurrenceEngine.teachingWeek(snapshot, monday)?.let { "教学第 $it 周" } ?: "非教学周",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0..6).forEach { offset ->
                val day = monday.plusDays(offset.toLong())
                Button(
                    onClick = { selectedDay = offset },
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                    colors = if (selectedDay == offset) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Text("${"一二三四五六日"[offset]}\n${day.dayOfMonth}")
                }
            }
        }
        if (weekOffset != 0) {
            OutlinedButton(onClick = {
                weekOffset = 0
                selectedDay = today.dayOfWeek.value - 1
            }) { Text("回到本周") }
        }
        Spacer(Modifier.height(16.dp))
        Text(date.format(DateTimeFormatter.ofPattern("M月d日")), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        if (courses.isEmpty()) Text("这一天没有课程", color = MaterialTheme.colorScheme.secondary)
        courses.forEach {
            CourseCard(it)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION") // saveFormData is kept off for older WebView implementations.
@Composable
private fun ImportScreen(
    onClose: () -> Unit,
    onImported: (ScheduleSnapshot) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentHost by remember { mutableStateOf("zhlj.whu.edu.cn") }
    var pageStatus by remember { mutableStateOf("正在打开智慧珞珈门户…") }
    var pendingImport by remember { mutableStateOf<ImportResult.Success?>(null) }
    var message by remember { mutableStateOf("请登录智慧珞珈；首页显示当前教学周后，直接点击“导入门户课表”。移动版或电脑版首页都可以，应用不会读取或保存密码。") }
    val importer = remember { AdaptiveWhuScheduleImporter() }
    BackHandler {
        if (canGoBack) webView?.goBack() else onClose()
    }
    // Keep the WebView alive when assigning it to Compose state. Using webView
    // as the effect key would dispose the just-created instance on recomposition.
    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                clearWhuSession(it)
                it.destroy()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回我的")
            }
            Text("从智慧珞珈导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(message, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
        Text(
            "当前页面：$currentHost",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            "页面状态：$pageStatus",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Row(
            Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = {
                    pendingImport = null
                    message = "正在从武大 HTTPS 门户读取全部教学周…"
                    recognizePortalSchedule(
                        webView = webView,
                        importer = importer,
                        onResult = { result ->
                            message = when (result) {
                                is ImportResult.Success -> {
                                    pendingImport = result
                                    result.note
                                }
                                is ImportResult.NeedsCapture -> result.reason
                                is ImportResult.Failure -> result.message
                            }
                        },
                        onMessage = { message = it },
                    )
                }) { Text("导入门户课表") }
            OutlinedButton(onClick = {
                webView?.loadUrl(WHU_PORTAL_URL)
                pendingImport = null
                message = "正在重新打开智慧珞珈登录入口。"
            }) { Text("门户首页") }
            OutlinedButton(onClick = {
                clearWhuSession(webView)
                webView?.loadUrl(WHU_PORTAL_URL)
                pendingImport = null
                message = "登录状态已清除，正在重新打开智慧珞珈门户。"
            }) { Text("退出登录") }
        }
        pendingImport?.let { preview ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导入预览", fontWeight = FontWeight.Bold)
                    Text("${preview.snapshot.semester.name} · ${preview.snapshot.courses.size} 门课程 · ${preview.snapshot.meetings.size} 条安排")
                    Text(
                        preview.snapshot.courses.take(4).joinToString("、") { it.name } +
                            if (preview.snapshot.courses.size > 4) "……" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("首周周一：${preview.snapshot.semester.firstMonday} · 共 ${preview.snapshot.semester.totalWeeks} 周", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clearWhuSession(webView)
                            pendingImport = null
                            onImported(preview.snapshot)
                        }) { Text("确认导入") }
                        OutlinedButton(onClick = {
                            pendingImport = null
                            message = "已取消，本机原课表没有变化。"
                        }) { Text("取消") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                WebView(context).apply {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    isSaveEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.saveFormData = false
                    settings.userAgentString = DESKTOP_BROWSER_USER_AGENT
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(true)
                    // The portal embeds some university-provided applications on a
                    // different HTTPS host. Their SSO session depends on third-party
                    // cookies; the whole session is still deleted when Import closes.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    if (
                        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    ) {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            WHU_CAPTURE_SCRIPT,
                            setOf("https://whu.edu.cn", "https://*.whu.edu.cn"),
                        )
                    }
                    val navigationPolicy = TrustedPortalNavigationPolicy()
                    webViewClient = TrustedPortalWebViewClient(
                        navigationPolicy = navigationPolicy,
                        onMessage = { message = it },
                        onNavigationChanged = { canGoBack = it },
                        onHostChanged = { currentHost = it },
                        onPageStatusChanged = { pageStatus = it },
                    )
                    webChromeClient = SameViewWebChromeClient(
                        mainWebView = this,
                        navigationPolicy = navigationPolicy,
                        onMessage = { message = it },
                        onProgressChanged = { progress -> pageStatus = "加载进度：$progress%" },
                    )
                    webView = this
                    CookieManager.getInstance().removeAllCookies {
                        CookieManager.getInstance().flush()
                        post { loadUrl(WHU_PORTAL_URL) }
                    }
                }
            },
        )
    }
}

private fun recognizePortalSchedule(
    webView: WebView?,
    importer: AdaptiveWhuScheduleImporter,
    onResult: (ImportResult) -> Unit,
    onMessage: (String) -> Unit,
) {
    val view = webView ?: run {
        onMessage("登录页面尚未就绪，请稍后重试。")
        return
    }
    startPortalCollection(view, importer, onResult, onMessage, attempt = 0)
}

private fun startPortalCollection(
    webView: WebView,
    importer: AdaptiveWhuScheduleImporter,
    onResult: (ImportResult) -> Unit,
    onMessage: (String) -> Unit,
    attempt: Int,
) {
    if (attempt >= 60) {
        onMessage("等待门户当前教学周信息超时，请刷新智慧珞珈首页后重试。")
        return
    }
    runCatching {
        webView.evaluateJavascript(WHU_PORTAL_COLLECT_SCRIPT) { rawResult ->
            when {
                rawResult.contains("__PORTAL_STARTED__") -> pollPortalSchedule(
                    webView = webView,
                    importer = importer,
                    onResult = onResult,
                    onMessage = onMessage,
                    attempt = 0,
                )
                rawResult.contains("__PORTAL_WAITING__") -> webView.postDelayed({
                    if (webView.isAttachedToWindow) startPortalCollection(
                        webView,
                        importer,
                        onResult,
                        onMessage,
                        attempt + 1,
                    )
                }, 500L)
                rawResult.contains("__NOT_PORTAL__") -> onMessage(
                    "请先点击“门户首页”并完成登录，回到智慧珞珈首页后再导入。",
                )
                else -> onMessage("无法启动门户课表读取，请返回智慧珞珈首页后重试。")
            }
        }
    }.onFailure {
        onMessage("无法读取门户页面，请退出导入页后重试。")
    }
}

private fun pollPortalSchedule(
    webView: WebView,
    importer: AdaptiveWhuScheduleImporter,
    onResult: (ImportResult) -> Unit,
    onMessage: (String) -> Unit,
    attempt: Int,
) {
    if (attempt >= 120) {
        onMessage("门户课表读取超时，请确认网络正常并停留在智慧珞珈首页后重试。")
        return
    }
    webView.postDelayed({
        if (!webView.isAttachedToWindow) return@postDelayed
        runCatching {
            webView.evaluateJavascript(WHU_PORTAL_READ_SCRIPT) { rawResult ->
                when {
                    rawResult.contains("__PORTAL_LOADING__") -> pollPortalSchedule(
                        webView,
                        importer,
                        onResult,
                        onMessage,
                        attempt + 1,
                    )
                    rawResult.contains("__PORTAL_ERROR__") -> {
                        val detail = rawResult
                            .substringAfter("__PORTAL_ERROR__:")
                            .trim('"')
                            .replace("\\\"", "\"")
                            .take(120)
                        onMessage("武大门户课表接口读取失败：$detail。请刷新智慧珞珈首页后重试。")
                    }
                    else -> onResult(importer.parseDocument(rawResult))
                }
            }
        }.onFailure {
            onMessage("门户页面已关闭，请重新打开导入页。")
        }
    }, 500L)
}

private fun clearWhuSession(webView: WebView?) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
    webView?.clearHistory()
    webView?.clearCache(true)
    webView?.clearFormData()
}

@SuppressLint("MissingOnRenderProcessGone") // Implemented below; AndroidX lint 1.17 does not detect the Kotlin override.
private class TrustedPortalWebViewClient(
    private val navigationPolicy: TrustedPortalNavigationPolicy,
    private val onMessage: (String) -> Unit,
    private val onNavigationChanged: (Boolean) -> Unit,
    private val onHostChanged: (String) -> Unit,
    private val onPageStatusChanged: (String) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val bridgeHost = request.url.host.orEmpty()
        if (
            request.url.scheme.equals("jsbridge", ignoreCase = true) &&
            (
                bridgeHost.equals("NotificationReady", ignoreCase = true) ||
                    bridgeHost.startsWith("PostNotificationWithId-", ignoreCase = true)
                )
        ) {
            // Official mobile pages use this navigation only to tell their
            // native container that rendering has finished. It carries no
            // account data and expects no response, so treating it as a no-op
            // is safer than pretending to be the official application.
            onPageStatusChanged("移动门户页面已就绪")
            return true
        }
        val allowed = navigationPolicy.allowRequest(view.url, request)
        if (!allowed) onMessage("为保护账号，已阻止不安全或无来源的地址：${request.url}")
        return !allowed
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        val uri = url.toUri()
        uri.host?.let { onHostChanged(it + uri.path.orEmpty()) }
        onPageStatusChanged("正在加载网页…")
        // Document-start injection covers WHU pages. Re-injecting is idempotent
        // and also enables capture on an HTTPS application host trusted at runtime.
        if (navigationPolicy.isTrusted(uri)) {
            view.evaluateJavascript(WHU_CAPTURE_SCRIPT, null)
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            onPageStatusChanged("加载失败：WebView ${error.errorCode}")
            onMessage("网页加载失败（${error.errorCode}）：${error.description}")
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) {
            onPageStatusChanged("服务器返回 HTTP ${errorResponse.statusCode}")
            onMessage("服务器拒绝或无法提供该页面（HTTP ${errorResponse.statusCode}）。")
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onMessage("登录网页进程意外停止，请退出导入页后重试；本机课表没有变化。")
        view.destroy()
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        val uri = url.toUri()
        uri.host?.let { onHostChanged(it + uri.path.orEmpty()) }
        onPageStatusChanged("网页加载完成${view.title?.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}")
        onNavigationChanged(view.canGoBack())
    }
}

/**
 * Starts with WHU domains only. An external host is trusted for this in-memory
 * import session only when a trusted page navigates to it via a user gesture or
 * an HTTPS redirect. Non-HTTPS schemes are never accepted.
 */
private class TrustedPortalNavigationPolicy {
    private val trustedExternalHosts = mutableSetOf<String>()

    fun isTrusted(uri: android.net.Uri): Boolean {
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase().orEmpty()
        return isWhuHost(host) || host in trustedExternalHosts
    }

    fun allowRequest(sourceUrl: String?, request: WebResourceRequest): Boolean {
        val target = request.url
        if (isTrusted(target)) return true
        if (target.scheme != "https") return false

        val sourceTrusted = sourceUrl?.toUri()?.let(::isTrusted) == true
        // A trusted portal may use JavaScript navigation without a gesture and
        // without Android marking it as an HTTP redirect. The trust is scoped
        // to this in-memory WebView session and HTTPS remains mandatory.
        val isPortalTransition = sourceTrusted
        if (isPortalTransition) target.host?.lowercase()?.let(trustedExternalHosts::add)
        return isPortalTransition
    }

    fun allowPopup(sourceUrl: String?, target: android.net.Uri, userGesture: Boolean): Boolean {
        if (target.scheme != "https" || !userGesture) return false
        val sourceTrusted = sourceUrl?.toUri()?.let(::isTrusted) == true
        if (sourceTrusted) target.host?.lowercase()?.let(trustedExternalHosts::add)
        return sourceTrusted
    }

    private fun isWhuHost(host: String): Boolean =
        host == "whu.edu.cn" || host.endsWith(".whu.edu.cn")
}

@SuppressLint("MissingOnRenderProcessGone") // The popup client implements the callback below; AndroidX lint misses the Kotlin object override.
private class SameViewWebChromeClient(
    private val mainWebView: WebView,
    private val navigationPolicy: TrustedPortalNavigationPolicy,
    private val onMessage: (String) -> Unit,
    private val onProgressChanged: (Int) -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (view === mainWebView) onProgressChanged(newProgress)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (!isUserGesture) {
            onMessage("已阻止网页自动弹出的窗口。请直接点击应用入口重试。")
            return false
        }

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        var handled = false

        fun handlePopup(url: String): Boolean {
            if (handled) return true
            val target = url.toUri()
            if (target.scheme == "about") return false
            handled = true
            if (navigationPolicy.allowPopup(view.url, target, isUserGesture)) {
                target.host?.let { onMessage("正在进入智慧珞珈接入服务：$it") }
                mainWebView.loadUrl(target.toString())
            } else {
                onMessage("为保护账号，已阻止新窗口地址：$target")
            }
            popup.stopLoading()
            popup.post { popup.destroy() }
            return true
        }

        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean =
                handlePopup(request.url.toString())

            override fun onPageStarted(popupView: WebView, url: String, favicon: android.graphics.Bitmap?) {
                handlePopup(url)
            }

            override fun onRenderProcessGone(popupView: WebView, detail: RenderProcessGoneDetail): Boolean {
                popupView.destroy()
                onMessage("网页弹出窗口进程已关闭，请重新点击入口。")
                return true
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}

@Composable
private fun MyScreen(
    snapshot: ScheduleSnapshot,
    onImport: () -> Unit,
    onScheduleChanged: (ScheduleSnapshot) -> Unit,
) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var exact by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
    }
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted
        if (granted && snapshot.remindersCanRun()) {
            ReminderScheduler.rescheduleAll(context, snapshot)
        }
    }
    val exactAlarmPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact && snapshot.remindersCanRun()) {
            ReminderScheduler.rescheduleAll(context, snapshot)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(snapshot.semester.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${snapshot.courses.size} 门课程 · ${snapshot.meetings.size} 条上课安排")
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("从智慧珞珈导入课表")
                }
                Text("重新导入会先显示预览，确认后才覆盖当前课表。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("每日 07:30 汇总今日课程")
                Text("每节课开始前 15 分钟提醒")
                Text(if (notificationsAllowed) "通知权限已开启" else "通知权限尚未开启，暂时无法显示提醒")
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(onClick = {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("开启通知") }
                }
                Text(if (exact) "精确提醒权限已开启" else "精确提醒权限尚未开启，系统可能延迟通知")
                if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Button(onClick = {
                        exactAlarmPermission.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    }) { Text("开启精确提醒") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("学期校准", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("第1周周一：${snapshot.semester.firstMonday}")
                if (snapshot.semester.name.contains("首周待确认")) {
                    Text("为避免错时提醒，校准前不会启用课程通知。", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onScheduleChanged(snapshot.withSemester()) }) {
                        Text("日期正确，启用提醒")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onScheduleChanged(snapshot.withSemester(firstMonday = snapshot.semester.firstMonday.minusWeeks(1)))
                    }) { Text("提前一周") }
                    OutlinedButton(onClick = {
                        onScheduleChanged(snapshot.withSemester(firstMonday = snapshot.semester.firstMonday.plusWeeks(1)))
                    }) { Text("推后一周") }
                }
                Text("教学周数：${snapshot.semester.totalWeeks}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = snapshot.semester.totalWeeks > 1,
                        onClick = { onScheduleChanged(snapshot.withSemester(totalWeeks = snapshot.semester.totalWeeks - 1)) },
                    ) { Text("减少一周") }
                    OutlinedButton(
                        enabled = snapshot.semester.totalWeeks < 30,
                        onClick = { onScheduleChanged(snapshot.withSemester(totalWeeks = snapshot.semester.totalWeeks + 1)) },
                    ) { Text("增加一周") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("隐私", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("课表仅保存在本机。登录由武大网页完成，应用禁用表单保存和自动填充，不保存学号、密码或验证码。")
                Text("离开导入页面后，网页登录 Cookie、缓存和历史记录会被清除。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ScheduleSnapshot.remindersCanRun(): Boolean =
    semester.name != "演示学期" && !semester.name.contains("首周待确认")

private fun ScheduleSnapshot.withSemester(
    firstMonday: LocalDate = semester.firstMonday,
    totalWeeks: Int = semester.totalWeeks,
): ScheduleSnapshot = copy(
    semester = semester.copy(
        name = semester.name.replace("（首周待确认）", ""),
        firstMonday = firstMonday,
        totalWeeks = totalWeeks,
    ),
)
