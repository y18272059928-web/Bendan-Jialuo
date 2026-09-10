package cn.edu.whu.schedule.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import java.time.LocalDate
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
                color_argb INTEGER NOT NULL
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
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun hasSchedule(): Boolean = readableDatabase.rawQuery(
        "SELECT EXISTS(SELECT 1 FROM semester LIMIT 1)",
        null,
    ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

    fun read(): ScheduleSnapshot {
        val semester = readableDatabase.rawQuery(
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
        val courses = readableDatabase.rawQuery(
            "SELECT id,name,teacher,color_argb FROM course ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Course(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
                }
            }
        }
        val meetings = readableDatabase.rawQuery(
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
        return ScheduleSnapshot(semester, courses, meetings)
    }

    fun replace(snapshot: ScheduleSnapshot) {
        writableDatabase.transaction {
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
        }
    }

    companion object {
        private const val DATABASE_NAME = "schedule.db"
        private const val DATABASE_VERSION = 1

        fun encodeWeeks(weeks: Set<Int>): String = weeks.sorted().joinToString(",")
        fun decodeWeeks(value: String): Set<Int> = value.split(',')
            .mapNotNull(String::toIntOrNull)
            .filter { it > 0 }
            .toSet()
    }
}
