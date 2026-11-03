package com.divya.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Avatar ki halat. GeminiLive yahan likhta hai, avatar/NeonHome yahan se padhte hain.
 * stage: 0 idle, 1 Listening, 2 Understanding, 3 Processing, 4 Performing, 5 Completed
 * level: Divya ki asli awaaz ki loudness (0..1), avatar isi se hilta/chamakta hai.
 */
object AvatarBus {
    var stage by mutableStateOf(0)
    var level by mutableStateOf(0f)
    var speaking by mutableStateOf(false)   // Divya abhi bol rahi hai (chhote pauses mein bhi true rehta hai)

    fun moveTo(s: Int) { if (stage != s) stage = s }

    /** 16-bit PCM chunk ki loudness (RMS) 0..1. Har 4th sample le kar tez rakha hai. */
    fun pcmLevel(b: ByteArray): Float {
        var sum = 0.0; var n = 0; var i = 0
        while (i + 1 < b.size) {
            val v = (b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)
            sum += v.toDouble() * v; n++; i += 8
        }
        if (n == 0) return 0f
        val rms = Math.sqrt(sum / n) / 32768.0
        return (rms * 6.0).coerceIn(0.0, 1.0).toFloat()
    }
}
