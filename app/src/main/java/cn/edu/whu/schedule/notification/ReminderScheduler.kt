package cn.edu.whu.schedule.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import cn.edu.whu.schedule.data.CourseOccurrence
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.domain.CourseReminderPlanner
import cn.edu.whu.schedule.domain.DailyReminderPlanner
import cn.edu.whu.schedule.domain.OccurrenceEngine
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    const val COURSE_CHANNEL = "course_reminders"
    const val DAILY_CHANNEL = "daily_summary"
    const val ACTION_COURSE = "cn.edu.whu.schedule.COURSE_REMINDER"
    const val ACTION_DAILY = "cn.edu.whu.schedule.DAILY_SUMMARY"

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(COURSE_CHANNEL, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "在每节课开始前提醒"
                },
                NotificationChannel(DAILY_CHANNEL, "每日课表", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "每天早晨汇总当天课程"
                },
            ),
        )
    }

    fun rescheduleAll(
        context: Context,
        snapshot: ScheduleSnapshot,
        reminderMinutes: Long = 15,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        cancelScheduledCourses(context)
        val requestCodes = mutableSetOf<Int>()
        CourseReminderPlanner.window(snapshot, now.toLocalDate())?.let { window ->
            OccurrenceEngine.between(snapshot, window.startInclusive, window.endInclusive)
                .asSequence()
                .filter { it.startsAt.minusMinutes(reminderMinutes).isAfter(now) }
                .forEach { requestCodes += scheduleCourse(context, it, reminderMinutes) }
        }
        reminderPreferences(context).edit {
            putString(KEY_COURSE_REQUEST_CODES, requestCodes.joinToString(","))
        }
        scheduleNextDailySummary(context, snapshot)
    }

    fun pauseAll(context: Context) {
        cancelScheduledCourses(context)
        cancelDailySummary(context)
        reminderPreferences(context).edit { remove(KEY_COURSE_REQUEST_CODES) }
    }

    private fun scheduleCourse(context: Context, occurrence: CourseOccurrence, reminderMinutes: Long): Int {
        val trigger = occurrence.startsAt.minusMinutes(reminderMinutes)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_COURSE
            putExtra("course_name", occurrence.course.name)
            putExtra("room", occurrence.meeting.room)
            putExtra("start_time", occurrence.meeting.startTime.toString())
            putExtra("date", occurrence.date.toString())
        }
        val requestCode = ("${occurrence.meeting.id}:${occurrence.date}".hashCode() and Int.MAX_VALUE)
        schedule(context, trigger, PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        return requestCode
    }

    private fun cancelScheduledCourses(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val codes = reminderPreferences(context).getString(KEY_COURSE_REQUEST_CODES, null)
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty()
        codes.forEach { requestCode ->
            val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_COURSE }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    fun scheduleNextDailySummary(
        context: Context,
        snapshot: ScheduleSnapshot,
        time: LocalTime = LocalTime.of(7, 30),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val next = DailyReminderPlanner.next(snapshot, now, time) ?: run {
            cancelDailySummary(context)
            return
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_DAILY }
        schedule(context, next, PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
    }

    private fun cancelDailySummary(context: Context) {
        val dailyIntent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_DAILY }
        val daily = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            dailyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (daily != null) {
            context.getSystemService(AlarmManager::class.java).cancel(daily)
            daily.cancel()
        }
    }

    private fun schedule(context: Context, dateTime: LocalDateTime, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    fun notificationId(occurrenceKey: String): Int = occurrenceKey.hashCode() and Int.MAX_VALUE

    private fun reminderPreferences(context: Context) =
        context.getSharedPreferences("scheduled_reminders", Context.MODE_PRIVATE)

    private const val DAILY_REQUEST_CODE = 730
    private const val KEY_COURSE_REQUEST_CODES = "course_request_codes"
}
