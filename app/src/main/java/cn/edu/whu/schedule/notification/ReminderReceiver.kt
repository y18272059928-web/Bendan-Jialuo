package cn.edu.whu.schedule.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.whu.schedule.MainActivity
import cn.edu.whu.schedule.R
import cn.edu.whu.schedule.ScheduleApplication
import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.domain.OccurrenceEngine
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notificationAllowed) {
            if (intent.action == ReminderScheduler.ACTION_DAILY) {
                val snapshot = (context.applicationContext as ScheduleApplication).database.read()
                ReminderScheduler.scheduleNextDailySummary(context, snapshot)
            }
            return
        }
        when (intent.action) {
            ReminderScheduler.ACTION_COURSE -> showCourse(context, intent)
            ReminderScheduler.ACTION_DAILY -> showDaily(context)
        }
    }

    private fun showCourse(context: Context, intent: Intent) {
        val name = intent.getStringExtra("course_name").orEmpty()
        val room = intent.getStringExtra("room").orEmpty()
        val start = intent.getStringExtra("start_time").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        notify(
            context,
            ReminderScheduler.notificationId("$name:$date:$start"),
            ReminderScheduler.COURSE_CHANNEL,
            "即将上课：$name",
            "$start · $room",
        )
    }

    private fun showDaily(context: Context) {
        val database = (context.applicationContext as ScheduleApplication).database
        val snapshot = database.read()
        val courses = OccurrenceEngine.onDate(snapshot, LocalDate.now())
            .filter { it.course.marker != CourseMarker.FINISHED }
        val text = if (courses.isEmpty()) {
            "今天没有课程"
        } else {
            courses.joinToString("；") { "${it.meeting.startTime} ${it.course.name}" }
        }
        notify(context, DAILY_ID, ReminderScheduler.DAILY_CHANNEL, "今日课表", text)
        // Refresh both the daily summary and the rolling course-alarm window.
        ReminderScheduler.rescheduleAll(context, snapshot)
    }

    private fun notify(context: Context, id: Int, channel: String, title: String, text: String) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    companion object {
        private const val DAILY_ID = 731
    }
}
