package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CalendarEventPlannerTest {
    @Test
    fun createsSemesterEventsAndSkipsFinishedCourses() {
        val snapshot = ScheduleSnapshot(
            semester = Semester(name = "测试学期", firstMonday = LocalDate.of(2026, 9, 7), totalWeeks = 2),
            courses = listOf(
                Course(1, "正常课程", "老师甲", 0xFF000000, note = "带教材"),
                Course(2, "考试课程", "老师乙", 0xFF000000, marker = CourseMarker.EXAM_WEEK),
                Course(3, "已结课", "老师丙", 0xFF000000, marker = CourseMarker.FINISHED),
            ),
            meetings = listOf(
                meeting(1, 1),
                meeting(2, 2),
                meeting(3, 3),
            ),
        )

        val events = CalendarEventPlanner.events(snapshot)

        assertEquals(2, events.size)
        assertTrue(events.any { it.title == "正常课程" && "备注：带教材" in it.description })
        assertTrue(events.any { it.title == "【考试周】考试课程" })
        assertFalse(events.any { "已结课" in it.title })
    }

    private fun meeting(id: Long, courseId: Long) = CourseMeeting(
        id = id,
        courseId = courseId,
        room = "教室",
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 35),
        weeks = setOf(1),
    )
}
