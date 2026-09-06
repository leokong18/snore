package com.snoretracker.app.data

data class Night(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val totalSnoreMs: Long,
    val snoreCount: Int
)

data class SnoreEvent(
    val id: Long,
    val nightId: Long,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val peakDb: Double,
    val audioPath: String?
)
