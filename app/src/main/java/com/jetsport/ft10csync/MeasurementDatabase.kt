package com.jetsport.ft10csync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jetsport.ft10csync.protocol.Ft10cStepRecord
import com.jetsport.ft10csync.data.StepHistoryMergePolicy

data class StepDayAggregate(val totalSteps: Int, val runSteps: Int, val walkSteps: Int)

data class MeasurementEntry(
    val id: Long,
    val type: String,
    val value1: Float,
    val value2: Float,
    val timestamp: Long
)

class MeasurementDatabase(context: Context) : SQLiteOpenHelper(context, "jetsport_measurements.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE measurements (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, value1 REAL NOT NULL, value2 REAL NOT NULL DEFAULT 0, measured_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_measurements_type_time ON measurements(type, measured_at DESC)")
        db.execSQL("CREATE TABLE step_hours (hour_start INTEGER PRIMARY KEY, day_key TEXT NOT NULL, steps INTEGER NOT NULL, run_start INTEGER NOT NULL DEFAULT 0, run_end INTEGER NOT NULL DEFAULT 0, run_duration INTEGER NOT NULL DEFAULT 0, run_steps INTEGER NOT NULL DEFAULT 0, walk_start INTEGER NOT NULL DEFAULT 0, walk_end INTEGER NOT NULL DEFAULT 0, walk_duration INTEGER NOT NULL DEFAULT 0, walk_steps INTEGER NOT NULL DEFAULT 0, uncategorized_steps INTEGER NOT NULL DEFAULT 0, raw_hex TEXT NOT NULL DEFAULT '', live_updated_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_step_hours_day ON step_hours(day_key)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS step_hours (hour_start INTEGER PRIMARY KEY, day_key TEXT NOT NULL, steps INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_step_hours_day ON step_hours(day_key)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE step_hours ADD COLUMN run_start INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN run_end INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN run_duration INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN run_steps INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN walk_start INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN walk_end INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN walk_duration INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN walk_steps INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN uncategorized_steps INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE step_hours ADD COLUMN raw_hex TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE step_hours ADD COLUMN live_updated_at INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun insert(type: String, value1: Float, value2: Float, timestamp: Long) {
        val values = ContentValues().apply {
            put("type", type)
            put("value1", value1)
            put("value2", value2)
            put("measured_at", timestamp)
        }
        writableDatabase.insert("measurements", null, values)
    }

    fun latest(type: String, limit: Int): List<MeasurementEntry> = query(
        "SELECT id, type, value1, value2, measured_at FROM measurements WHERE type=? ORDER BY measured_at DESC LIMIT ?",
        arrayOf(type, limit.toString())
    )

    fun latestAll(limit: Int): List<MeasurementEntry> = query(
        "SELECT id, type, value1, value2, measured_at FROM measurements ORDER BY measured_at DESC LIMIT ?",
        arrayOf(limit.toString())
    )


    /** Live B1 updates only the total, preserving detailed B2 run/walk fields for the same hour. */
    fun upsertStepHour(hourStart: Long, dayKey: String, steps: Int, liveUpdatedAtMs: Long) {
        val values = ContentValues().apply {
            put("hour_start", hourStart); put("day_key", dayKey); put("steps", steps)
            put("live_updated_at", liveUpdatedAtMs)
        }
        writableDatabase.insertWithOnConflict("step_hours", null, values, SQLiteDatabase.CONFLICT_IGNORE).let { inserted ->
            if (inserted == -1L) writableDatabase.execSQL(
                "UPDATE step_hours SET day_key=?, steps=?, live_updated_at=? WHERE hour_start=?",
                arrayOf(dayKey, steps, liveUpdatedAtMs, hourStart)
            )
        }
    }

    /** Atomically commits B2 while preserving a newer B1 total received after this request began. */
    fun commitStepHistory(records: List<Ft10cStepRecord>, historyRequestStartedAtMs: Long): Int {
        val db = writableDatabase
        var preservedLiveTotals = 0
        db.beginTransaction()
        try {
            records.forEach { r ->
                val cal = java.util.Calendar.getInstance().apply {
                    set(r.year, r.month - 1, r.day, r.hour, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val dayKey = "%04d-%02d-%02d".format(java.util.Locale.US, r.year, r.month, r.day)
                val existingLive = db.rawQuery(
                    "SELECT steps, live_updated_at FROM step_hours WHERE hour_start=?",
                    arrayOf(cal.timeInMillis.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getLong(1) else null
                }
                val preserveLive = existingLive != null && StepHistoryMergePolicy.shouldPreserveLiveTotal(
                    existingLive.second, historyRequestStartedAtMs
                )
                if (preserveLive) preservedLiveTotals++
                val values = ContentValues().apply {
                    put("hour_start", cal.timeInMillis); put("day_key", dayKey)
                    put("steps", if (preserveLive) existingLive!!.first else r.totalSteps)
                    put("live_updated_at", if (preserveLive) existingLive!!.second else 0L)
                    put("run_start", r.runStartMinute); put("run_end", r.runEndMinute)
                    put("run_duration", r.runDurationMinutes); put("run_steps", r.runSteps)
                    put("walk_start", r.walkStartMinute); put("walk_end", r.walkEndMinute)
                    put("walk_duration", r.walkDurationMinutes); put("walk_steps", r.walkSteps)
                    put("uncategorized_steps", r.uncategorizedSteps); put("raw_hex", r.rawHex)
                }
                db.insertWithOnConflict("step_hours", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return preservedLiveTotals
    }

    fun dailySteps(limitDays: Int = 14): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        readableDatabase.rawQuery("SELECT day_key, SUM(steps) FROM step_hours GROUP BY day_key ORDER BY day_key DESC LIMIT ?", arrayOf(limitDays.toString())).use { c ->
            while (c.moveToNext()) result += c.getString(0) to c.getInt(1)
        }
        return result.reversed()
    }

    fun stepsForDay(dayKey: String): Int = readableDatabase.rawQuery("SELECT COALESCE(SUM(steps),0) FROM step_hours WHERE day_key=?", arrayOf(dayKey)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun hasStepDataForDay(dayKey: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM step_hours WHERE day_key=? LIMIT 1", arrayOf(dayKey)
    ).use { it.moveToFirst() }

    fun stepAggregateForDay(dayKey: String): StepDayAggregate =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(steps),0), COALESCE(SUM(run_steps),0), COALESCE(SUM(walk_steps),0) FROM step_hours WHERE day_key=?",
            arrayOf(dayKey)
        ).use { c ->
            if (c.moveToFirst()) StepDayAggregate(c.getInt(0), c.getInt(1), c.getInt(2))
            else StepDayAggregate(0, 0, 0)
        }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM measurements", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun query(sql: String, args: Array<String>): List<MeasurementEntry> {
        val result = mutableListOf<MeasurementEntry>()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                result += MeasurementEntry(
                    cursor.getLong(0), cursor.getString(1), cursor.getFloat(2),
                    cursor.getFloat(3), cursor.getLong(4)
                )
            }
        }
        return result
    }
}
