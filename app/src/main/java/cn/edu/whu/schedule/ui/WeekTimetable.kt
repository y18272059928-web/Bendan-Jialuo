package cn.edu.whu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.launch

private const val PERIOD_COUNT = 13
private const val WEEK_PAGE_COUNT = 10_001
private const val WEEK_PAGE_CENTER = WEEK_PAGE_COUNT / 2
private val HeaderHeight = 40.dp
private val TimeColumnWidth = 34.dp

@Composable
fun ClassicWeekScreen(
    snapshot: ScheduleSnapshot,
    onAdd: () -> Unit,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val today = LocalDate.now()
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val pagerState = rememberPagerState(
        initialPage = WEEK_PAGE_CENTER,
        pageCount = { WEEK_PAGE_COUNT },
    )
    val scope = rememberCoroutineScope()
    val weekOffset = pagerState.currentPage - WEEK_PAGE_CENTER
    val monday = currentMonday.plusWeeks(weekOffset.toLong())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                scope.launch {
                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                }
            }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
            }
            Column(
                Modifier.weight(1f).clickable(enabled = weekOffset != 0) {
                    scope.launch { pagerState.animateScrollToPage(WEEK_PAGE_CENTER) }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    OccurrenceEngine.teachingWeek(snapshot, monday)?.let { "教学第 $it 周" } ?: "非教学周",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${monday.monthValue}/${monday.dayOfMonth}—${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}" +
                        if (weekOffset != 0) " · 点此回本周" else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
            IconButton(onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        (pagerState.currentPage + 1).coerceAtMost(WEEK_PAGE_COUNT - 1),
                    )
                }
            }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 9.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.width(16.dp))
                Text("添加", fontSize = 11.sp)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val pageMonday = currentMonday.plusWeeks((page - WEEK_PAGE_CENTER).toLong())
            val occurrences = remember(snapshot, pageMonday) {
                (0..6).flatMap { day ->
                    OccurrenceEngine.onDate(snapshot, pageMonday.plusDays(day.toLong()))
                }
            }
            WeekPage(
                monday = pageMonday,
                today = today,
                occurrences = occurrences,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun WeekPage(
    monday: LocalDate,
    today: LocalDate,
    occurrences: List<CourseOccurrence>,
    onEdit: (CourseEditTarget) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayColumnWidth = (maxWidth - TimeColumnWidth) / 7
        val gridHeight = maxHeight - HeaderHeight
        val periodRowHeight = gridHeight / PERIOD_COUNT

        Column(Modifier.fillMaxSize()) {
            WeekHeader(
                monday = monday,
                today = today,
                dayColumnWidth = dayColumnWidth,
            )
            TimetableGrid(
                occurrences = occurrences,
                timeColumnWidth = TimeColumnWidth,
                dayColumnWidth = dayColumnWidth,
                periodRowHeight = periodRowHeight,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun WeekHeader(
    monday: LocalDate,
    today: LocalDate,
    dayColumnWidth: Dp,
) {
    Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
        Box(Modifier.width(TimeColumnWidth).height(HeaderHeight), contentAlignment = Alignment.Center) {
            Text("节", fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        (0..6).forEach { offset ->
            val date = monday.plusDays(offset.toLong())
            val isToday = date == today
            Column(
                Modifier.width(dayColumnWidth).height(HeaderHeight)
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "周${"一二三四五六日"[offset]}",
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    "${date.monthValue}/${date.dayOfMonth}",
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    occurrences: List<CourseOccurrence>,
    timeColumnWidth: Dp,
    dayColumnWidth: Dp,
    periodRowHeight: Dp,
    modifier: Modifier = Modifier,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val showStartTime = periodRowHeight >= 30.dp
    Box(
        modifier.background(MaterialTheme.colorScheme.surface).drawBehind {
            val timeWidth = timeColumnWidth.toPx()
            val dayWidth = dayColumnWidth.toPx()
            val rowHeight = periodRowHeight.toPx()
            for (period in 0..PERIOD_COUNT) {
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
        (1..PERIOD_COUNT).forEach { period ->
            Column(
                Modifier.offset(y = periodRowHeight * (period - 1))
                    .width(timeColumnWidth)
                    .height(periodRowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(period.toString(), fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 10.sp)
                if (showStartTime) {
                    Text(
                        WhuPeriodTimes.start(period).toString(),
                        fontSize = 6.sp,
                        lineHeight = 7.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        occurrences.forEach { occurrence ->
            CourseBlock(
                occurrence = occurrence,
                timeColumnWidth = timeColumnWidth,
                dayColumnWidth = dayColumnWidth,
                periodRowHeight = periodRowHeight,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun CourseBlock(
    occurrence: CourseOccurrence,
    timeColumnWidth: Dp,
    dayColumnWidth: Dp,
    periodRowHeight: Dp,
    onEdit: (CourseEditTarget) -> Unit,
) {
    val meeting = occurrence.meeting
    val span = (meeting.endPeriod - meeting.startPeriod + 1).coerceAtLeast(1)
    val status = when (occurrence.course.marker) {
        CourseMarker.NORMAL -> null
        CourseMarker.EXAM_WEEK -> "考试"
        CourseMarker.FINISHED -> "结课"
    }
    Card(
        modifier = Modifier
            .offset(
                x = timeColumnWidth + dayColumnWidth * (meeting.dayOfWeek - 1),
                y = periodRowHeight * (meeting.startPeriod - 1),
            )
            .width(dayColumnWidth)
            .height(periodRowHeight * span)
            .padding(1.dp)
            .alpha(if (occurrence.course.marker == CourseMarker.FINISHED) 0.58f else 1f)
            .clip(RoundedCornerShape(5.dp))
            .clickable { onEdit(CourseEditTarget(occurrence.course, meeting)) },
        colors = CardDefaults.cardColors(
            containerColor = Color(occurrence.course.colorArgb).copy(alpha = 0.82f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(5.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (status != null) {
                Text(
                    status,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                        RoundedCornerShape(3.dp),
                    ),
                )
            }
            Text(
                occurrence.course.name,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (span >= 3) 5 else if (span == 2) 3 else 2,
            )
            if (span >= 2 && meeting.room.isNotBlank()) {
                Text(
                    meeting.room,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (span >= 3) 2 else 1,
                )
            }
            if (span >= 4 && occurrence.course.note.isNotBlank()) {
                Text(
                    "备：${occurrence.course.note}",
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
