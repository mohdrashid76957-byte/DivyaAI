package com.divya.ai

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Phone ki notifications padhne ke liye (Divya "notification bata" bolne par yahi se leti hai).
 * v2.11: naye notification aaye to padh kar sunata hai, call aaye to caller ka naam bolta hai,
 * aur charger lagane / hatane par battery ke saath bolta hai. Notification access ON honi zaroori hai.
 */
class DivyaNotifListener : NotificationListenerService() {
    companion object {
        @Volatile var instance: DivyaNotifListener? = null

        fun summary(max: Int = 12): String {
            val l = instance ?: return "notification access OFF - user ko Settings > Permissions mein 'Notification access' ON karna hoga"
            val list = try { l.activeNotifications.toList() } catch (e: Exception) { emptyList() }
            val items = list.filter { it.packageName != l.packageName && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .sortedByDescending { it.postTime }.take(max)
            if (items.isEmpty()) return "koi nayi notification nahi hai"
            val pm = l.packageManager
            return items.joinToString("\n") { sbn ->
                val ex = sbn.notification.extras
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Exception) { sbn.packageName }
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                "$app: $title - ${text.take(160)}"
            }
        }
    }

    private val seen = LinkedHashSet<String>()

    private val powerRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val p = getSharedPreferences("divya", 0)
            if (!p.getBoolean("annCharge", true)) return
            val lvl = try { getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 } catch (_: Exception) { -1 }
            val pct = if (lvl >= 0) " Battery $lvl percent hai." else ""
            val msg = when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> "Charger laga diya gaya hai.$pct"
                Intent.ACTION_POWER_DISCONNECTED -> "Charger hata diya gaya hai.$pct"
                else -> return
            }
            Announcer.speak(c, msg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(powerRx, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(powerRx, f)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { unregisterReceiver(powerRx) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        val p = getSharedPreferences("divya", 0)
        val ex = n.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
        // Ek notification ek hi baar bolo (dialer aksar update karta hai)
        if (!seen.add(n.key)) return
        if (seen.size > 200) seen.remove(seen.first())

        if (n.notification.category == Notification.CATEGORY_CALL) {
            if (p.getBoolean("annCall", true)) announceCall(title, text)
            return
        }
        if (!p.getBoolean("annNotif", true)) return
        if (n.isOngoing || (n.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (title.isBlank() && text.isBlank()) return
        val pm = packageManager
        val app = try { pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString() } catch (_: Exception) { "" }
        val body = listOf(title, text.take(200)).filter { it.isNotBlank() }.joinToString(". ")
        Announcer.speak(this, if (app.isBlank()) body else "$app se: $body")
    }

    private fun announceCall(title: String, text: String) {
        val p = getSharedPreferences("divya", 0)
        val who = (p.getString("annName", "Rashid sir") ?: "").ifBlank { "Sir" }
        // Dialer aksar naam title mein deta hai; "Incoming call" jaisa generic title ho to text lo
        val generic = title.isBlank() || title.contains("call", ignoreCase = true) || title.contains("incoming", ignoreCase = true)
        val caller = (if (generic) text else title).ifBlank { "anjaan number" }
        Announcer.speak(this, "$who, $caller ka call aa raha hai.")
    }
}
