package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CourseReminderPlannerTest {
    private val snapshot = ScheduleSnapshot(
        semester = Semester(
            name = "测试学期",
            firstMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
        ),
        courses = emptyList(),
        meetings = emptyList(),
    )

    @Test
    fun beginsAtSemesterStartWhenScheduledEarly() {
        assertEquals(
            ReminderDateWindow(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 18)),
            CourseReminderPlanner.window(snapshot, LocalDate.of(2026, 8, 1)),
        )
    }

    @Test
    fun rollsForwardAndClampsToSemesterEnd() {
        assertEquals(
            ReminderDateWindow(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10)),
            CourseReminderPlanner.window(snapshot, LocalDate.of(2026, 12, 20)),
        )
    }

    @Test
    fun stopsAfterSemester() {
        assertNull(CourseReminderPlanner.window(snapshot, LocalDate.of(2027, 1, 11)))
    }
}
