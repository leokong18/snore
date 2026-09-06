package com.snoretracker.app.data

import android.content.ContentValues
import android.content.Context

class NightRepository(context: Context) {

    private val helper = AppDatabaseHelper(context)

    fun startNight(startTime: Long): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("start_time", startTime)
            put("end_time", startTime)
            put("total_snore_ms", 0)
            put("snore_count", 0)
        }
        return db.insert("nights", null, values)
    }

    fun endNight(nightId: Long, endTime: Long) {
        val db = helper.writableDatabase
        val values = ContentValues().apply { put("end_time", endTime) }
        db.update("nights", values, "id = ?", arrayOf(nightId.toString()))
    }

    fun insertSnoreEvent(
        nightId: Long,
        startTime: Long,
        endTime: Long,
        peakDb: Double,
        audioPath: String?
    ) {
        val db = helper.writableDatabase
        val duration = endTime - startTime
        val values = ContentValues().apply {
            put("night_id", nightId)
            put("start_time", startTime)
            put("end_time", endTime)
            put("duration_ms", duration)
            put("peak_db", peakDb)
            put("audio_path", audioPath)
        }
        db.insert("snore_events", null, values)

        db.execSQL(
            "UPDATE nights SET total_snore_ms = total_snore_ms + ?, snore_count = snore_count + 1 WHERE id = ?",
            arrayOf(duration, nightId)
        )
    }

    fun getAllNights(): List<Night> {
        val db = helper.readableDatabase
        val nights = mutableListOf<Night>()
        val cursor = db.query(
            "nights", null, null, null, null, null, "start_time DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                nights.add(cursorToNight(it))
            }
        }
        return nights
    }

    fun getNight(nightId: Long): Night? {
        val db = helper.readableDatabase
        val cursor = db.query(
            "nights", null, "id = ?", arrayOf(nightId.toString()), null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) return cursorToNight(it)
        }
        return null
    }

    fun getEventsForNight(nightId: Long): List<SnoreEvent> {
        val db = helper.readableDatabase
        val events = mutableListOf<SnoreEvent>()
        val cursor = db.query(
            "snore_events", null, "night_id = ?", arrayOf(nightId.toString()),
            null, null, "start_time ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                events.add(
                    SnoreEvent(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        nightId = it.getLong(it.getColumnIndexOrThrow("night_id")),
                        startTime = it.getLong(it.getColumnIndexOrThrow("start_time")),
                        endTime = it.getLong(it.getColumnIndexOrThrow("end_time")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        peakDb = it.getDouble(it.getColumnIndexOrThrow("peak_db")),
                        audioPath = it.getString(it.getColumnIndexOrThrow("audio_path"))
                    )
                )
            }
        }
        return events
    }

    private fun cursorToNight(cursor: android.database.Cursor): Night {
        return Night(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow("start_time")),
            endTime = cursor.getLong(cursor.getColumnIndexOrThrow("end_time")),
            totalSnoreMs = cursor.getLong(cursor.getColumnIndexOrThrow("total_snore_ms")),
            snoreCount = cursor.getInt(cursor.getColumnIndexOrThrow("snore_count"))
        )
    }
}
