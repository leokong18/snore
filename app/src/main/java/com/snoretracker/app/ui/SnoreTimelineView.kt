package com.snoretracker.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.snoretracker.app.data.SnoreEvent

class SnoreTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var nightStart: Long = 0
    private var nightEnd: Long = 1
    private var events: List<SnoreEvent> = emptyList()

    private val trackPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }
    private val eventPaint = Paint().apply {
        color = Color.parseColor("#FF7043")
        style = Paint.Style.FILL
    }

    fun setData(nightStart: Long, nightEnd: Long, events: List<SnoreEvent>) {
        this.nightStart = nightStart
        this.nightEnd = if (nightEnd > nightStart) nightEnd else nightStart + 1
        this.events = events
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // background track
        canvas.drawRoundRect(0f, h * 0.35f, w, h * 0.65f, h * 0.1f, h * 0.1f, trackPaint)

        val totalSpan = (nightEnd - nightStart).toFloat().coerceAtLeast(1f)
        for (event in events) {
            val startFrac = ((event.startTime - nightStart) / totalSpan).coerceIn(0f, 1f)
            val endFrac = ((event.endTime - nightStart) / totalSpan).coerceIn(0f, 1f)
            val left = w * startFrac
            var right = w * endFrac
            if (right - left < 3f) right = left + 3f // minimum visible width
            canvas.drawRoundRect(left, h * 0.2f, right, h * 0.8f, h * 0.08f, h * 0.08f, eventPaint)
        }
    }
}
