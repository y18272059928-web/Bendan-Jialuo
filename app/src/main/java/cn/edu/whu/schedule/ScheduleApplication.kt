package cn.edu.whu.schedule

import android.app.Application
import cn.edu.whu.schedule.data.SampleSchedule
import cn.edu.whu.schedule.data.ScheduleDatabase
import cn.edu.whu.schedule.notification.ReminderScheduler

class ScheduleApplication : Application() {
    lateinit var database: ScheduleDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = ScheduleDatabase(this)
        if (!database.hasSchedule()) database.replace(SampleSchedule.create())
        ReminderScheduler.createNotificationChannels(this)
        val snapshot = database.read()
        if (snapshot.semester.name != "演示学期" && !snapshot.semester.name.contains("首周待确认")) {
            ReminderScheduler.rescheduleAll(this, snapshot)
        }
    }
}
