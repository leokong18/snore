package com.snoretracker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.snoretracker.app.data.NightRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class SnoreDetectionService : Service() {

    companion object {
        const val ACTION_START = "com.snoretracker.app.action.START"
        const val ACTION_STOP = "com.snoretracker.app.action.STOP"
        const val CHANNEL_ID = "snore_monitoring_channel"
        const val NOTIFICATION_ID = 1001
        const val SAMPLE_RATE = 16000
        const val TAG = "SnoreDetectionService"

        @Volatile var isRunning = false
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var repository: NightRepository
    private var nightId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        repository = NightRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> {
                if (!isRunning) startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, buildNotification())

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreTracker::MonitorLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L) // safety cap: 10 hours

        nightId = repository.startNight(System.currentTimeMillis())
        isRunning = true

        captureThread = Thread { captureLoop() }.apply { start() }
    }

    private fun stopMonitoring() {
        isRunning = false
        captureThread?.join(2000)
        captureThread = null

        if (nightId != -1L) {
            repository.endNight(nightId, System.currentTimeMillis())
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("MissingPermission")
    private fun captureLoop() {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, SAMPLE_RATE) // at least ~0.5-1s worth

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission", e)
            return
        }
        audioRecord = record

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return
        }
        record.startRecording()

        val analyzer = SnoreAnalyzer()
        val frameSamples = SAMPLE_RATE / 4 // ~0.25s frames
        val frame = ShortArray(frameSamples)

        // Pre-roll ring buffer so a saved clip includes ~1s before the snore was detected.
        val preRollFrames = 4
        val preRoll = ArrayDeque<ShortArray>()

        var inEvent = false
        var candidateStreak = 0
        var quietStreak = 0
        var eventStartTime = 0L
        var peakDb = 0.0
        var wavWriter: WavFileWriter? = null
        var clipFile: File? = null

        val clipsDir = File(getExternalFilesDir(null), "snore_clips").apply { mkdirs() }

        while (isRunning) {
            val read = record.read(frame, 0, frame.size)
            if (read <= 0) continue

            val result = analyzer.analyzeFrame(frame, read)

            if (!inEvent) {
                analyzer.updateNoiseFloor(result.rms)

                // keep a rolling pre-roll buffer of recent frames
                preRoll.addLast(frame.copyOf(read))
                if (preRoll.size > preRollFrames) preRoll.removeFirst()

                if (result.isCandidate) {
                    candidateStreak++
                } else {
                    candidateStreak = 0
                }

                if (candidateStreak >= analyzer.framesToStart) {
                    // Start a new snore event
                    inEvent = true
                    quietStreak = 0
                    eventStartTime = System.currentTimeMillis() -
                        (preRoll.size * frameSamples * 1000L / SAMPLE_RATE)
                    peakDb = analyzer.rmsToDb(result.rms)

                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                        .format(eventStartTime)
                    clipFile = File(clipsDir, "snore_$ts.wav")
                    wavWriter = WavFileWriter(clipFile!!, SAMPLE_RATE)
                    for (pre in preRoll) wavWriter?.writeSamples(pre, pre.size)
                    wavWriter?.writeSamples(frame, read)
                    preRoll.clear()
                }
            } else {
                wavWriter?.writeSamples(frame, read)
                val db = analyzer.rmsToDb(result.rms)
                if (db > peakDb) peakDb = db

                if (result.isCandidate) {
                    quietStreak = 0
                } else {
                    quietStreak++
                }

                if (quietStreak >= analyzer.framesToStop) {
                    // Close out this snore event
                    val eventEndTime = System.currentTimeMillis()
                    wavWriter?.finish()

                    val durationMs = eventEndTime - eventStartTime
                    if (durationMs >= 400) {
                        repository.insertSnoreEvent(
                            nightId, eventStartTime, eventEndTime, peakDb,
                            clipFile?.absolutePath
                        )
                    } else {
                        // too short, likely noise - discard the clip
                        clipFile?.delete()
                    }

                    inEvent = false
                    candidateStreak = 0
                    quietStreak = 0
                    wavWriter = null
                    clipFile = null
                }
            }
        }

        // If monitoring stopped mid-event, close it out gracefully.
        if (inEvent) {
            val eventEndTime = System.currentTimeMillis()
            wavWriter?.finish()
            val durationMs = eventEndTime - eventStartTime
            if (durationMs >= 400) {
                repository.insertSnoreEvent(nightId, eventStartTime, eventEndTime, peakDb, clipFile?.absolutePath)
            } else {
                clipFile?.delete()
            }
        }

        record.stop()
        record.release()
        audioRecord = null
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SnoreDetectionService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在监测打呼噜")
            .setContentText("整晚音频分析进行中，点击返回应用")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "停止监测", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "打呼噜监测", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监测服务运行时显示的常驻通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
