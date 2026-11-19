package com.divya.ai

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * v3.5 Emotions: Divya ke jazbaat screen par dikhte hain - smile / laugh / cry / love / kiss (flying kiss) / hurt (dard).
 * Voice tool `show_emotion(emotion)` se trigger hota hai. Sirf animation hai, touch rokta nahi.
 */
object EmotionBus {
    var kind by mutableStateOf("")
    var stamp by mutableStateOf(0L)
    fun trigger(k: String) { Handler(Looper.getMainLooper()).post { kind = k.lowercase().trim(); stamp = System.nanoTime() } }
}

@Composable
fun EmotionOverlay() {
    val k = EmotionBus.kind
    val st = EmotionBus.stamp
    if (k.isBlank()) return
    val prog = remember(st) { Animatable(0f) }
    LaunchedEffect(st) {
        prog.snapTo(0f)
        prog.animateTo(1f, tween(3400, easing = LinearEasing))
        EmotionBus.kind = ""
    }
    val main = when (k) { "cry" -> "😭"; "laugh" -> "😂"; "smile" -> "😊"; "love" -> "😍"; "kiss" -> "😘"; "hurt" -> "💔"; "hug" -> "🤗"; "worry" -> "🥺"; "blush" -> "🥰"; "proud" -> "🌟"; else -> "💜" }
    val bits = when (k) {
        "cry" -> listOf("💧", "😢"); "hurt" -> listOf("💧", "🥺")
        "laugh" -> listOf("😆", "✨", "🤣"); "smile" -> listOf("✨", "🌸")
        "kiss" -> listOf("💋", "❤️", "😘"); "love" -> listOf("💖", "❤️", "💞")
        "hug" -> listOf("💞", "🤍", "🤗"); "worry" -> listOf("💭", "💧"); "blush" -> listOf("💗", "🌸", "🥰"); "proud" -> listOf("✨", "👏", "🌟")
        else -> listOf("💜")
    }
    val falling = k == "cry" || k == "hurt" || k == "worry"
    val p = prog.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight
        for (i in 0 until 16) {
            val p2 = (p + i * 0.061f) % 1f
            val x = W * (((i * 0.137f) + 0.04f) % 0.92f)
            val y = if (falling) H * (p2 * 1.1f - 0.1f) else H * (1.05f - p2 * 1.1f)
            Text(bits[i % bits.size], fontSize = (22 + (i % 3) * 6).sp, modifier = Modifier.offset(x, y).alpha(sin(PI * p2).toFloat().coerceIn(0f, 1f)))
        }
        val s = 0.5f + 0.9f * sin(PI * p).toFloat().coerceIn(0f, 1f)
        // kiss: neeche se uthkar beech mein aata hai; baaki: beech mein pulse
        val my = if (k == "kiss") H * (0.62f - 0.22f * p) else H * 0.38f
        Text(main, fontSize = 84.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = my).scale(s).alpha((1f - (p - 0.8f).coerceAtLeast(0f) * 5f).coerceIn(0f, 1f)))
    }
}
