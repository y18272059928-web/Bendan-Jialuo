package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleEditorTest {
    private val base = ScheduleSnapshot(
        semester = Semester(
            name = "测试学期",
            firstMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
        ),
        courses = listOf(Course(1, "网络安全", "余老师", 0xFFD99175)),
        meetings = listOf(meeting(id = 1, courseId = 1, day = 2)),
    )

    @Test
    fun upsertAddsCourseAndMeeting() {
        val course = Course(
            id = ScheduleEditor.newCourseId(base),
            name = "数据科学",
            teacher = "李老师",
            colorArgb = ScheduleEditor.palette[1],
            note = "带电脑",
            marker = CourseMarker.EXAM_WEEK,
        )
        val meeting = meeting(
            id = ScheduleEditor.newMeetingId(base),
            courseId = course.id,
            day = 4,
        )

        val result = ScheduleEditor.upsert(base, course, meeting)

        assertEquals(2, result.courses.size)
        assertEquals(2, result.meetings.size)
        assertEquals("带电脑", result.courses.last().note)
        assertEquals(CourseMarker.EXAM_WEEK, result.courses.last().marker)
    }

    @Test
    fun deletingLastMeetingRemovesOrphanCourse() {
        val result = ScheduleEditor.deleteMeeting(base, 1)

        assertTrue(result.meetings.isEmpty())
        assertTrue(result.courses.isEmpty())
    }

    @Test
    fun deletingOneMeetingKeepsSharedCourse() {
        val snapshot = base.copy(meetings = base.meetings + meeting(id = 2, courseId = 1, day = 4))

        val result = ScheduleEditor.deleteMeeting(snapshot, 1)

        assertEquals(1, result.meetings.size)
        assertEquals(1, result.courses.size)
        assertFalse(result.meetings.any { it.id == 1L })
    }

    @Test
    fun reimportPreservesUserNoteAndMarkerByCourseName() {
        val previous = base.copy(
            courses = listOf(base.courses.single().copy(note = "考试地点另行通知", marker = CourseMarker.EXAM_WEEK)),
        )
        val imported = base.copy(
            courses = listOf(base.courses.single().copy(id = 88, teacher = "新教师")),
            meetings = listOf(meeting(id = 99, courseId = 88, day = 3)),
        )

        val result = ScheduleEditor.preserveUserMetadata(imported, previous)

        assertEquals("考试地点另行通知", result.courses.single().note)
        assertEquals(CourseMarker.EXAM_WEEK, result.courses.single().marker)
        assertEquals("新教师", result.courses.single().teacher)
    }

    @Test
    fun periodTimesMatchWhuSchedule() {
        assertEquals("08:00", WhuPeriodTimes.start(1).toString())
        assertEquals("20:55", WhuPeriodTimes.end(13).toString())
    }

    private fun meeting(id: Long, courseId: Long, day: Int) = CourseMeeting(
        id = id,
        courseId = courseId,
        room = "教室",
        dayOfWeek = day,
        startPeriod = 1,
        endPeriod = 2,
        startTime = WhuPeriodTimes.start(1),
        endTime = WhuPeriodTimes.end(2),
        weeks = (1..18).toSet(),
    )
}
