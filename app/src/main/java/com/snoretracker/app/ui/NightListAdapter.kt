package com.snoretracker.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.snoretracker.app.R
import com.snoretracker.app.data.Night
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NightListAdapter(
    private val nights: List<Night>,
    private val onClick: (Night) -> Unit
) : RecyclerView.Adapter<NightListAdapter.NightViewHolder>() {

    class NightViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.dateText)
        val summaryText: TextView = view.findViewById(R.id.summaryText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NightViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_night, parent, false)
        return NightViewHolder(view)
    }

    override fun onBindViewHolder(holder: NightViewHolder, position: Int) {
        val night = nights[position]
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
        holder.dateText.text = dateFormat.format(night.startTime)

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(night.endTime - night.startTime)
        val snoreMinutes = TimeUnit.MILLISECONDS.toMinutes(night.totalSnoreMs)
        holder.summaryText.text =
            "监测时长 ${totalMinutes} 分钟 · 打呼 ${night.snoreCount} 次 · 共 ${snoreMinutes} 分钟"

        holder.itemView.setOnClickListener { onClick(night) }
    }

    override fun getItemCount(): Int = nights.size
}
