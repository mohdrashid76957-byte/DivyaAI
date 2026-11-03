package com.divya.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Phone ki notifications padhne ke liye (Divya "notification bata" bolne par yahi se leti hai). */
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
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
}
