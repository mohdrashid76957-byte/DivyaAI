package com.divya.ai.glow

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.divya.ai.MainActivity
import kotlin.math.abs

/** Divya ka avatar sab apps/home screen ke upar tairta hai. Drag karo, tap karo to Divya app khulta hai. */
object FloatingAvatar {
    private var view: FrameLayout? = null
    private var wm: WindowManager? = null

    fun show(svc: AccessibilityService) {
        if (view != null) return
        val dp = svc.resources.displayMetrics.density
        val img = ImageView(svc).apply {
            setImageResource(com.divya.ai.R.drawable.divya_full); scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val box = FrameLayout(svc).apply {
            addView(img, FrameLayout.LayoutParams(-1, -1))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 22f * dp) }
            }
            elevation = 12f * dp
        }
        val lp = WindowManager.LayoutParams(
            (104 * dp).toInt(), (149 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (20 * dp).toInt(); y = (300 * dp).toInt() }
        val manager = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        box.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    lp.x = ox + dx; lp.y = oy + dy
                    runCatching { manager.updateViewLayout(box, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                else -> false
            }
        }
        manager.addView(box, lp)
        wm = manager; view = box
    }

    fun hide() {
        view?.let { runCatching { wm?.removeView(it) } }
        view = null; wm = null
    }
}
