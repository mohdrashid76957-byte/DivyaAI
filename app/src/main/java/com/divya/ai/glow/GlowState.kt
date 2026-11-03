package com.divya.ai.glow

/** Divya ki halat: glow ka rang/speed isse badalta hai. */
enum class GlowState(val argb: Long, val periodMs: Int) {
    IDLE(0xFF2979FF, 3000),      // blue, slow
    LISTENING(0xFF00FF40, 1200), // green neon (reel jaisa)
    THINKING(0xFFB388FF, 800),   // purple, fast
    SPEAKING(0xFF00E5FF, 600)    // cyan, fast pulse
}
