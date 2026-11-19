package com.divya.ai

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * v3.26: har home layout (Ice, Cyber, ...) ki Divya photo ko wahi natural motion: saans, sway, bolte waqt nod + emphasis (asli awaaz se),
 * sunte waqt aage jhuk kar sir tirchha, sochte waqt sir tirchha + idhar-udhar nazar. Use: `val motion = rememberAvatarMotion()` phir `.graphicsLayer { motion() }`.
 */
@Composable
fun rememberAvatarMotion(): GraphicsLayerScope.() -> Unit {
    val tr = rememberInfiniteTransition(label = "avm")
    val breath by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val sway by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sw")
    val glance by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gl")
    val phase by tr.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "ph")
    val smooth by animateFloatAsState(AvatarBus.level, tween(70), label = "lv")
    return {
        val lv = smooth
        val spk = AvatarBus.speaking || lv > 0.02f
        val st = AvatarBus.stage
        transformOrigin = TransformOrigin(0.5f, 0.8f)
        var ty = breath * 2.dp.toPx(); var tx = sway * 3.dp.toPx(); var rz = sway * 0.6f; var sc = 1f + 0.006f * breath
        if (spk) {
            ty -= lv * 5.dp.toPx()
            rz += sin(phase) * 1.5f * (0.4f + lv) + sin(phase * 0.5f) * 0.8f
            tx += sin(phase * 0.5f) * 3.dp.toPx() * lv
            sc += 0.022f * lv
        } else if (st == 1 || st == 2) { rz += -2.5f; sc += 0.015f; ty -= 3.dp.toPx() }
        else if (st == 3 || st == 4) { rz += 2.2f + glance * 1.2f; tx += glance * 5.dp.toPx() }
        translationX = tx; translationY = ty; rotationZ = rz; scaleX = sc; scaleY = sc
    }
}
