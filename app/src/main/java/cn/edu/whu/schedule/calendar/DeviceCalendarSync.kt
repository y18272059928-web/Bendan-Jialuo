package cn.edu.whu.schedule.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.domain.CalendarEventPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class CalendarSyncResult(
    val success: Boolean,
    val eventCount: Int = 0,
    val message: String,
)

object DeviceCalendarSync {
    private const val ACCOUNT_NAME = "笨蛋珞珈"
    private const val CALENDAR_NAME = "bendan_jialuo_courses"
    private const val CALENDAR_DISPLAY_NAME = "笨蛋珞珈课程"
    private const val PREFS = "device_calendar_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_COUNT = "last_count"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun lastSyncedCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LAST_COUNT, 0)

    fun syncIfEnabled(context: Context, snapshot: ScheduleSnapshot) {
        if (!isEnabled(context) || !hasPermissions(context)) return
        applicationScope.launch { sync(context.applicationContext, snapshot) }
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(
        context: Context,
        snapshot: ScheduleSnapshot,
        reminderMinutes: Int = 15,
    ): CalendarSyncResult = withContext(Dispatchers.IO) {
        if (!hasPermissions(context)) {
            return@withContext CalendarSyncResult(false, message = "请先允许读取和写入日历。")
        }
        runCatching {
            val resolver = context.contentResolver
            val calendarId = findCalendar(context) ?: createCalendar(context)
            resolver.delete(
                Events.CONTENT_URI,
                "${Events.CALENDAR_ID}=?",
                arrayOf(calendarId.toString()),
            )

            val zone = ZoneId.systemDefault()
            val events = CalendarEventPlanner.events(snapshot)
            var inserted = 0
            events.forEach { event ->
                val values = ContentValues().apply {
                    put(Events.CALENDAR_ID, calendarId)
                    put(Events.TITLE, event.title)
                    put(Events.EVENT_LOCATION, event.location)
                    put(Events.DESCRIPTION, event.description)
                    put(Events.DTSTART, event.startsAt.atZone(zone).toInstant().toEpochMilli())
                    put(Events.DTEND, event.endsAt.atZone(zone).toInstant().toEpochMilli())
                    put(Events.EVENT_TIMEZONE, zone.id)
                    put(Events.HAS_ALARM, 1)
                    put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
                }
                val eventUri = resolver.insert(Events.CONTENT_URI, values)
                    ?: error("系统日历拒绝写入课程")
                val eventId = ContentUris.parseId(eventUri)
                resolver.insert(
                    Reminders.CONTENT_URI,
                    ContentValues().apply {
                        put(Reminders.EVENT_ID, eventId)
                        put(Reminders.MINUTES, reminderMinutes)
                        put(Reminders.METHOD, Reminders.METHOD_ALERT)
                    },
                ) ?: error("系统日历拒绝写入提醒")
                inserted += 1
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_ENABLED, true)
                putInt(KEY_LAST_COUNT, inserted)
            }
            CalendarSyncResult(
                success = true,
                eventCount = inserted,
                message = "已同步 $inserted 节课程到手机日历，每节提前 $reminderMinutes 分钟提醒。",
            )
        }.getOrElse { error ->
            CalendarSyncResult(
                success = false,
                message = "同步失败：${error.message ?: "手机日历服务不可用"}",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun findCalendar(context: Context): Long? {
        val projection = arrayOf(Calendars._ID)
        return context.contentResolver.query(
            Calendars.CONTENT_URI,
            projection,
            "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.ACCOUNT_NAME}=? AND ${Calendars.NAME}=?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT_NAME, CALENDAR_NAME),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    @SuppressLint("MissingPermission")
    private fun createCalendar(context: Context): Long {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(Calendars.CALENDAR_COLOR, 0xFFE7CDA9.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
        }
        val created = context.contentResolver.insert(uri, values)
            ?: error("无法创建“笨蛋珞珈课程”日历")
        return ContentUris.parseId(created)
    }
}
