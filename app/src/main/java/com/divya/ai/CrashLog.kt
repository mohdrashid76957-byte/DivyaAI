package com.divya.ai

import android.content.Context
import java.io.File

/**
 * Crash logger: app girne par asli karan (stack trace) file mein save hota hai aur agli baar app kholne par dikhta hai.
 * Isse "start karte hi band ho jati hai" jaise bug ka exact line pata chal jata hai (logcat/USB ke bina).
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { note(app, "thread ${t.name}", e) } catch (_: Throwable) {}
            old?.uncaughtException(t, e)
        }
    }

    fun note(c: Context, where: String, e: Throwable) {
        try {
            val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
            val head = "Divya v${BuildConfigVersion.NAME} | ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} | Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                "Jagah: $where | Time: ${java.util.Date()}\n\n"
            file(c.applicationContext).writeText((head + sw.toString()).take(6000))
        } catch (_: Throwable) {}
    }

    fun read(c: Context): String? = try { file(c).takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }
    fun clear(c: Context) { try { file(c).delete() } catch (_: Throwable) {} }
}

object BuildConfigVersion { const val NAME = "2.22.2" }
