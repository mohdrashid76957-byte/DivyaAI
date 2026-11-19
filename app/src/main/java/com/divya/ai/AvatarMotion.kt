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
        val spk = AvatarBus.speaking || smooth > 0.02f
        // bolte waqt awaaz halki ho tab bhi jeevant dikhe: asli loudness + chalta hua natural envelope
        val lv = if (spk) maxOf(smooth, 0.40f + 0.30f * sin(phase * 2.3f) * sin(phase * 0.7f + 1f)).coerceIn(0f, 1f) else 0f
        val st = AvatarBus.stage
        transformOrigin = TransformOrigin(0.5f, 0.8f)
        cameraDistance = 14f * density
        var ty = breath * 5.dp.toPx(); var tx = sway * 8.dp.toPx(); var rz = sway * 1.2f; var sc = 1f + 0.012f * breath; var ry = sway * 2f
        if (spk) {
            ty -= lv * 9.dp.toPx() * (0.6f + 0.4f * sin(phase * 2f))
            rz += sin(phase) * 3.5f * (0.5f + lv) + sin(phase * 0.5f) * 1.6f
            tx += sin(phase * 0.5f) * 7.dp.toPx()
            ry += sin(phase * 0.5f + 0.8f) * 7f          // sir idhar-udhar ghoomta hai
            sc += 0.035f * lv
        } else if (st == 1 || st == 2) { rz += -4f; sc += 0.03f; ty -= 6.dp.toPx(); ry += 4f }          // sun rahi hai: aage jhuk kar sir tirchha
        else if (st == 3 || st == 4) { rz += 4f + glance * 2f; tx += glance * 9.dp.toPx(); ry += glance * 8f } // soch rahi hai: nazar idhar-udhar
        translationX = tx; translationY = ty; rotationZ = rz; rotationY = ry; scaleX = sc; scaleY = sc
    }
}
