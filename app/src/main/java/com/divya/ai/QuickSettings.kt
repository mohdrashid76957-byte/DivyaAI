package com.divya.ai

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * v3.12: voice se quick settings.
 * - "Do Not Disturb on/off" (permission "Notification access" pehli baar Settings mein dena hota hai)
 * - WiFi / mobile data / Bluetooth: Android 10+ par WiFi seedha band nahi hota, isliye sahi settings panel kholta hai.
 * Alarm, timer, reminder aur brightness pehle se OfflineBrain / OfflineFeatures mein hain.
 */
object QuickSettings {
    fun run(ctx: Context, command: String): String {
        val t = command.lowercase()
        if (listOf("do not disturb", "donotdisturb", "dnd", "disturb").any { t.contains(it) }) return dnd(ctx, t)
        if (t.contains("bluetooth")) {
            open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
            return "Bluetooth settings khol di hain."
        }
        if (listOf("wifi", "wi-fi", "mobile data", "internet").any { t.contains(it) }) {
            val action = if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS
            open(ctx, action)
            return "Android 10 ke baad WiFi ya mobile data seedha band nahi hota, isliye internet panel khol di hai. Wahan se toggle karo."
        }
        return "Ye quick setting abhi nahi hai. Do Not Disturb, WiFi, mobile data ya Bluetooth bolo."
    }

    private fun dnd(ctx: Context, t: String): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            open(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return "Do Not Disturb ke liye pehle permission do. Settings khol di hai, Divya ko allow karo, phir dobara bolo."
        }
        val off = listOf("off", "band", "bandh", "hatao").any { t.contains(it) }
        nm.setInterruptionFilter(
            if (off) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE
        )
        return if (off) "Do Not Disturb band kar diya." else "Do Not Disturb on kar diya."
    }

    private fun open(ctx: Context, action: String) {
        try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
