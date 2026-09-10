package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OccurrenceEngineTest {
    private val snapshot = ScheduleSnapshot(
        semester = Semester(name = "测试学期", firstMonday = LocalDate.of(2026, 9, 7), totalWeeks = 18),
        courses = listOf(Course(1, "测试课程", "老师", 0xFF000000)),
        meetings = listOf(
            CourseMeeting(1, 1, "教室", 2, 1, 2, LocalTime.of(8, 0), LocalTime.of(9, 35), setOf(1, 3)),
        ),
    )

    @Test
    fun calculatesTeachingWeekFromFirstMonday() {
        assertEquals(1, OccurrenceEngine.teachingWeek(snapshot, LocalDate.of(2026, 9, 7)))
        assertEquals(2, OccurrenceEngine.teachingWeek(snapshot, LocalDate.of(2026, 9, 20)))
        assertNull(OccurrenceEngine.teachingWeek(snapshot, LocalDate.of(2026, 9, 6)))
    }

    @Test
    fun respectsDayAndWeekSet() {
        assertEquals(1, OccurrenceEngine.onDate(snapshot, LocalDate.of(2026, 9, 8)).size)
        assertEquals(0, OccurrenceEngine.onDate(snapshot, LocalDate.of(2026, 9, 15)).size)
        assertEquals(1, OccurrenceEngine.onDate(snapshot, LocalDate.of(2026, 9, 22)).size)
    }

    @Test
    fun excludesDatesAfterSemesterEnd() {
        assertEquals(18, OccurrenceEngine.teachingWeek(snapshot, LocalDate.of(2027, 1, 10)))
        assertNull(OccurrenceEngine.teachingWeek(snapshot, LocalDate.of(2027, 1, 11)))
        assertEquals(0, OccurrenceEngine.onDate(snapshot, LocalDate.of(2027, 1, 12)).size)
    }

    @Test
    fun returnsOccurrencesInStartTimeOrder() {
        val laterMeeting = snapshot.meetings.single().copy(
            id = 2,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 35),
        )
        val unordered = snapshot.copy(meetings = listOf(laterMeeting, snapshot.meetings.single()))
        val result = OccurrenceEngine.onDate(unordered, LocalDate.of(2026, 9, 8))
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(14, 0)), result.map { it.meeting.startTime })
    }
}
