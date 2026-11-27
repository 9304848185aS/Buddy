package com.example.buddy

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Short in-memory notification previews, read only after an explicit user request. */
class BuddyNotificationListener : NotificationListenerService() {
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        synchronized(lock) { recent.removeAll { it.key == key } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        if (item.packageName == packageName) return
        val extras = item.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            val info = packageManager.getApplicationInfo(item.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { item.packageName.substringAfterLast('.') }
        val line = listOf(app, title, text).filter { it.isNotBlank() }.joinToString(": ").take(500)
        synchronized(lock) {
            recent.removeAll { it.key == item.key }
            recent.add(0, Entry(item.key, System.currentTimeMillis(), line))
            while (recent.size > 10) recent.removeAt(recent.lastIndex)
        }
    }

    companion object {
        private val lock = Any()
        private data class Entry(val key: String, val time: Long, val text: String)
        private val recent = mutableListOf<Entry>()

        fun latestSummary(): String = synchronized(lock) {
            if (recent.isEmpty()) return@synchronized "अभी कोई notification उपलब्ध नहीं है। Notification access चालू करने के बाद आने वाले notifications यहाँ मिलेंगे।"
            val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            recent.take(5).mapIndexed { index, entry ->
                (index + 1).toString() + ". " + entry.text + " (" + formatter.format(Date(entry.time)) + ")"
            }.joinToString(". ").take(1800)
        }
    }
}
