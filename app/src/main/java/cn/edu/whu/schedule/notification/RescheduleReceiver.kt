package cn.edu.whu.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.whu.schedule.ScheduleApplication

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
        if (intent.action !in allowedActions) return
        val application = context.applicationContext as ScheduleApplication
        val snapshot = application.database.read()
        if (snapshot.semester.name != "演示学期" && !snapshot.semester.name.contains("首周待确认")) {
            ReminderScheduler.rescheduleAll(context, snapshot)
        }
    }
}
