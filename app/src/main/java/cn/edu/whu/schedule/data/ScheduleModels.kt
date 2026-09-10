package cn.edu.whu.schedule.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Semester(
    val id: Long = 1,
    val name: String,
    val firstMonday: LocalDate,
    val totalWeeks: Int,
)

data class Course(
    val id: Long,
    val name: String,
    val teacher: String,
    val colorArgb: Long,
)

data class CourseMeeting(
    val id: Long,
    val courseId: Long,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val weeks: Set<Int>,
)

data class ScheduleSnapshot(
    val semester: Semester,
    val courses: List<Course>,
    val meetings: List<CourseMeeting>,
)

data class CourseOccurrence(
    val course: Course,
    val meeting: CourseMeeting,
    val week: Int,
    val date: LocalDate,
) {
    val startsAt: LocalDateTime = date.atTime(meeting.startTime)
    val endsAt: LocalDateTime = date.atTime(meeting.endTime)
}

