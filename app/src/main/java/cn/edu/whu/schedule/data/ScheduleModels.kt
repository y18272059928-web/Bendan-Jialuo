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
    val note: String = "",
    val marker: CourseMarker = CourseMarker.NORMAL,
)

enum class CourseMarker(val label: String) {
    NORMAL("正常"),
    EXAM_WEEK("考试周"),
    FINISHED("已结课");

    companion object {
        fun fromStorage(value: String?): CourseMarker =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

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

data class Exam(
    val id: Long,
    val courseId: Long? = null,
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val room: String = "",
    val seat: String = "",
    val note: String = "",
)

data class StudyTask(
    val id: Long,
    val courseId: Long? = null,
    val title: String,
    val dueAt: LocalDateTime,
    val note: String = "",
    val completed: Boolean = false,
    val reminderMinutes: Int = 60,
)

data class NoClassDate(
    val id: Long,
    val date: LocalDate,
    val name: String,
)

data class ScheduleSnapshot(
    val semester: Semester,
    val courses: List<Course>,
    val meetings: List<CourseMeeting>,
    val exams: List<Exam> = emptyList(),
    val tasks: List<StudyTask> = emptyList(),
    val noClassDates: List<NoClassDate> = emptyList(),
)

fun ScheduleSnapshot.courseName(courseId: Long?): String? =
    courseId?.let { id -> courses.firstOrNull { it.id == id }?.name }

fun ScheduleSnapshot.nextPersonalItemId(): Long = sequenceOf(
    exams.asSequence().map(Exam::id),
    tasks.asSequence().map(StudyTask::id),
    noClassDates.asSequence().map(NoClassDate::id),
).flatten().maxOrNull()?.plus(1) ?: 1L

data class CourseOccurrence(
    val course: Course,
    val meeting: CourseMeeting,
    val week: Int,
    val date: LocalDate,
) {
    val startsAt: LocalDateTime = date.atTime(meeting.startTime)
    val endsAt: LocalDateTime = date.atTime(meeting.endTime)
}

