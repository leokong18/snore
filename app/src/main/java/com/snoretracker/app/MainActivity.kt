package com.snoretracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snoretracker.app.data.NightRepository
import com.snoretracker.app.ui.NightListAdapter
import com.snoretracker.app.ui.NightReportActivity

class MainActivity : AppCompatActivity() {

    private lateinit var repository: NightRepository
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            actuallyStartMonitoring()
        } else {
            statusText.text = "需要麦克风和通知权限才能监测打呼噜"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = NightRepository(this)
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)

        val recyclerView: RecyclerView = findViewById(R.id.nightsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        updateButtonState()

        toggleButton.setOnClickListener {
            if (SnoreDetectionService.isRunning) {
                stopMonitoring()
            } else {
                requestPermissionsAndStart()
            }
        }

        recyclerView.adapter = NightListAdapter(repository.getAllNights()) { night ->
            val intent = Intent(this, NightReportActivity::class.java)
            intent.putExtra("night_id", night.id)
            startActivity(intent)
        }
        this.recyclerView = recyclerView
    }

    private lateinit var recyclerView: RecyclerView

    override fun onResume() {
        super.onResume()
        updateButtonState()
        refreshList()
    }

    private fun refreshList() {
        recyclerView.adapter = NightListAdapter(repository.getAllNights()) { night ->
            val intent = Intent(this, NightReportActivity::class.java)
            intent.putExtra("night_id", night.id)
            startActivity(intent)
        }
    }

    private fun updateButtonState() {
        if (SnoreDetectionService.isRunning) {
            toggleButton.text = "停止监测"
            statusText.text = "监测中… 手机请放在床头，屏幕可关闭"
        } else {
            toggleButton.text = "开始监测"
            statusText.text = "准备就绪，睡前点击开始"
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            actuallyStartMonitoring()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun actuallyStartMonitoring() {
        val intent = Intent(this, SnoreDetectionService::class.java).apply {
            action = SnoreDetectionService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        updateButtonState()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, SnoreDetectionService::class.java).apply {
            action = SnoreDetectionService.ACTION_STOP
        }
        startService(intent)
        updateButtonState()
        recyclerView.postDelayed({ refreshList() }, 500)
    }
}
