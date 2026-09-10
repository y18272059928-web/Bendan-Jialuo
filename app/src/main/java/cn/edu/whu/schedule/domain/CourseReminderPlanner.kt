package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.ScheduleSnapshot
import java.time.LocalDate

data class ReminderDateWindow(
    val startInclusive: LocalDate,
    val endInclusive: LocalDate,
)

/** Keeps the number of system alarms bounded while daily refreshes cover the whole semester. */
object CourseReminderPlanner {
    const val DEFAULT_LOOKAHEAD_DAYS = 42L

    fun window(
        snapshot: ScheduleSnapshot,
        today: LocalDate,
        lookaheadDays: Long = DEFAULT_LOOKAHEAD_DAYS,
    ): ReminderDateWindow? {
        require(lookaheadDays > 0)
        val semesterStart = snapshot.semester.firstMonday
        val semesterEnd = semesterStart
            .plusWeeks(snapshot.semester.totalWeeks.toLong())
            .minusDays(1)
        if (today.isAfter(semesterEnd)) return null

        val start = if (today.isBefore(semesterStart)) semesterStart else today
        val end = minOf(start.plusDays(lookaheadDays - 1), semesterEnd)
        return ReminderDateWindow(start, end)
    }
}
