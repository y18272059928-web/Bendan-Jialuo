package cn.edu.whu.schedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.whu.schedule.data.Exam
import cn.edu.whu.schedule.data.NoClassDate
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.StudyTask
import cn.edu.whu.schedule.data.courseName
import cn.edu.whu.schedule.data.nextPersonalItemId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val compactDate = DateTimeFormatter.ofPattern("M月d日")
private val compactDateTime = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
fun AcademicPlannerSection(
    snapshot: ScheduleSnapshot,
    onScheduleChanged: (ScheduleSnapshot) -> Unit,
) {
    var examEditor by remember { mutableStateOf<Exam?>(null) }
    var taskEditor by remember { mutableStateOf<StudyTask?>(null) }
    var noClassEditor by remember { mutableStateOf<NoClassDate?>(null) }
    var creatingExam by remember { mutableStateOf(false) }
    var creatingTask by remember { mutableStateOf(false) }
    var creatingNoClass by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val upcomingExams = snapshot.exams.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    val openTasks = snapshot.tasks.filterNot(StudyTask::completed).sortedBy { it.dueAt }
    val completedTasks = snapshot.tasks.filter(StudyTask::completed).sortedByDescending { it.dueAt }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("学业计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${upcomingExams.size} 场待考 · ${openTasks.size} 项待办 · ${snapshot.noClassDates.size} 个停课日")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { creatingExam = true }) { Text("添加考试") }
                OutlinedButton(onClick = { creatingTask = true }) { Text("添加待办") }
            }
            OutlinedButton(onClick = { creatingNoClass = true }) { Text("添加停课日") }

            if (upcomingExams.isNotEmpty()) {
                Text("考试", fontWeight = FontWeight.Bold)
                upcomingExams.take(4).forEach { exam ->
                    PlannerRow(
                        title = exam.title,
                        subtitle = buildList {
                            add(exam.date.format(compactDate))
                            exam.startTime?.let { add(it.toString()) }
                            if (exam.room.isNotBlank()) add(exam.room)
                        }.joinToString(" · "),
                        onClick = { examEditor = exam },
                    )
                }
            }
            if (openTasks.isNotEmpty()) {
                Text("待办", fontWeight = FontWeight.Bold)
                openTasks.take(5).forEach { task ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = {
                                onScheduleChanged(
                                    snapshot.copy(tasks = snapshot.tasks.map { item ->
                                        if (item.id == task.id) item.copy(completed = true) else item
                                    }),
                                )
                            },
                        )
                        Column(
                            Modifier.weight(1f).clickable { taskEditor = task }.padding(vertical = 6.dp),
                        ) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${task.dueAt.format(compactDateTime)}${snapshot.courseName(task.courseId)?.let { " · $it" }.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (completedTasks.isNotEmpty()) {
                Text("已完成", fontWeight = FontWeight.Bold)
                completedTasks.take(3).forEach { task ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = {
                                onScheduleChanged(
                                    snapshot.copy(tasks = snapshot.tasks.map { item ->
                                        if (item.id == task.id) item.copy(completed = false) else item
                                    }),
                                )
                            },
                        )
                        Column(
                            Modifier.weight(1f).clickable { taskEditor = task }.padding(vertical = 6.dp),
                        ) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            Text("点按可修改，取消勾选可恢复", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            snapshot.noClassDates.filter { !it.date.isBefore(today) }.take(4).forEach { holiday ->
                PlannerRow(
                    title = "停课 · ${holiday.name}",
                    subtitle = holiday.date.format(compactDate),
                    onClick = { noClassEditor = holiday },
                )
            }
            if (upcomingExams.isEmpty() && openTasks.isEmpty() && snapshot.noClassDates.none { !it.date.isBefore(today) }) {
                Text("暂无考试、待办或停课安排。", color = MaterialTheme.colorScheme.secondary)
            }
            Text("点按已有项目可修改或删除；完成的待办会保留在本机。", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (creatingExam || examEditor != null) {
        ExamEditorDialog(
            snapshot = snapshot,
            initial = examEditor,
            onDismiss = { creatingExam = false; examEditor = null },
            onSave = { exam ->
                onScheduleChanged(snapshot.copy(exams = snapshot.exams.filterNot { it.id == exam.id } + exam))
                creatingExam = false
                examEditor = null
            },
            onDelete = examEditor?.let { editing ->
                {
                    onScheduleChanged(snapshot.copy(exams = snapshot.exams.filterNot { it.id == editing.id }))
                    examEditor = null
                }
            },
        )
    }
    if (creatingTask || taskEditor != null) {
        TaskEditorDialog(
            snapshot = snapshot,
            initial = taskEditor,
            onDismiss = { creatingTask = false; taskEditor = null },
            onSave = { task ->
                onScheduleChanged(snapshot.copy(tasks = snapshot.tasks.filterNot { it.id == task.id } + task))
                creatingTask = false
                taskEditor = null
            },
            onDelete = taskEditor?.let { editing ->
                {
                    onScheduleChanged(snapshot.copy(tasks = snapshot.tasks.filterNot { it.id == editing.id }))
                    taskEditor = null
                }
            },
        )
    }
    if (creatingNoClass || noClassEditor != null) {
        NoClassDateDialog(
            snapshot = snapshot,
            initial = noClassEditor,
            onDismiss = { creatingNoClass = false; noClassEditor = null },
            onSave = { item ->
                onScheduleChanged(
                    snapshot.copy(
                        noClassDates = snapshot.noClassDates.filterNot { it.id == item.id || it.date == item.date } + item,
                    ),
                )
                creatingNoClass = false
                noClassEditor = null
            },
            onDelete = noClassEditor?.let { editing ->
                {
                    onScheduleChanged(snapshot.copy(noClassDates = snapshot.noClassDates.filterNot { it.id == editing.id }))
                    noClassEditor = null
                }
            },
        )
    }
}

@Composable
private fun PlannerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ExamEditorDialog(
    snapshot: ScheduleSnapshot,
    initial: Exam?,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var courseId by remember(initial) { mutableStateOf(initial?.courseId) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var startTime by remember(initial) { mutableStateOf(initial?.startTime ?: LocalTime.of(9, 0)) }
    var endTime by remember(initial) { mutableStateOf(initial?.endTime ?: LocalTime.of(11, 0)) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var seat by remember(initial) { mutableStateOf(initial?.seat.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    val valid = title.isNotBlank() && endTime.isAfter(startTime)

    PlannerDialog(
        title = if (initial == null) "添加考试" else "修改考试",
        onDismiss = onDismiss,
        onDelete = onDelete,
        valid = valid,
        onConfirm = {
            onSave(
                Exam(
                    id = initial?.id ?: snapshot.nextPersonalItemId(),
                    courseId = courseId,
                    title = title.trim(),
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    room = room.trim(),
                    seat = seat.trim(),
                    note = note.trim(),
                ),
            )
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("考试名称") }, modifier = Modifier.fillMaxWidth())
        CoursePicker(snapshot, courseId) { selected ->
            courseId = selected
            if (title.isBlank()) title = snapshot.courseName(selected)?.plus("考试").orEmpty()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton(date, { date = it }, Modifier.weight(1f))
            TimeButton("开始", startTime, { startTime = it }, Modifier.weight(1f))
            TimeButton("结束", endTime, { endTime = it }, Modifier.weight(1f))
        }
        OutlinedTextField(room, { room = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(seat, { seat = it }, label = { Text("座位号") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
        if (!valid && title.isNotBlank()) Text("结束时间应晚于开始时间。", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TaskEditorDialog(
    snapshot: ScheduleSnapshot,
    initial: StudyTask?,
    onDismiss: () -> Unit,
    onSave: (StudyTask) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var courseId by remember(initial) { mutableStateOf(initial?.courseId) }
    var dueDate by remember(initial) { mutableStateOf(initial?.dueAt?.toLocalDate() ?: LocalDate.now().plusDays(1)) }
    var dueTime by remember(initial) { mutableStateOf(initial?.dueAt?.toLocalTime() ?: LocalTime.of(20, 0)) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var reminderMinutes by remember(initial) { mutableStateOf(initial?.reminderMinutes ?: 60) }
    var completed by remember(initial) { mutableStateOf(initial?.completed ?: false) }

    PlannerDialog(
        title = if (initial == null) "添加待办" else "修改待办",
        onDismiss = onDismiss,
        onDelete = onDelete,
        valid = title.isNotBlank(),
        onConfirm = {
            onSave(
                StudyTask(
                    id = initial?.id ?: snapshot.nextPersonalItemId(),
                    courseId = courseId,
                    title = title.trim(),
                    dueAt = LocalDateTime.of(dueDate, dueTime),
                    note = note.trim(),
                    completed = completed,
                    reminderMinutes = reminderMinutes,
                ),
            )
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("待办内容") }, modifier = Modifier.fillMaxWidth())
        CoursePicker(snapshot, courseId) { courseId = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton(dueDate, { dueDate = it }, Modifier.weight(1f))
            TimeButton("截止", dueTime, { dueTime = it }, Modifier.weight(1f))
        }
        OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(15 to "提前15分", 60 to "提前1小时", 1440 to "提前1天").forEach { (minutes, label) ->
                OutlinedButton(onClick = { reminderMinutes = minutes }, enabled = reminderMinutes != minutes) { Text(label) }
            }
        }
        Row {
            Checkbox(completed, { completed = it })
            Text("已完成", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun NoClassDateDialog(
    snapshot: ScheduleSnapshot,
    initial: NoClassDate?,
    onDismiss: () -> Unit,
    onSave: (NoClassDate) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "停课") }
    var date by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    PlannerDialog(
        title = if (initial == null) "添加停课日" else "修改停课日",
        onDismiss = onDismiss,
        onDelete = onDelete,
        valid = name.isNotBlank(),
        onConfirm = { onSave(NoClassDate(initial?.id ?: snapshot.nextPersonalItemId(), date, name.trim())) },
    ) {
        DateButton(date, { date = it }, Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("名称，例如：国庆节") }, modifier = Modifier.fillMaxWidth())
        Text("该日期的课程、课前提醒和下次日历同步都会自动跳过。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlannerDialog(
    title: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    valid: Boolean,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.widthIn(max = 520.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = valid) { Text("保存") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("删除", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun CoursePicker(snapshot: ScheduleSnapshot, selectedId: Long?, onSelected: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(snapshot.courseName(selectedId) ?: "不关联课程")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("不关联课程") },
                onClick = { onSelected(null); expanded = false },
            )
            snapshot.courses.forEach { course ->
                DropdownMenuItem(
                    text = { Text(course.name) },
                    onClick = { onSelected(course.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DateButton(date: LocalDate, onChanged: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onChanged(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
    ) { Text(date.format(compactDate)) }
}

@Composable
private fun TimeButton(label: String, time: LocalTime, onChanged: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChanged(LocalTime.of(hour, minute)) },
                time.hour,
                time.minute,
                true,
            ).show()
        },
    ) { Text("$label $time") }
}