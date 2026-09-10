package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.CourseOccurrence
import cn.edu.whu.schedule.data.ScheduleSnapshot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object OccurrenceEngine {
    fun teachingWeek(snapshot: ScheduleSnapshot, date: LocalDate): Int? {
        val days = ChronoUnit.DAYS.between(snapshot.semester.firstMonday, date)
        if (days < 0) return null
        val week = (days / 7).toInt() + 1
        return week.takeIf { it <= snapshot.semester.totalWeeks }
    }

    fun onDate(snapshot: ScheduleSnapshot, date: LocalDate): List<CourseOccurrence> {
        val week = teachingWeek(snapshot, date) ?: return emptyList()
        val courses = snapshot.courses.associateBy { it.id }
        return snapshot.meetings.asSequence()
            .filter { it.dayOfWeek == date.dayOfWeek.value && week in it.weeks }
            .mapNotNull { meeting ->
                courses[meeting.courseId]?.let { course ->
                    CourseOccurrence(course, meeting, week, date)
                }
            }
            .sortedBy { it.meeting.startTime }
            .toList()
    }

    fun between(
        snapshot: ScheduleSnapshot,
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): List<CourseOccurrence> = buildList {
        var date = startInclusive
        while (!date.isAfter(endInclusive)) {
            addAll(onDate(snapshot, date))
            date = date.plusDays(1)
        }
    }
}

