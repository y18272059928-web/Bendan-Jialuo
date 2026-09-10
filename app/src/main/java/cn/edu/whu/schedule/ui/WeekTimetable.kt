package cn.edu.whu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.data.CourseOccurrence
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.domain.OccurrenceEngine
import cn.edu.whu.schedule.domain.WhuPeriodTimes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val TimeColumnWidth = 50.dp
private val DayColumnWidth = 94.dp
private val PeriodRowHeight = 66.dp
private val TimetableWidth = TimeColumnWidth + DayColumnWidth * 7
private val TimetableHeight = PeriodRowHeight * 13

@Composable
fun ClassicWeekScreen(
    snapshot: ScheduleSnapshot,
    onAdd: () -> Unit,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val today = LocalDate.now()
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var weekOffset by remember { mutableIntStateOf(0) }
    val monday = currentMonday.plusWeeks(weekOffset.toLong())
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val occurrences = remember(snapshot, monday) {
        (0..6).flatMap { day ->
            OccurrenceEngine.onDate(snapshot, monday.plusDays(day.toLong()))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    OccurrenceEngine.teachingWeek(snapshot, monday)?.let { "教学第 $it 周" } ?: "非教学周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${monday.monthValue}月${monday.dayOfMonth}日—${monday.plusDays(6).monthValue}月${monday.plusDays(6).dayOfMonth}日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加课程")
            }
            if (weekOffset != 0) {
                OutlinedButton(onClick = { weekOffset = 0 }) { Text("回到本周") }
            }
        }
        Row(
            Modifier.horizontalScroll(horizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(TimeColumnWidth).height(46.dp), contentAlignment = Alignment.Center) {
                Text("节次", style = MaterialTheme.typography.labelSmall)
            }
            (0..6).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                val isToday = date == today
                Column(
                    Modifier.width(DayColumnWidth).height(46.dp)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("周${"一二三四五六日"[offset]}", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    Text("${date.monthValue}/${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().verticalScroll(vertical),
        ) {
            Row(Modifier.height(TimetableHeight).horizontalScroll(horizontal)) {
                TimetableGrid(occurrences = occurrences, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    occurrences: List<CourseOccurrence>,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(TimetableWidth, TimetableHeight)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val timeWidth = TimeColumnWidth.toPx()
                val dayWidth = DayColumnWidth.toPx()
                val rowHeight = PeriodRowHeight.toPx()
                for (period in 0..13) {
                    val y = period * rowHeight
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawLine(gridColor, Offset(timeWidth, 0f), Offset(timeWidth, size.height), 1f)
                for (day in 1..7) {
                    val x = timeWidth + day * dayWidth
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                }
            },
    ) {
        (1..13).forEach { period ->
            Column(
                Modifier.offset(y = PeriodRowHeight * (period - 1))
                    .width(TimeColumnWidth)
                    .height(PeriodRowHeight)
                    .padding(top = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(period.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    WhuPeriodTimes.start(period).toString(),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        occurrences.forEach { occurrence ->
            CourseBlock(occurrence = occurrence, onEdit = onEdit)
        }
    }
}

@Composable
private fun CourseBlock(
    occurrence: CourseOccurrence,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val meeting = occurrence.meeting
    val span = (meeting.endPeriod - meeting.startPeriod + 1).coerceAtLeast(1)
    val status = when (occurrence.course.marker) {
        CourseMarker.NORMAL -> null
        CourseMarker.EXAM_WEEK -> "考试周"
        CourseMarker.FINISHED -> "已结课"
    }
    Card(
        modifier = Modifier
            .offset(
                x = TimeColumnWidth + DayColumnWidth * (meeting.dayOfWeek - 1),
                y = PeriodRowHeight * (meeting.startPeriod - 1),
            )
            .width(DayColumnWidth - 3.dp)
            .height(PeriodRowHeight * span - 3.dp)
            .padding(2.dp)
            .alpha(if (occurrence.course.marker == CourseMarker.FINISHED) 0.58f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onEdit(CourseEditTarget(occurrence.course, meeting)) },
        colors = CardDefaults.cardColors(
            containerColor = Color(occurrence.course.colorArgb).copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (status != null) {
                Text(
                    status,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        RoundedCornerShape(5.dp),
                    ).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Text(
                occurrence.course.name,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (span >= 2) 3 else 2,
            )
            if (span >= 2 && meeting.room.isNotBlank()) {
                Text(meeting.room, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 2)
            }
            if (span >= 3 && occurrence.course.note.isNotBlank()) {
                Text("备：${occurrence.course.note}", fontSize = 9.sp, lineHeight = 10.sp, maxLines = 2)
            }
        }
    }
}
