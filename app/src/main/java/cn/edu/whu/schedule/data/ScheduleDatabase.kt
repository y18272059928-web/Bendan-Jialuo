package cn.edu.whu.schedule.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE semester(
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                first_monday TEXT NOT NULL,
                total_weeks INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE course(
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                teacher TEXT NOT NULL,
                color_argb INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                marker TEXT NOT NULL DEFAULT 'NORMAL'
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE meeting(
                id INTEGER PRIMARY KEY,
                course_id INTEGER NOT NULL,
                room TEXT NOT NULL,
                day_of_week INTEGER NOT NULL,
                start_period INTEGER NOT NULL,
                end_period INTEGER NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                weeks TEXT NOT NULL,
                FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX meeting_course_id ON meeting(course_id)")
        createPersonalTables(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE course ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE course ADD COLUMN marker TEXT NOT NULL DEFAULT 'NORMAL'")
        }
        if (oldVersion < 3) createPersonalTables(db)
    }

    fun hasSchedule(): Boolean = readableDatabase.rawQuery(
        "SELECT EXISTS(SELECT 1 FROM semester LIMIT 1)",
        null,
    ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

    fun read(): ScheduleSnapshot {
        val db = readableDatabase
        val semester = db.rawQuery(
            "SELECT id,name,first_monday,total_weeks FROM semester LIMIT 1",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "尚未设置学期" }
            Semester(
                id = cursor.getLong(0),
                name = cursor.getString(1),
                firstMonday = LocalDate.parse(cursor.getString(2)),
                totalWeeks = cursor.getInt(3),
            )
        }
        val courses = db.rawQuery(
            "SELECT id,name,teacher,color_argb,note,marker FROM course ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Course(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            teacher = cursor.getString(2),
                            colorArgb = cursor.getLong(3),
                            note = cursor.getString(4),
                            marker = CourseMarker.fromStorage(cursor.getString(5)),
                        ),
                    )
                }
            }
        }
        val meetings = db.rawQuery(
            """SELECT id,course_id,room,day_of_week,start_period,end_period,
                start_time,end_time,weeks FROM meeting ORDER BY day_of_week,start_time""".trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CourseMeeting(
                            id = cursor.getLong(0),
                            courseId = cursor.getLong(1),
                            room = cursor.getString(2),
                            dayOfWeek = cursor.getInt(3),
                            startPeriod = cursor.getInt(4),
                            endPeriod = cursor.getInt(5),
                            startTime = LocalTime.parse(cursor.getString(6)),
                            endTime = LocalTime.parse(cursor.getString(7)),
                            weeks = decodeWeeks(cursor.getString(8)),
                        ),
                    )
                }
            }
        }
        val exams = db.rawQuery(
            "SELECT id,course_id,title,exam_date,start_time,end_time,room,seat,note FROM exam ORDER BY exam_date,start_time",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Exam(
                            id = cursor.getLong(0),
                            courseId = cursor.getNullableLong(1),
                            title = cursor.getString(2),
                            date = LocalDate.parse(cursor.getString(3)),
                            startTime = cursor.getNullableString(4)?.let(LocalTime::parse),
                            endTime = cursor.getNullableString(5)?.let(LocalTime::parse),
                            room = cursor.getString(6),
                            seat = cursor.getString(7),
                            note = cursor.getString(8),
                        ),
                    )
                }
            }
        }
        val tasks = db.rawQuery(
            "SELECT id,course_id,title,due_at,note,completed,reminder_minutes FROM study_task ORDER BY completed,due_at",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StudyTask(
                            id = cursor.getLong(0),
                            courseId = cursor.getNullableLong(1),
                            title = cursor.getString(2),
                            dueAt = LocalDateTime.parse(cursor.getString(3)),
                            note = cursor.getString(4),
                            completed = cursor.getInt(5) != 0,
                            reminderMinutes = cursor.getInt(6),
                        ),
                    )
                }
            }
        }
        val noClassDates = db.rawQuery(
            "SELECT id,date,name FROM no_class_date ORDER BY date",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(NoClassDate(cursor.getLong(0), LocalDate.parse(cursor.getString(1)), cursor.getString(2)))
                }
            }
        }
        return ScheduleSnapshot(semester, courses, meetings, exams, tasks, noClassDates)
    }

    fun replace(snapshot: ScheduleSnapshot) {
        writableDatabase.transaction {
            delete("exam", null, null)
            delete("study_task", null, null)
            delete("no_class_date", null, null)
            delete("meeting", null, null)
            delete("course", null, null)
            delete("semester", null, null)

            insertOrThrow("semester", null, ContentValues().apply {
                put("id", snapshot.semester.id)
                put("name", snapshot.semester.name)
                put("first_monday", snapshot.semester.firstMonday.toString())
                put("total_weeks", snapshot.semester.totalWeeks)
            })
            snapshot.courses.forEach { course ->
                insertOrThrow("course", null, ContentValues().apply {
                    put("id", course.id)
                    put("name", course.name)
                    put("teacher", course.teacher)
                    put("color_argb", course.colorArgb)
                    put("note", course.note)
                    put("marker", course.marker.name)
                })
            }
            snapshot.meetings.forEach { meeting ->
                insertOrThrow("meeting", null, ContentValues().apply {
                    put("id", meeting.id)
                    put("course_id", meeting.courseId)
                    put("room", meeting.room)
                    put("day_of_week", meeting.dayOfWeek)
                    put("start_period", meeting.startPeriod)
                    put("end_period", meeting.endPeriod)
                    put("start_time", meeting.startTime.toString())
                    put("end_time", meeting.endTime.toString())
                    put("weeks", encodeWeeks(meeting.weeks))
                })
            }
            snapshot.exams.forEach { exam ->
                insertOrThrow("exam", null, ContentValues().apply {
                    putNullableLong("course_id", exam.courseId)
                    put("id", exam.id)
                    put("title", exam.title)
                    put("exam_date", exam.date.toString())
                    putNullableString("start_time", exam.startTime?.toString())
                    putNullableString("end_time", exam.endTime?.toString())
                    put("room", exam.room)
                    put("seat", exam.seat)
                    put("note", exam.note)
                })
            }
            snapshot.tasks.forEach { task ->
                insertOrThrow("study_task", null, ContentValues().apply {
                    putNullableLong("course_id", task.courseId)
                    put("id", task.id)
                    put("title", task.title)
                    put("due_at", task.dueAt.toString())
                    put("note", task.note)
                    put("completed", if (task.completed) 1 else 0)
                    put("reminder_minutes", task.reminderMinutes)
                })
            }
            snapshot.noClassDates.forEach { holiday ->
                insertOrThrow("no_class_date", null, ContentValues().apply {
                    put("id", holiday.id)
                    put("date", holiday.date.toString())
                    put("name", holiday.name)
                })
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "schedule.db"
        private const val DATABASE_VERSION = 3

        private fun createPersonalTables(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS exam(
                    id INTEGER PRIMARY KEY,
                    course_id INTEGER,
                    title TEXT NOT NULL,
                    exam_date TEXT NOT NULL,
                    start_time TEXT,
                    end_time TEXT,
                    room TEXT NOT NULL DEFAULT '',
                    seat TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE SET NULL
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS study_task(
                    id INTEGER PRIMARY KEY,
                    course_id INTEGER,
                    title TEXT NOT NULL,
                    due_at TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    completed INTEGER NOT NULL DEFAULT 0,
                    reminder_minutes INTEGER NOT NULL DEFAULT 60,
                    FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE SET NULL
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS no_class_date(
                    id INTEGER PRIMARY KEY,
                    date TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL
                )""".trimIndent(),
            )
        }

        fun encodeWeeks(weeks: Set<Int>): String = weeks.sorted().joinToString(",")
        fun decodeWeeks(value: String): Set<Int> = value.split(',')
            .mapNotNull(String::toIntOrNull)
            .filter { it > 0 }
            .toSet()
    }
}

private fun android.database.Cursor.getNullableLong(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

private fun android.database.Cursor.getNullableString(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun ContentValues.putNullableLong(key: String, value: Long?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullableString(key: String, value: String?) {
    if (value == null) putNull(key) else put(key, value)
}