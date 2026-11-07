package com.divya.ai

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background mein chal rahe kaam (website / project / Termux code). Divya inke chalte waqt baat karti rehti hai;
 * screen par "Coding..." chip aur halka code-rain (jo code abhi likha ja raha hai) dikhta hai - Maya jaisa.
 */
object BgTasks {
    class T(val id: Int, val title: String) {
        var stage by mutableStateOf("Shuru…")
        var done by mutableStateOf(false)
        @Volatile var cancelled = false
    }
    val tasks = mutableStateListOf<T>()
    var code by mutableStateOf("")
    private val ids = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current: T? = null

    fun start(title: String): T {
        val t = T(ids.incrementAndGet(), title)
        current = t
        main.post { tasks.add(t) }
        return t
    }

    /** Abhi chal rahe kaam ka stage badlo (koi thread se bula sakte ho; koi task na ho to kuch nahi hota). */
    fun stage(s: String) { current?.takeIf { !it.done }?.stage = s }

    /** Jo code abhi likha gaya use code-rain mein dikhao. */
    fun feed(content: String) { if (current?.done == false) code = content.take(2500) }

    fun finish(t: T) {
        t.done = true
        main.postDelayed({ tasks.remove(t); if (tasks.none { !it.done }) code = "" }, 1200)
    }

    fun active() = tasks.any { !it.done }

    /** User ne chip par X dabaya / "cancel" bola: sab chalte kaam ko cancel flag + chip hata do. */
    fun cancelAll() { tasks.filter { !it.done }.forEach { it.cancelled = true; finish(it) } }
    fun isCancelled(): Boolean = current?.cancelled == true
}

/** Poori screen par halka code-rain + upar chip. Touch nahi rokta. */
@Composable
fun BgOverlay() {
    val running = BgTasks.tasks.filter { !it.done }
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    if (running.isEmpty()) return
    val t = running.last()
    val tr = rememberInfiniteTransition(label = "bg")
    val y by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "y")
    Box(Modifier.fillMaxSize()) {
        val snippet = BgTasks.code.ifBlank { "<!-- Divya coding… -->\n// planning…\n// writing files…" }
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Text(snippet.lines().take(60).joinToString("\n"), color = Color(0xFF39FF88).copy(alpha = 0.28f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp).offset(y = (260 - 520 * y).dp))
        }
        val shape = RoundedCornerShape(20.dp)
        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp).clip(shape).background(Color(0xE6031521))
            .border(1.2.dp, Color(0xFF19E3F0), shape).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF19E3F0), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("⚡ ${t.title.take(28)} · ${t.stage}" + if (running.size > 1) "  (+${running.size - 1})" else "", color = Color.White, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Text("✕", color = Color(0xFFFF6B6B), fontSize = 15.sp, modifier = Modifier.clickable {
                BgTasks.cancelAll(); PhoneTools.cancelInstall(appCtx)
            })
        }
    }
}
