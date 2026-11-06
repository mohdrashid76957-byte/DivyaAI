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
    var shape by mutableStateOf(0f)         // 0 = gol/khula (aa, oo), 1 = chaudha/patla (ee, s, sh) - awaaz ke zero-crossing se andaza
    var speaking by mutableStateOf(false)   // Divya abhi bol rahi hai (chhote pauses mein bhi true rehta hai)

    // v2.22.10: HUD orb ke liye (compose state nahi, sirf draw loop padhta hai)
    @Volatile var micLevel = 0f      // meri (user ki) awaaz ki loudness 0..1, mic chalte waqt
    @Volatile var caption = ""       // jo abhi suna/bola ja raha hai (orb ke neeche pill mein dikhta hai)
    @Volatile var captionAt = 0L
    fun say(t: String) { caption = t; captionAt = android.os.SystemClock.uptimeMillis() }

    fun moveTo(s: Int) { if (stage != s) stage = s }

    /** 16-bit PCM chunk ki loudness (RMS) 0..1. Har 4th sample le kar tez rakha hai. */
    fun pcmLevel(b: ByteArray, len: Int = b.size): Float {
        var sum = 0.0; var n = 0; var i = 0
        val end = minOf(len, b.size)
        while (i + 1 < end) {
            val v = (b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)
            sum += v.toDouble() * v; n++; i += 8
        }
        if (n == 0) return 0f
        val rms = Math.sqrt(sum / n) / 32768.0
        return (rms * 6.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** Zero-crossing rate -> 0..1. Zyada = s/sh/ee jaisi tez awaaz (mooh chaudha), kam = aa/oo jaisi gehri awaaz. Andaza hai, asli phoneme nahi. */
    fun pcmShape(b: ByteArray): Float {
        var z = 0; var n = 0; var prev = 0; var i = 0
        while (i + 1 < b.size) {
            val v = (b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)
            if (n > 0 && ((v >= 0) != (prev >= 0))) z++
            prev = v; n++; i += 2
        }
        if (n < 8) return 0f
        return (((z.toFloat() / n) - 0.05f) / 0.20f).coerceIn(0f, 1f)
    }
}
