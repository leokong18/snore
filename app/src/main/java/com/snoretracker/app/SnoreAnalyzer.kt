package com.snoretracker.app

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Frame-level audio analysis used to decide whether a short chunk of audio
 * looks like snoring: louder than the recent ambient (room) noise floor,
 * and dominated by low-frequency rumble rather than sharp/high-frequency
 * sound such as speech consonants or bed rustling (approximated with the
 * zero-crossing rate).
 */
class SnoreAnalyzer {

    // Adaptive ambient noise floor, in RMS amplitude units (16-bit PCM scale).
    var noiseFloor: Double = 150.0
        private set

    // How many "loud + low-frequency" frames in a row are needed to open an event.
    val framesToStart = 2
    // How many "quiet or high-frequency" frames in a row are needed to close an event.
    val framesToStop = 4

    // Multiplier over the noise floor a frame's RMS must exceed to count as "loud".
    private val loudMultiplier = 2.2
    // Zero-crossing rate (crossings per sample) above which a frame is treated as
    // "too high-frequency" to be a snore (more typical of speech/hiss).
    private val zcrThreshold = 0.12
    private val adaptRate = 0.05

    data class FrameResult(val rms: Double, val zcr: Double, val isCandidate: Boolean)

    fun analyzeFrame(buffer: ShortArray, len: Int): FrameResult {
        val rms = computeRms(buffer, len)
        val zcr = computeZcr(buffer, len)
        val isCandidate = rms > noiseFloor * loudMultiplier && zcr < zcrThreshold
        return FrameResult(rms, zcr, isCandidate)
    }

    /** Only call this while NOT inside a snore event, to slowly track room noise. */
    fun updateNoiseFloor(rms: Double) {
        if (rms < noiseFloor * 3.0) { // ignore huge spikes so one loud noise doesn't skew it
            noiseFloor = noiseFloor * (1 - adaptRate) + rms * adaptRate
            if (noiseFloor < 30.0) noiseFloor = 30.0
        }
    }

    private fun computeRms(buffer: ShortArray, len: Int): Double {
        var sum = 0.0
        for (i in 0 until len) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / len)
    }

    private fun computeZcr(buffer: ShortArray, len: Int): Double {
        var crossings = 0
        for (i in 1 until len) {
            if ((buffer[i - 1] >= 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / len
    }

    /** Rough loudness in dB relative to a fixed reference, for display/report purposes. */
    fun rmsToDb(rms: Double): Double {
        val ref = 1.0
        val ratio = (abs(rms) + 1.0) / ref
        return 20 * kotlin.math.log10(ratio)
    }
}
