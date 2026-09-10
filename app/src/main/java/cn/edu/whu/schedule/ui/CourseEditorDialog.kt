package cn.edu.whu.schedule.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.CourseMarker
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.domain.ScheduleEditor
import cn.edu.whu.schedule.domain.WhuPeriodTimes

data class CourseEditTarget(
    val course: Course,
    val meeting: CourseMeeting,
)

@Composable
fun CourseEditorDialog(
    snapshot: ScheduleSnapshot,
    target: CourseEditTarget?,
    onDismiss: () -> Unit,
    onSave: (Course, CourseMeeting) -> Unit,
    onDelete: ((Long) -> Unit)?,
) {
    val totalWeeks = snapshot.semester.totalWeeks.coerceIn(1, 30)
    var name by remember(target) { mutableStateOf(target?.course?.name.orEmpty()) }
    var teacher by remember(target) { mutableStateOf(target?.course?.teacher.orEmpty()) }
    var room by remember(target) { mutableStateOf(target?.meeting?.room.orEmpty()) }
    var note by remember(target) { mutableStateOf(target?.course?.note.orEmpty()) }
    var marker by remember(target) { mutableStateOf(target?.course?.marker ?: CourseMarker.NORMAL) }
    var dayOfWeek by remember(target) { mutableIntStateOf(target?.meeting?.dayOfWeek ?: 1) }
    var startPeriod by remember(target) { mutableIntStateOf(target?.meeting?.startPeriod ?: 1) }
    var endPeriod by remember(target) { mutableIntStateOf(target?.meeting?.endPeriod ?: 2) }
    var weeks by remember(target, totalWeeks) {
        mutableStateOf(target?.meeting?.weeks?.filter { it in 1..totalWeeks }?.toSet() ?: (1..totalWeeks).toSet())
    }
    val canSave = name.isNotBlank() && weeks.isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (target == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "修改后会立即更新课表与提醒；已结课课程不会再发送通知。",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称（必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                EditorLabel("课程标识")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CourseMarker.entries.forEach { option ->
                        FilterChip(
                            selected = marker == option,
                            onClick = { marker = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                EditorLabel("上课星期")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    "一二三四五六日".forEachIndexed { index, day ->
                        FilterChip(
                            selected = dayOfWeek == index + 1,
                            onClick = { dayOfWeek = index + 1 },
                            label = { Text("周$day") },
                        )
                    }
                }

                EditorLabel("上课节次")
                PeriodPicker(
                    label = "开始",
                    value = startPeriod,
                    onMinus = {
                        if (startPeriod > 1) {
                            startPeriod--
                            if (endPeriod < startPeriod) endPeriod = startPeriod
                        }
                    },
                    onPlus = {
                        if (startPeriod < 13) {
                            startPeriod++
                            if (endPeriod < startPeriod) endPeriod = startPeriod
                        }
                    },
                )
                PeriodPicker(
                    label = "结束",
                    value = endPeriod,
                    onMinus = { if (endPeriod > startPeriod) endPeriod-- },
                    onPlus = { if (endPeriod < 13) endPeriod++ },
                )
                Text(
                    "${WhuPeriodTimes.start(startPeriod)}–${WhuPeriodTimes.end(endPeriod)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                EditorLabel("上课周次（至少选择一周）")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { weeks = (1..totalWeeks).toSet() }) { Text("全选") }
                    OutlinedButton(onClick = { weeks = (1..totalWeeks).filter { it % 2 == 1 }.toSet() }) { Text("单周") }
                    OutlinedButton(onClick = { weeks = (1..totalWeeks).filter { it % 2 == 0 }.toSet() }) { Text("双周") }
                    OutlinedButton(onClick = { weeks = emptySet() }) { Text("清空") }
                }
                (1..totalWeeks).chunked(5).forEach { rowWeeks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        rowWeeks.forEach { week ->
                            FilterChip(
                                selected = week in weeks,
                                onClick = {
                                    weeks = if (week in weeks) weeks - week else weeks + week
                                },
                                label = { Text(week.toString()) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        enabled = canSave,
                        onClick = {
                            val courseId = target?.course?.id ?: ScheduleEditor.newCourseId(snapshot)
                            val meetingId = target?.meeting?.id ?: ScheduleEditor.newMeetingId(snapshot)
                            val color = target?.course?.colorArgb
                                ?: ScheduleEditor.palette[((courseId - 1) % ScheduleEditor.palette.size).toInt()]
                            onSave(
                                Course(
                                    id = courseId,
                                    name = name.trim(),
                                    teacher = teacher.trim(),
                                    colorArgb = color,
                                    note = note.trim(),
                                    marker = marker,
                                ),
                                CourseMeeting(
                                    id = meetingId,
                                    courseId = courseId,
                                    room = room.trim(),
                                    dayOfWeek = dayOfWeek,
                                    startPeriod = startPeriod,
                                    endPeriod = endPeriod,
                                    startTime = WhuPeriodTimes.start(startPeriod),
                                    endTime = WhuPeriodTimes.end(endPeriod),
                                    weeks = weeks,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存")
                    }
                }
                if (target != null && onDelete != null) {
                    Button(
                        onClick = { onDelete(target.meeting.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除这条上课安排")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun PeriodPicker(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label：第 $value 节", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            Text("−")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            Text("+")
        }
    }
}
