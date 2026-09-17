package cn.edu.whu.schedule.domain

import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.courseName
import java.time.LocalDateTime
import java.time.LocalTime

data class PlannedPersonalReminder(
    val key: String,
    val triggerAt: LocalDateTime,
    val kind: String,
    val title: String,
    val subtitle: String,
)

object PersonalReminderPlanner {
    fun plan(
        snapshot: ScheduleSnapshot,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<PlannedPersonalReminder> {
        val tasks = snapshot.tasks.asSequence()
            .filterNot { it.completed }
            .mapNotNull { task ->
                val trigger = task.dueAt.minusMinutes(task.reminderMinutes.toLong())
                if (!trigger.isAfter(now)) return@mapNotNull null
                PlannedPersonalReminder(
                    key = "task:${task.id}",
                    triggerAt = trigger,
                    kind = "待办提醒",
                    title = task.title,
                    subtitle = buildList {
                        snapshot.courseName(task.courseId)?.let(::add)
                        add("截止：${task.dueAt.toLocalDate()} ${task.dueAt.toLocalTime()}")
                        if (task.note.isNotBlank()) add(task.note)
                    }.joinToString(" · "),
                )
            }

        val exams = snapshot.exams.asSequence().mapNotNull { exam ->
            val startsAt = LocalDateTime.of(exam.date, exam.startTime ?: LocalTime.of(9, 0))
            if (!startsAt.isAfter(now)) return@mapNotNull null
            val dayBefore = startsAt.minusDays(1)
            val twoHoursBefore = startsAt.minusHours(2)
            val trigger = when {
                dayBefore.isAfter(now) -> dayBefore
                twoHoursBefore.isAfter(now) -> twoHoursBefore
                now.plusMinutes(1).isBefore(startsAt) -> now.plusMinutes(1)
                else -> return@mapNotNull null
            }
            PlannedPersonalReminder(
                key = "exam:${exam.id}",
                triggerAt = trigger,
                kind = "考试提醒",
                title = exam.title,
                subtitle = buildList {
                    snapshot.courseName(exam.courseId)?.let(::add)
                    add("${exam.date}${exam.startTime?.let { " $it" }.orEmpty()}")
                    if (exam.room.isNotBlank()) add(exam.room)
                    if (exam.seat.isNotBlank()) add("座位 ${exam.seat}")
                }.joinToString(" · "),
            )
        }

        return (tasks + exams).sortedBy(PlannedPersonalReminder::triggerAt).toList()
    }
}