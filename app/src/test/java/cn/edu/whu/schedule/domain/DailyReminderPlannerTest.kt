package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailyReminderPlannerTest {
    private val snapshot = ScheduleSnapshot(
        semester = Semester(
            name = "测试学期",
            firstMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 2,
        ),
        courses = emptyList(),
        meetings = emptyList(),
    )

    @Test
    fun schedulesFirstMorningWhenEnabledBeforeSemester() {
        assertEquals(
            LocalDateTime.of(2026, 9, 7, 7, 30),
            DailyReminderPlanner.next(snapshot, LocalDateTime.of(2026, 9, 1, 12, 0)),
        )
    }

    @Test
    fun schedulesTodayBeforeSummaryTimeAndTomorrowAfterIt() {
        assertEquals(
            LocalDateTime.of(2026, 9, 9, 7, 30),
            DailyReminderPlanner.next(snapshot, LocalDateTime.of(2026, 9, 9, 7, 0)),
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 10, 7, 30),
            DailyReminderPlanner.next(snapshot, LocalDateTime.of(2026, 9, 9, 8, 0)),
        )
    }

    @Test
    fun stopsAfterLastSemesterMorning() {
        assertEquals(
            LocalDateTime.of(2026, 9, 20, 7, 30),
            DailyReminderPlanner.next(snapshot, LocalDateTime.of(2026, 9, 19, 12, 0)),
        )
        assertNull(DailyReminderPlanner.next(snapshot, LocalDateTime.of(2026, 9, 20, 8, 0)))
    }
}
