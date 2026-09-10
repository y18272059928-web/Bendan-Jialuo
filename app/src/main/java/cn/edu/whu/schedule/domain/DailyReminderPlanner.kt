package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.ScheduleSnapshot
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure date calculation kept separate from Android alarms for deterministic tests. */
object DailyReminderPlanner {
    fun next(
        snapshot: ScheduleSnapshot,
        now: LocalDateTime,
        time: LocalTime = LocalTime.of(7, 30),
    ): LocalDateTime? {
        val semesterStart = snapshot.semester.firstMonday
        val semesterEnd = semesterStart
            .plusWeeks(snapshot.semester.totalWeeks.toLong())
            .minusDays(1)
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        if (next.toLocalDate().isBefore(semesterStart)) next = semesterStart.atTime(time)
        return next.takeUnless { it.toLocalDate().isAfter(semesterEnd) }
    }
}
