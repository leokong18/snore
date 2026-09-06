package com.snoretracker.app

import java.io.File
import java.io.RandomAccessFile

/**
 * Writes 16-bit mono PCM samples into a valid .wav file.
 * Header is written as a placeholder first, then patched with real sizes on finish().
 */
class WavFileWriter(private val file: File, private val sampleRate: Int) {

    private val raf = RandomAccessFile(file, "rw")
    private var dataLength = 0
    private var closed = false

    init {
        raf.setLength(0)
        writeHeader(0)
    }

    private fun writeHeader(dataSize: Int) {
        raf.seek(0)
        val byteRate = sampleRate * 2 // mono, 16-bit
        val totalDataLen = dataSize + 36

        val header = ByteArray(44)
        // RIFF chunk
        "RIFF".toByteArray().copyInto(header, 0)
        writeIntLE(header, 4, totalDataLen)
        "WAVE".toByteArray().copyInto(header, 8)
        // fmt sub-chunk
        "fmt ".toByteArray().copyInto(header, 12)
        writeIntLE(header, 16, 16) // sub-chunk size
        writeShortLE(header, 20, 1) // PCM format
        writeShortLE(header, 22, 1) // mono channel
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, 2) // block align
        writeShortLE(header, 34, 16) // bits per sample
        // data sub-chunk
        "data".toByteArray().copyInto(header, 36)
        writeIntLE(header, 40, dataSize)

        raf.write(header)
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xff).toByte()
        buf[offset + 1] = ((value shr 8) and 0xff).toByte()
        buf[offset + 2] = ((value shr 16) and 0xff).toByte()
        buf[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xff).toByte()
        buf[offset + 1] = ((value shr 8) and 0xff).toByte()
    }

    @Synchronized
    fun writeSamples(buffer: ShortArray, len: Int) {
        if (closed) return
        raf.seek(44L + dataLength)
        val bytes = ByteArray(len * 2)
        for (i in 0 until len) {
            val s = buffer[i].toInt()
            bytes[i * 2] = (s and 0xff).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
        }
        raf.write(bytes)
        dataLength += len * 2
    }

    @Synchronized
    fun finish() {
        if (closed) return
        writeHeader(dataLength)
        raf.close()
        closed = true
    }

    fun discardAndDelete() {
        if (!closed) {
            try { raf.close() } catch (_: Exception) {}
            closed = true
        }
        file.delete()
    }
}
