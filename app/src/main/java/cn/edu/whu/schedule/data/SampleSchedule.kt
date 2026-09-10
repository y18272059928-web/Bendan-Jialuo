package cn.edu.whu.schedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

object SampleSchedule {
    fun create(today: LocalDate = LocalDate.now()): ScheduleSnapshot {
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val courses = listOf(
            Course(1, "研究生课程示例", "待导入", 0xFF7A1733),
            Course(2, "课表导入说明", "本地演示", 0xFF315B7D),
        )
        val meetings = listOf(
            CourseMeeting(1, 1, "信息门户导入后替换", 1, 1, 2, LocalTime.of(8, 0), LocalTime.of(9, 35), (1..18).toSet()),
            CourseMeeting(2, 2, "点击底部“导入”", 3, 3, 4, LocalTime.of(10, 5), LocalTime.of(11, 40), setOf(1, 3, 5, 7, 9, 11, 13, 15, 17)),
        )
        return ScheduleSnapshot(Semester(name = "演示学期", firstMonday = firstMonday, totalWeeks = 18), courses, meetings)
    }
}

