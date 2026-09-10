package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.ScheduleSnapshot
import java.time.LocalTime

object WhuPeriodTimes {
    private val periods = listOf(
        LocalTime.of(8, 0) to LocalTime.of(8, 45),
        LocalTime.of(8, 50) to LocalTime.of(9, 35),
        LocalTime.of(9, 50) to LocalTime.of(10, 35),
        LocalTime.of(10, 40) to LocalTime.of(11, 25),
        LocalTime.of(11, 30) to LocalTime.of(12, 15),
        LocalTime.of(14, 5) to LocalTime.of(14, 50),
        LocalTime.of(14, 55) to LocalTime.of(15, 40),
        LocalTime.of(15, 45) to LocalTime.of(16, 30),
        LocalTime.of(16, 40) to LocalTime.of(17, 25),
        LocalTime.of(17, 30) to LocalTime.of(18, 15),
        LocalTime.of(18, 30) to LocalTime.of(19, 15),
        LocalTime.of(19, 20) to LocalTime.of(20, 5),
        LocalTime.of(20, 10) to LocalTime.of(20, 55),
    )

    fun start(period: Int): LocalTime = periods[period.coerceIn(1, periods.size) - 1].first
    fun end(period: Int): LocalTime = periods[period.coerceIn(1, periods.size) - 1].second
}

object ScheduleEditor {
    val palette = listOf(
        0xFFD99175, 0xFF7FA99B, 0xFFB58FBC, 0xFFE1B96C,
        0xFF7F9FC4, 0xFFA7A56A, 0xFFC7838B, 0xFF78A9A5,
    )

    fun newCourseId(snapshot: ScheduleSnapshot): Long =
        (snapshot.courses.maxOfOrNull(Course::id) ?: 0L) + 1L

    fun newMeetingId(snapshot: ScheduleSnapshot): Long =
        (snapshot.meetings.maxOfOrNull(CourseMeeting::id) ?: 0L) + 1L

    fun upsert(snapshot: ScheduleSnapshot, course: Course, meeting: CourseMeeting): ScheduleSnapshot {
        val courses = snapshot.courses.filterNot { it.id == course.id } + course
        val meetings = snapshot.meetings.filterNot { it.id == meeting.id } + meeting
        return snapshot.copy(
            courses = courses.sortedBy(Course::id),
            meetings = meetings.sortedWith(compareBy(CourseMeeting::dayOfWeek, CourseMeeting::startPeriod)),
        )
    }

    fun deleteMeeting(snapshot: ScheduleSnapshot, meetingId: Long): ScheduleSnapshot {
        val deleted = snapshot.meetings.firstOrNull { it.id == meetingId } ?: return snapshot
        val meetings = snapshot.meetings.filterNot { it.id == meetingId }
        val courses = if (meetings.none { it.courseId == deleted.courseId }) {
            snapshot.courses.filterNot { it.id == deleted.courseId }
        } else {
            snapshot.courses
        }
        return snapshot.copy(courses = courses, meetings = meetings)
    }

    fun preserveUserMetadata(imported: ScheduleSnapshot, previous: ScheduleSnapshot): ScheduleSnapshot {
        val metadata = previous.courses.associateBy { it.name.trim().lowercase() }
        return imported.copy(courses = imported.courses.map { course ->
            metadata[course.name.trim().lowercase()]?.let {
                course.copy(note = it.note, marker = it.marker)
            } ?: course
        })
    }
}
