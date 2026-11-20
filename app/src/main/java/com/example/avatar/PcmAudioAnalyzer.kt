package com.example.avatar

import kotlin.math.abs
import kotlin.math.sqrt

data class DecodedPcmAudio(
    val sampleRate: Int,
    val channelCount: Int,
    val samples: ShortArray,
    val durationMs: Long
)

data class PcmAmplitudeEnvelope(
    val windowMs: Int,
    val amplitudes: FloatArray,
    val durationMs: Long
) {
    fun amplitudeAt(positionMs: Long): Float {
        if (amplitudes.isEmpty() || positionMs < 0L) return 0f
        val index = (positionMs / windowMs).toInt().coerceIn(0, amplitudes.lastIndex)
        return amplitudes[index]
    }
}

/**
 * Parses WAV PCM files produced by Android TextToSpeech.synthesizeToFile and builds a
 * smoothed RMS amplitude envelope indexed by playback position in milliseconds.
 */
object PcmAudioAnalyzer {

    fun parseWav(bytes: ByteArray): DecodedPcmAudio? {
        if (bytes.size < 44) return null
        if (readAscii(bytes, 0, 4) != "RIFF" || readAscii(bytes, 8, 4) != "WAVE") {
            return null
        }

        var audioFormat = 1
        var channelCount = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLength = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = readAscii(bytes, offset, 4)
            val chunkSize = readIntLE(bytes, offset + 4)
            val chunkDataStart = offset + 8
            if (chunkSize < 0 || chunkDataStart + chunkSize > bytes.size) {
                break
            }
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16) {
                        audioFormat = readShortLE(bytes, chunkDataStart)
                        channelCount = readShortLE(bytes, chunkDataStart + 2).coerceAtLeast(1)
                        sampleRate = readIntLE(bytes, chunkDataStart + 4).coerceAtLeast(8000)
                        bitsPerSample = readShortLE(bytes, chunkDataStart + 14)
                    }
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataLength = chunkSize
                    break
                }
            }
            // RIFF chunks are padded to even byte boundaries
            val paddedSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
            offset = chunkDataStart + paddedSize
        }

        if (audioFormat != 1 || bitsPerSample != 16 || dataOffset < 0 || dataLength < 2) {
            return null
        }

        val sampleCount = dataLength / 2
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val b0 = bytes[dataOffset + i * 2].toInt() and 0xFF
            val b1 = bytes[dataOffset + i * 2 + 1].toInt()
            samples[i] = ((b1 shl 8) or b0).toShort()
        }

        val frames = sampleCount / channelCount
        val durationMs = (frames * 1000L) / sampleRate
        return DecodedPcmAudio(
            sampleRate = sampleRate,
            channelCount = channelCount,
            samples = samples,
            durationMs = durationMs
        )
    }

    fun buildEnvelope(
        samples: ShortArray,
        sampleRate: Int,
        channelCount: Int = 1,
        windowMs: Int = 40
    ): PcmAmplitudeEnvelope {
        if (samples.isEmpty() || sampleRate <= 0) {
            return PcmAmplitudeEnvelope(windowMs, FloatArray(0), 0L)
        }
        val channels = channelCount.coerceAtLeast(1)
        val totalFrames = samples.size / channels
        val durationMs = (totalFrames * 1000L) / sampleRate
        val framesPerWindow = ((sampleRate * windowMs) / 1000).coerceAtLeast(1)
        val windowCount = ((totalFrames + framesPerWindow - 1) / framesPerWindow).coerceAtLeast(1)
        val rawRms = FloatArray(windowCount)

        var maxRms = 0f
        for (w in 0 until windowCount) {
            val startFrame = w * framesPerWindow
            val endFrame = minOf(startFrame + framesPerWindow, totalFrames)
            var sumSq = 0.0
            var count = 0
            for (f in startFrame until endFrame) {
                var mono = 0f
                for (c in 0 until channels) {
                    mono += samples[f * channels + c].toFloat() / 32768f
                }
                mono /= channels
                sumSq += (mono * mono).toDouble()
                count++
            }
            val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
            rawRms[w] = rms
            if (rms > maxRms) maxRms = rms
        }

        // Normalize gently against speech peak while preserving silence (< 0.012 RMS)
        val normScale = if (maxRms > 0.04f) (1f / maxRms).coerceAtMost(7.5f) else 4.0f
        val smoothed = FloatArray(windowCount)
        var prev = 0f
        for (i in 0 until windowCount) {
            val gated = if (rawRms[i] < 0.012f) 0f else (rawRms[i] * normScale).coerceIn(0f, 1f)
            // Slight smoothing across adjacent windows to prevent jittery lip snaps
            val nextVal = if (gated > prev) {
                prev + (gated - prev) * 0.72f
            } else {
                prev + (gated - prev) * 0.45f
            }
            smoothed[i] = if (abs(nextVal) < 0.02f) 0f else nextVal
            prev = smoothed[i]
        }

        return PcmAmplitudeEnvelope(
            windowMs = windowMs,
            amplitudes = smoothed,
            durationMs = durationMs
        )
    }

    fun computeChunkWindowAmplitudes(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channelCount: Int = 1,
        windowMs: Int = 40
    ): FloatArray {
        val channels = channelCount.coerceAtLeast(1)
        val totalSamples = pcmBytes.size / 2
        val totalFrames = totalSamples / channels
        if (totalFrames <= 0 || sampleRate <= 0) return FloatArray(0)

        val framesPerWindow = ((sampleRate * windowMs) / 1000).coerceAtLeast(1)
        val windowCount = ((totalFrames + framesPerWindow - 1) / framesPerWindow).coerceAtLeast(1)
        val out = FloatArray(windowCount)

        for (w in 0 until windowCount) {
            val startFrame = w * framesPerWindow
            val endFrame = minOf(startFrame + framesPerWindow, totalFrames)
            var sumSq = 0.0
            var count = 0
            for (f in startFrame until endFrame) {
                var mono = 0f
                for (c in 0 until channels) {
                    val sampleIdx = (f * channels + c) * 2
                    val b0 = pcmBytes[sampleIdx].toInt() and 0xFF
                    val b1 = pcmBytes[sampleIdx + 1].toInt()
                    val sample = ((b1 shl 8) or b0).toShort()
                    mono += sample.toFloat() / 32768f
                }
                mono /= channels
                sumSq += (mono * mono).toDouble()
                count++
            }
            val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
            // Gate low background noise floor and map conversational speech RMS cleanly to 0..1
            out[w] = if (rms < 0.014f) 0f else (rms * 4.2f).coerceIn(0f, 1f)
        }
        return out
    }

    fun parsePcmSampleRate(mimeType: String?, defaultRate: Int = 24_000): Int {
        if (mimeType.isNullOrBlank()) return defaultRate
        val match = Regex("rate=(\\d+)").find(mimeType) ?: return defaultRate
        return match.groupValues[1].toIntOrNull()?.coerceIn(8_000, 48_000) ?: defaultRate
    }

    private fun readAscii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
