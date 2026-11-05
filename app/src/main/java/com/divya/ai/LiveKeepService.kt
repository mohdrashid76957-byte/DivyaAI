package com.divya.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

object ShareBus { @Volatile var on = false }

/** Divya ka session chal raha ho tab chalta hai: app background mein jaye to bhi mic/awaaz/glow zinda rehte hain. */
class LiveKeepService : Service() {
    companion object {
        fun start(c: Context) { try { c.startForegroundService(Intent(c, LiveKeepService::class.java)) } catch (_: Exception) {} }
        fun stop(c: Context) { try { c.stopService(Intent(c, LiveKeepService::class.java)) } catch (_: Exception) {} }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("live", "Divya active", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "live").setContentTitle("Divya active hai")
            .setContentText(if (ShareBus.on) "📺 Screen sharing ON - Divya aapki screen dekh rahi hai. Band karne ke liye app kholo." else "Background mein bhi sun rahi hai. Rokne ke liye app mein mic dabao.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val mic = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                // Location type bhi (agar permission mili hai) taaki background mein bhi "main kahan hoon" / Maps ke liye GPS mil sake
                try {
                    if (PhoneTools.hasLoc(this)) startForeground(78, n, mic or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    else startForeground(78, n, mic)
                } catch (e: Exception) { startForeground(78, n, mic) }
            } else startForeground(78, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        return START_NOT_STICKY
    }
}
