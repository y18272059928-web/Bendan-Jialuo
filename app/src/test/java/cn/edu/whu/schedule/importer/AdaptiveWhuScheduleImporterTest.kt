package cn.edu.whu.schedule.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AdaptiveWhuScheduleImporterTest {
    private val importer = AdaptiveWhuScheduleImporter(
        Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
    )

    @Test
    fun parsesWeekRangesAndParity() {
        assertEquals(setOf(1, 3, 5, 7), AdaptiveWhuScheduleImporter.parseWeeksForTest("1-8周（单周）"))
        assertEquals(setOf(2, 4, 6, 8), AdaptiveWhuScheduleImporter.parseWeeksForTest("第1至8周 双周"))
        assertEquals(setOf(1, 4, 9), AdaptiveWhuScheduleImporter.parseWeeksForTest("1,4,9周"))
    }

    @Test
    fun parsesChineseWeekdays() {
        assertEquals(1, AdaptiveWhuScheduleImporter.parseDayForTest("星期一"))
        assertEquals(4, AdaptiveWhuScheduleImporter.parseDayForTest("周四"))
        assertEquals(7, AdaptiveWhuScheduleImporter.parseDayForTest("礼拜天"))
    }

    @Test
    fun importsCapturedJsonAndInfersFirstMondayFromCurrentWeek() {
        val body = JSONObject().put(
            "data",
            JSONArray()
                .put(JSONObject()
                    .put("KCMC", "空间数据分析")
                    .put("JSXM", "张老师")
                    .put("JSMC", "信息学部 201")
                    .put("XQJ", 3)
                    .put("KSJC", 3)
                    .put("JSJC", 5)
                    .put("ZCD", "1-8周"))
                .put(JSONObject()
                    .put("courseName", "学术规范")
                    .put("teacherName", "李老师")
                    .put("roomName", "文理学部 101")
                    .put("weekDay", 5)
                    .put("startSection", 11)
                    .put("endSection", 13)
                    .put("weeks", "2-10周（双）")),
        ).toString()
        val capture = JSONObject()
            .put("pageText", "2026-2027学年第一学期 当前教学第1周")
            .put("captures", JSONArray().put(JSONObject().put("body", body)))
            .toString()

        val result = importer.parseDocument(JSONObject.quote(capture))

        assertTrue(result is ImportResult.Success)
        val snapshot = (result as ImportResult.Success).snapshot
        assertEquals(2, snapshot.courses.size)
        assertEquals(2, snapshot.meetings.size)
        assertEquals(LocalDate.of(2026, 9, 7), snapshot.semester.firstMonday)
        assertEquals(setOf(2, 4, 6, 8, 10), snapshot.meetings[1].weeks)
    }

    @Test
    fun ignoresUnrelatedPortalObjects() {
        val page = JSONObject()
            .put("pageText", "欢迎进入信息门户")
            .put("captures", JSONArray().put(JSONObject().put("body", "{\"name\":\"某用户\",\"day\":1}")))
            .toString()

        assertTrue(importer.parseDocument(JSONObject.quote(page)) is ImportResult.NeedsCapture)
    }

    @Test
    fun acceptsArrayWeeksAndSections() {
        val body = JSONObject().put(
            "rows",
            JSONArray().put(JSONObject()
                .put("courseName", "研究方法")
                .put("weekday", "Monday")
                .put("sections", JSONArray(listOf(3, 4, 5)))
                .put("weeks", JSONArray(listOf(1, 3, 5)))),
        ).toString()
        val capture = JSONObject()
            .put("pageText", "当前教学第1周")
            .put("captures", JSONArray().put(JSONObject().put("body", body)))
            .toString()

        val result = importer.parseDocument(JSONObject.quote(capture)) as ImportResult.Success

        assertEquals(3, result.snapshot.meetings.single().startPeriod)
        assertEquals(5, result.snapshot.meetings.single().endPeriod)
        assertEquals(setOf(1, 3, 5), result.snapshot.meetings.single().weeks)
    }

    @Test
    fun importsNormalizedPortalMeetingsAndKeepsPortalTermLength() {
        val page = JSONObject()
            .put("pageText", "当前第1周")
            .put("semesterName", "2026-2027学年第一学期")
            .put("firstMonday", "2026-09-07")
            .put("totalWeeks", 19)
            .put(
                "portalMeetings",
                JSONArray().put(
                    JSONObject()
                        .put("courseName", "门户接口测试课程")
                        .put("teacher", "测试教师")
                        .put("classroom", "测试教室")
                        .put("dayOfWeek", 4)
                        .put("startPeriod", 11)
                        .put("endPeriod", 13)
                        .put("weeks", JSONArray(listOf(1, 2, 4))),
                ),
            )
            .toString()

        val result = importer.parseDocument(JSONObject.quote(page)) as ImportResult.Success

        assertEquals("2026-2027学年第一学期", result.snapshot.semester.name)
        assertEquals(LocalDate.of(2026, 9, 7), result.snapshot.semester.firstMonday)
        assertEquals(19, result.snapshot.semester.totalWeeks)
        assertEquals(4, result.snapshot.meetings.single().dayOfWeek)
        assertEquals(setOf(1, 2, 4), result.snapshot.meetings.single().weeks)
    }
}
