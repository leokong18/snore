package com.snoretracker.app.ui

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.snoretracker.app.R
import com.snoretracker.app.data.SnoreEvent
import java.text.SimpleDateFormat
import java.util.Locale

class SnoreEventAdapter(
    private val events: List<SnoreEvent>
) : RecyclerView.Adapter<SnoreEventAdapter.EventViewHolder>() {

    private var currentPlayer: MediaPlayer? = null
    private var currentlyPlayingId: Long? = null

    class EventViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.eventTimeText)
        val durationText: TextView = view.findViewById(R.id.eventDurationText)
        val playButton: Button = view.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snore_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        holder.timeText.text = timeFormat.format(event.startTime)
        holder.durationText.text = "持续 ${event.durationMs / 1000}秒 · 峰值 ${"%.0f".format(event.peakDb)}dB"

        holder.playButton.text = if (currentlyPlayingId == event.id) "停止" else "播放"
        holder.playButton.isEnabled = event.audioPath != null

        holder.playButton.setOnClickListener {
            if (currentlyPlayingId == event.id) {
                stopPlayback()
                notifyItemChanged(position)
            } else {
                stopPlayback()
                val path = event.audioPath ?: return@setOnClickListener
                try {
                    currentPlayer = MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                        setOnCompletionListener {
                            currentlyPlayingId = null
                            notifyItemChanged(position)
                        }
                        start()
                    }
                    currentlyPlayingId = event.id
                } catch (e: Exception) {
                    currentlyPlayingId = null
                }
                notifyItemChanged(position)
            }
        }
    }

    private fun stopPlayback() {
        currentPlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        currentPlayer = null
        currentlyPlayingId = null
    }

    fun releasePlayer() {
        stopPlayback()
    }

    override fun getItemCount(): Int = events.size
}
