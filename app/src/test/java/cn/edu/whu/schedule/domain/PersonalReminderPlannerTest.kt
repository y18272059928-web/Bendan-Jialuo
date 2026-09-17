package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.Exam
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import cn.edu.whu.schedule.data.StudyTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PersonalReminderPlannerTest {
    private val now = LocalDateTime.of(2026, 9, 18, 10, 0)
    private val base = ScheduleSnapshot(
        semester = Semester(name = "测试", firstMonday = LocalDate.of(2026, 9, 7), totalWeeks = 18),
        courses = listOf(Course(1, "网络安全", "老师", 0xFF000000)),
        meetings = emptyList(),
    )

    @Test
    fun schedulesOpenTaskAtSelectedLeadTime() {
        val snapshot = base.copy(
            tasks = listOf(
                StudyTask(
                    id = 1,
                    courseId = 1,
                    title = "实验报告",
                    dueAt = LocalDateTime.of(2026, 9, 18, 20, 0),
                    reminderMinutes = 60,
                ),
            ),
        )

        val reminder = PersonalReminderPlanner.plan(snapshot, now).single()

        assertEquals(LocalDateTime.of(2026, 9, 18, 19, 0), reminder.triggerAt)
        assertTrue(reminder.subtitle.contains("网络安全"))
    }

    @Test
    fun schedulesExamOneDayBeforeAndFallsBackWhenAddedLate() {
        val regular = base.copy(
            exams = listOf(
                Exam(1, 1, "期末考试", LocalDate.of(2026, 9, 20), LocalTime.of(9, 0)),
            ),
        )
        val late = base.copy(
            exams = listOf(
                Exam(2, 1, "临时测验", LocalDate.of(2026, 9, 18), LocalTime.of(11, 0)),
            ),
        )

        assertEquals(
            LocalDateTime.of(2026, 9, 19, 9, 0),
            PersonalReminderPlanner.plan(regular, now).single().triggerAt,
        )
        assertEquals(now.plusMinutes(1), PersonalReminderPlanner.plan(late, now).single().triggerAt)
    }

    @Test
    fun excludesCompletedAndExpiredItems() {
        val snapshot = base.copy(
            tasks = listOf(
                StudyTask(1, title = "已完成", dueAt = now.plusDays(1), completed = true),
                StudyTask(2, title = "已过期", dueAt = now.minusHours(1)),
            ),
            exams = listOf(
                Exam(3, title = "已结束", date = now.toLocalDate(), startTime = now.minusHours(1).toLocalTime()),
            ),
        )

        assertTrue(PersonalReminderPlanner.plan(snapshot, now).isEmpty())
    }
}