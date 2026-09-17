package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.data.ScheduleSnapshot
import java.time.LocalDateTime

data class CalendarEventSpec(
    val title: String,
    val location: String,
    val description: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
)

object CalendarEventPlanner {
    fun events(snapshot: ScheduleSnapshot): List<CalendarEventSpec> {
        val end = snapshot.semester.firstMonday
            .plusWeeks(snapshot.semester.totalWeeks.toLong())
            .minusDays(1)
        return OccurrenceEngine.between(snapshot, snapshot.semester.firstMonday, end)
            .asSequence()
            .filter { it.course.marker != CourseMarker.FINISHED }
            .map { occurrence ->
                val course = occurrence.course
                val marker = if (course.marker == CourseMarker.EXAM_WEEK) "【考试周】" else ""
                val details = buildList {
                    if (course.teacher.isNotBlank()) add("教师：${course.teacher}")
                    add("教学第 ${occurrence.week} 周 · 第${occurrence.meeting.startPeriod}–${occurrence.meeting.endPeriod}节")
                    if (course.note.isNotBlank()) add("备注：${course.note}")
                    add("由笨蛋珞珈同步")
                }.joinToString("\n")
                CalendarEventSpec(
                    title = marker + course.name,
                    location = occurrence.meeting.room,
                    description = details,
                    startsAt = occurrence.startsAt,
                    endsAt = occurrence.endsAt,
                )
            }
            .toList()
    }
}
