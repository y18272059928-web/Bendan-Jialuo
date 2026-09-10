package cn.edu.whu.schedule.importer

import cn.edu.whu.schedule.data.Course
import cn.edu.whu.schedule.data.CourseMeeting
import cn.edu.whu.schedule.data.ScheduleSnapshot
import cn.edu.whu.schedule.data.Semester
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

sealed interface ImportResult {
    data class Success(val snapshot: ScheduleSnapshot, val note: String) : ImportResult
    data class NeedsCapture(val reason: String) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/** Authentication stays inside WebView; this boundary never receives a password. */
interface WhuScheduleImporter {
    fun parseDocument(document: String): ImportResult
}

/**
 * Tolerant adapter for common graduate-system JSON and HTML-table field names.
 * It accepts only rows containing a course name, weekday and period/time signal,
 * which prevents unrelated portal data from being imported.
 */
class AdaptiveWhuScheduleImporter(
    private val clock: Clock = Clock.systemDefaultZone(),
) : WhuScheduleImporter {
    override fun parseDocument(document: String): ImportResult {
        val root = decodeJavascriptResult(document)
            ?: return ImportResult.Failure("当前页面内容无法读取，请刷新课表页面后重试。")

        val pageText = (root as? JSONObject)?.optString("pageText").orEmpty()
        val candidates = mutableListOf<MeetingCandidate>()
        collectJsonCandidates(root, candidates)
        if (root is JSONObject) {
            collectCapturedBodies(root.optJSONArray("captures"), candidates)
            collectTableCandidates(root.optJSONArray("tables"), candidates)
        }

        val unique = candidates
            .filter { it.name.isNotBlank() && it.dayOfWeek in 1..7 && it.startPeriod in 1..13 }
            .distinctBy {
                listOf(it.name, it.teacher, it.room, it.dayOfWeek, it.startPeriod, it.endPeriod, it.weeks)
            }
            .take(MAX_MEETINGS)

        if (unique.isEmpty()) {
            return ImportResult.NeedsCapture(
                "还没有识别到课程。请进入“我的课表/学生课表”并切换一次周次或学期，再点识别。",
            )
        }

        val parsedMaxWeek = unique.flatMap { it.weeks.orEmpty() }.maxOrNull()
        val explicitTotalWeeks = findString(root, TOTAL_WEEKS_KEYS)?.firstInt()
        val totalWeeks = (explicitTotalWeeks ?: parsedMaxWeek ?: 20).coerceIn(1, 30)
        val today = LocalDate.now(clock)
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val explicitFirstMonday = findDate(root, FIRST_MONDAY_KEYS)
        val shownWeek = CURRENT_WEEK.find(pageText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val firstMonday = when {
            explicitFirstMonday != null -> explicitFirstMonday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            shownWeek != null && shownWeek in 1..30 -> currentMonday.minusWeeks((shownWeek - 1).toLong())
            else -> currentMonday
        }
        val needsDateCheck = explicitFirstMonday == null && shownWeek == null
        val semesterName = findString(root, SEMESTER_NAME_KEYS)
            ?.takeIf { it.length in 3..80 }
            ?: inferSemesterName(today)

        val courseNames = unique.map { it.name }.distinct().take(MAX_COURSES)
        val ids = courseNames.withIndex().associate { (index, name) -> name to (index + 1L) }
        val courses = courseNames.mapIndexed { index, name ->
            val first = unique.first { it.name == name }
            Course(
                id = index + 1L,
                name = name,
                teacher = first.teacher.ifBlank { "教师待确认" },
                colorArgb = COLORS[index % COLORS.size],
            )
        }
        val meetings = unique.filter { ids.containsKey(it.name) }.mapIndexed { index, item ->
            val weeks = item.weeks?.filter { it in 1..totalWeeks }?.toSet().orEmpty()
                .ifEmpty { (1..totalWeeks).toSet() }
            CourseMeeting(
                id = index + 1L,
                courseId = ids.getValue(item.name),
                room = item.room.ifBlank { "地点待确认" },
                dayOfWeek = item.dayOfWeek,
                startPeriod = item.startPeriod,
                endPeriod = item.endPeriod.coerceIn(item.startPeriod, 13),
                startTime = item.startTime ?: PERIOD_TIMES.getValue(item.startPeriod).first,
                endTime = item.endTime ?: PERIOD_TIMES.getValue(item.endPeriod.coerceIn(item.startPeriod, 13)).second,
                weeks = weeks,
            )
        }

        val note = buildString {
            append("识别到 ${courses.size} 门课程、${meetings.size} 条上课安排。")
            if (needsDateCheck) append(" 页面未给出当前教学周，暂按本周为第1周；请先核对预览。")
        }
        return ImportResult.Success(
            snapshot = ScheduleSnapshot(
                semester = Semester(
                    name = if (needsDateCheck) "$semesterName（首周待确认）" else semesterName,
                    firstMonday = firstMonday,
                    totalWeeks = totalWeeks,
                ),
                courses = courses,
                meetings = meetings,
            ),
            note = note,
        )
    }

    private fun collectCapturedBodies(captures: JSONArray?, output: MutableList<MeetingCandidate>) {
        if (captures == null) return
        for (index in 0 until captures.length()) {
            val body = captures.optJSONObject(index)?.optString("body").orEmpty()
            if (body.isBlank()) continue
            parseJsonLike(body)?.let { collectJsonCandidates(it, output) }
        }
    }

    private fun collectJsonCandidates(value: Any?, output: MutableList<MeetingCandidate>, depth: Int = 0) {
        if (value == null || depth > 12 || output.size >= MAX_MEETINGS * 2) return
        when (value) {
            is JSONObject -> {
                val values = value.toNormalizedMap()
                parseCandidate(values)?.let(output::add)
                value.keys().forEach { key -> collectJsonCandidates(value.opt(key), output, depth + 1) }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                collectJsonCandidates(value.opt(index), output, depth + 1)
            }
        }
    }

    private fun collectTableCandidates(tables: JSONArray?, output: MutableList<MeetingCandidate>) {
        if (tables == null) return
        for (tableIndex in 0 until tables.length()) {
            val table = tables.optJSONObject(tableIndex) ?: continue
            val rows = table.optJSONArray("rows") ?: continue
            var headers = table.optJSONArray("headers")?.toStringList().orEmpty()
            var firstRow = 0
            if (headers.isEmpty() && rows.length() > 0) {
                val possible = rows.optJSONArray(0)?.toStringList().orEmpty()
                if (possible.any { isKnownHeader(it) }) {
                    headers = possible
                    firstRow = 1
                }
            }
            if (headers.isEmpty()) continue
            for (rowIndex in firstRow until rows.length()) {
                val cells = rows.optJSONArray(rowIndex)?.toStringList().orEmpty()
                if (cells.isEmpty()) continue
                val row = headers.mapIndexedNotNull { index, header ->
                    cells.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { normalizeKey(header) to it }
                }.toMap()
                parseCandidate(row)?.let(output::add)
                collectGridCells(headers, cells, row, output)
            }
        }
    }

    private fun collectGridCells(
        headers: List<String>,
        cells: List<String>,
        base: Map<String, String>,
        output: MutableList<MeetingCandidate>,
    ) {
        headers.forEachIndexed { index, header ->
            val day = parseDay(header) ?: return@forEachIndexed
            val cell = cells.getOrNull(index).orEmpty()
            if (cell.isBlank() || cell == "-") return@forEachIndexed
            val combined = base.toMutableMap().apply {
                put(normalizeKey("星期"), day.toString())
                put(normalizeKey("上课时间"), cell)
                if (lookup(this, COURSE_NAME_KEYS).isNullOrBlank()) {
                    put(normalizeKey("课程名称"), cell.lineSequence().firstOrNull().orEmpty())
                }
            }
            parseCandidate(combined)?.let(output::add)
        }
    }

    private fun parseCandidate(values: Map<String, String>): MeetingCandidate? {
        val composite = lookup(values, COMPOSITE_KEYS).orEmpty()
        val name = lookup(values, COURSE_NAME_KEYS)
            ?.cleanCourseName()
            ?.takeIf { it.length in 1..120 }
            ?: return null
        val day = parseDay(lookup(values, DAY_KEYS).orEmpty()) ?: parseDay(composite) ?: return null
        val periodPair = parsePeriods(
            lookup(values, START_PERIOD_KEYS),
            lookup(values, END_PERIOD_KEYS),
            lookup(values, PERIOD_KEYS) ?: composite,
        ) ?: return null
        val weekDescription = lookup(values, WEEK_KEYS)
            ?: composite.takeIf { it.contains('周') || it.contains("week", ignoreCase = true) }
        val weeks = parseWeeks(
            weekDescription,
            lookup(values, START_WEEK_KEYS),
            lookup(values, END_WEEK_KEYS),
        )
        val startTime = parseTime(lookup(values, START_TIME_KEYS)) ?: parseTimePair(composite)?.first
        val endTime = parseTime(lookup(values, END_TIME_KEYS)) ?: parseTimePair(composite)?.second
        return MeetingCandidate(
            name = name,
            teacher = lookup(values, TEACHER_KEYS).orEmpty().cleanValue(),
            room = lookup(values, ROOM_KEYS).orEmpty().cleanValue(),
            dayOfWeek = day,
            startPeriod = periodPair.first,
            endPeriod = periodPair.second,
            startTime = startTime,
            endTime = endTime,
            weeks = weeks,
        )
    }

    companion object {
        private const val MAX_COURSES = 100
        private const val MAX_MEETINGS = 500
        private val CURRENT_WEEK = Regex("(?:当前(?:教学)?|教学|本)\\s*第?\\s*(\\d{1,2})\\s*周")

        private val COLORS = listOf(
            0xFF6750A4, 0xFF006A6A, 0xFF9C4048, 0xFF3F6374,
            0xFF765A00, 0xFF386A20, 0xFF7D5260, 0xFF4F6356,
        )
        private val PERIOD_TIMES = mapOf(
            1 to (LocalTime.of(8, 0) to LocalTime.of(8, 45)),
            2 to (LocalTime.of(8, 50) to LocalTime.of(9, 35)),
            3 to (LocalTime.of(9, 50) to LocalTime.of(10, 35)),
            4 to (LocalTime.of(10, 40) to LocalTime.of(11, 25)),
            5 to (LocalTime.of(11, 30) to LocalTime.of(12, 15)),
            6 to (LocalTime.of(14, 5) to LocalTime.of(14, 50)),
            7 to (LocalTime.of(14, 55) to LocalTime.of(15, 40)),
            8 to (LocalTime.of(15, 45) to LocalTime.of(16, 30)),
            9 to (LocalTime.of(16, 40) to LocalTime.of(17, 25)),
            10 to (LocalTime.of(17, 30) to LocalTime.of(18, 15)),
            11 to (LocalTime.of(18, 30) to LocalTime.of(19, 15)),
            12 to (LocalTime.of(19, 20) to LocalTime.of(20, 5)),
            13 to (LocalTime.of(20, 10) to LocalTime.of(20, 55)),
        )

        private val COURSE_NAME_KEYS = keys("课程名称", "课程名", "kcmc", "courseName", "course_name", "subjectName", "subject", "title")
        private val TEACHER_KEYS = keys("任课教师", "教师", "教师姓名", "jsxm", "rkjs", "skjs", "teacher", "teacherName")
        private val ROOM_KEYS = keys("上课地点", "地点", "教室", "jsmc", "jxdd", "skdd", "room", "roomName", "classroom", "classroomName", "location")
        private val DAY_KEYS = keys("星期", "星期几", "上课星期", "xqj", "weekday", "weekDay", "dayOfWeek", "day")
        private val START_PERIOD_KEYS = keys("开始节次", "起始节次", "ksjc", "startPeriod", "startSection", "beginSection", "jcStart")
        private val END_PERIOD_KEYS = keys("结束节次", "终止节次", "jsjc", "endPeriod", "endSection", "jcEnd")
        private val PERIOD_KEYS = keys("节次", "上课节次", "jc", "jcs", "period", "periods", "section", "sections", "skjc")
        private val START_TIME_KEYS = keys("开始时间", "上课时间", "kssj", "startTime", "beginTime")
        private val END_TIME_KEYS = keys("结束时间", "下课时间", "jssj", "endTime")
        private val WEEK_KEYS = keys("周次", "上课周次", "zcd", "weeks", "weekList", "weekDescription", "teachingWeeks", "skzc")
        private val START_WEEK_KEYS = keys("起始周", "开始周", "qsz", "startWeek", "beginWeek")
        private val END_WEEK_KEYS = keys("结束周", "终止周", "jsz", "endWeek")
        private val COMPOSITE_KEYS = keys("上课时间星期节号", "上课时间", "时间地点", "sksj", "courseTime", "timeDescription", "description")
        private val FIRST_MONDAY_KEYS = keys("firstMonday", "semesterStartDate", "termStartDate", "xqksrq", "kxrq", "开学日期")
        private val SEMESTER_NAME_KEYS = keys("semesterName", "termName", "xnxqmc", "xqmc", "学期名称", "学年学期")
        private val TOTAL_WEEKS_KEYS = keys("totalWeeks", "weekTotal", "总周数", "教学周数")

        internal fun parseWeeksForTest(value: String): Set<Int>? = parseWeeks(value, null, null)
        internal fun parseDayForTest(value: String): Int? = parseDay(value)

        private fun keys(vararg values: String) = values.map(::normalizeKey).toSet()

        private fun normalizeKey(value: String): String = value
            .lowercase()
            .replace(Regex("[\\s_\\-:/（）()【】\\[\\]]"), "")

        private fun lookup(values: Map<String, String>, aliases: Set<String>): String? =
            aliases.firstNotNullOfOrNull { values[it]?.takeIf(String::isNotBlank) }

        private fun JSONObject.toNormalizedMap(): Map<String, String> = buildMap {
            keys().forEach { key ->
                val value = opt(key)
                when {
                    value == null || value == JSONObject.NULL || value is JSONObject -> Unit
                    value is JSONArray -> value.primitiveText()?.let { put(normalizeKey(key), it) }
                    else -> put(normalizeKey(key), value.toString())
                }
            }
        }

        private fun JSONArray.primitiveText(): String? = buildList {
            for (index in 0 until length()) {
                val item = opt(index)
                if (item is JSONObject || item is JSONArray || item == JSONObject.NULL) return null
                add(item.toString())
            }
        }.joinToString(",")

        private fun JSONArray.toStringList(): List<String> = buildList {
            for (index in 0 until length()) add(optString(index).trim())
        }

        private fun decodeJavascriptResult(raw: String): Any? {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed == "null" || trimmed == "undefined") return null
            return runCatching {
                val first = JSONTokener(trimmed).nextValue()
                if (first is String) parseJsonLike(first) ?: first else first
            }.getOrNull()
        }

        private fun parseJsonLike(raw: String): Any? {
            var text = raw.trim().removePrefix("\uFEFF")
            if (!text.startsWith('{') && !text.startsWith('[')) {
                val start = listOf(text.indexOf('{'), text.indexOf('[')).filter { it >= 0 }.minOrNull() ?: return null
                val end = max(text.lastIndexOf('}'), text.lastIndexOf(']'))
                if (end <= start) return null
                text = text.substring(start, end + 1)
            }
            return runCatching { JSONTokener(text).nextValue() }.getOrNull()
        }

        private fun parseDay(raw: String): Int? {
            val value = raw.trim().lowercase()
            Regex("(?:星期|周|礼拜)\\s*([一二三四五六日天1-7])").find(value)?.groupValues?.get(1)?.let {
                return dayToken(it)
            }
            Regex("(?:day|weekday)\\D*([1-7])").find(value)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                .indexOfFirst { value.contains(it) }
                .takeIf { it >= 0 }
                ?.let { return it + 1 }
            return value.toIntOrNull()?.takeIf { it in 1..7 }
        }

        private fun dayToken(token: String): Int? = when (token) {
            "一", "1" -> 1
            "二", "2" -> 2
            "三", "3" -> 3
            "四", "4" -> 4
            "五", "5" -> 5
            "六", "6" -> 6
            "日", "天", "7" -> 7
            else -> null
        }

        private fun parsePeriods(startRaw: String?, endRaw: String?, combined: String?): Pair<Int, Int>? {
            val start = startRaw?.firstInt()
            val end = endRaw?.firstInt()
            if (start != null && start in 1..13) return start to (end ?: start).coerceIn(start, 13)
            val value = combined.orEmpty()
            val range = Regex("(?:第\\s*)?(\\d{1,2})\\s*[-—~至到]\\s*(\\d{1,2})\\s*节?").find(value)
            if (range != null) {
                val first = range.groupValues[1].toInt()
                val last = range.groupValues[2].toInt()
                if (first in 1..13 && last in first..13) return first to last
            }
            val single = Regex("(?:第\\s*)?(\\d{1,2})\\s*节").find(value)?.groupValues?.get(1)?.toIntOrNull()
            if (single != null && single in 1..13) return single to single
            if (value.trim().startsWith('[') || (value.contains(',') && !value.contains('周'))) {
                val periods = Regex("\\d{1,2}").findAll(value).map { it.value.toInt() }.filter { it in 1..13 }.toList()
                if (periods.isNotEmpty()) return periods.min() to periods.max()
            }
            return null
        }

        private fun parseWeeks(raw: String?, startRaw: String?, endRaw: String?): Set<Int>? {
            val start = startRaw?.firstInt()
            val end = endRaw?.firstInt()
            if (start != null && end != null && start in 1..30 && end in start..30) return (start..end).toSet()
            val value = raw.orEmpty()
            if (value.isBlank()) return null
            val oddOnly = value.contains('单') || value.contains("odd", ignoreCase = true)
            val evenOnly = value.contains('双') || value.contains("even", ignoreCase = true)
            val result = linkedSetOf<Int>()
            Regex("(\\d{1,2})\\s*[-—~至到]\\s*(\\d{1,2})").findAll(value).forEach { match ->
                val first = match.groupValues[1].toInt()
                val last = match.groupValues[2].toInt()
                if (first in 1..30 && last in first..30) result.addAll(first..last)
            }
            if (result.isEmpty()) {
                Regex("\\d{1,2}").findAll(value).mapNotNull { it.value.toIntOrNull() }
                    .filterTo(result) { it in 1..30 }
            }
            val filtered = result.filter { (!oddOnly || it % 2 == 1) && (!evenOnly || it % 2 == 0) }.toSet()
            return filtered.takeIf { it.isNotEmpty() }
        }

        private fun parseTime(raw: String?): LocalTime? {
            val match = Regex("(?<!\\d)([01]?\\d|2[0-3])[:：]([0-5]\\d)").find(raw.orEmpty()) ?: return null
            return runCatching { LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
        }

        private fun parseTimePair(raw: String): Pair<LocalTime, LocalTime>? {
            val values = Regex("(?<!\\d)([01]?\\d|2[0-3])[:：]([0-5]\\d)").findAll(raw)
                .mapNotNull { runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() }
                .take(2).toList()
            return if (values.size == 2) values[0] to values[1] else null
        }

        private fun String.firstInt(): Int? = Regex("\\d{1,2}").find(this)?.value?.toIntOrNull()
        private fun String.cleanValue(): String = replace(Regex("\\s+"), " ").trim().take(120)
        private fun String.cleanCourseName(): String = cleanValue()
            .removePrefix("课程：").removePrefix("课程:").trim()

        private fun isKnownHeader(value: String): Boolean {
            val key = normalizeKey(value)
            return key in COURSE_NAME_KEYS || key in DAY_KEYS || key in PERIOD_KEYS || key in WEEK_KEYS
        }

        private fun findString(value: Any?, aliases: Set<String>, depth: Int = 0): String? {
            if (depth > 10) return null
            return when (value) {
                is JSONObject -> {
                    value.keys().forEach { key ->
                        val child = value.opt(key)
                        if (normalizeKey(key) in aliases && child != null && child != JSONObject.NULL) return child.toString()
                    }
                    value.keys().asSequence()
                        .firstNotNullOfOrNull { key -> findString(value.opt(key), aliases, depth + 1) }
                }
                is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findString(value.opt(it), aliases, depth + 1) }
                else -> null
            }
        }

        private fun findDate(value: Any?, aliases: Set<String>): LocalDate? = findString(value, aliases)?.let(::parseDate)

        private fun parseDate(raw: String): LocalDate? {
            val candidate = Regex("\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}").find(raw)?.value
                ?.let { it.replace('年', '-').replace('月', '-').replace('.', '-').replace('/', '-') }
                ?: Regex("\\d{8}").find(raw)?.value
                ?: return null
            val formatters = listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy-M-d"), DateTimeFormatter.BASIC_ISO_DATE)
            return formatters.firstNotNullOfOrNull { formatter -> runCatching { LocalDate.parse(candidate, formatter) }.getOrNull() }
        }

        private fun inferSemesterName(date: LocalDate): String = when (date.monthValue) {
            in 8..12 -> "${date.year}-${date.year + 1}学年第一学期"
            in 1..7 -> "${date.year - 1}-${date.year}学年第二学期"
            else -> "当前学期"
        }
    }
}

private data class MeetingCandidate(
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val weeks: Set<Int>?,
)
