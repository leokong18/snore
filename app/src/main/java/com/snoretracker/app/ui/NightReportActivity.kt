package com.snoretracker.app.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snoretracker.app.R
import com.snoretracker.app.data.NightRepository
import java.util.concurrent.TimeUnit

class NightReportActivity : AppCompatActivity() {

    private var eventAdapter: SnoreEventAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night_report)

        val nightId = intent.getLongExtra("night_id", -1L)
        val repository = NightRepository(this)
        val night = repository.getNight(nightId)
        val events = repository.getEventsForNight(nightId)

        if (night == null) {
            finish()
            return
        }

        val summaryText: TextView = findViewById(R.id.reportSummaryText)
        val timelineView: SnoreTimelineView = findViewById(R.id.timelineView)
        val recyclerView: RecyclerView = findViewById(R.id.eventsRecyclerView)

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(night.endTime - night.startTime)
        val snoreMinutes = TimeUnit.MILLISECONDS.toMinutes(night.totalSnoreMs)
        val percent = if (totalMinutes > 0) (snoreMinutes * 100 / totalMinutes) else 0

        summaryText.text = "总监测时长：${totalMinutes} 分钟\n" +
            "打呼噜次数：${night.snoreCount} 次\n" +
            "打呼噜总时长：${snoreMinutes} 分钟（约占 ${percent}%）"

        timelineView.setData(night.startTime, night.endTime, events)

        recyclerView.layoutManager = LinearLayoutManager(this)
        eventAdapter = SnoreEventAdapter(events)
        recyclerView.adapter = eventAdapter
    }

    override fun onDestroy() {
        super.onDestroy()
        eventAdapter?.releasePlayer()
    }
}
