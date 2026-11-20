package com.bear.assistant

import android.Manifest
import android.app.Notification
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val prefs = getSharedPreferences("bear", MODE_PRIVATE)
        val muted = prefs.getStringSet("muted", emptySet()) ?: emptySet()
        if (sbn.packageName in muted) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_CALL) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        if (prefs.getBoolean("contacts_only", false) && !isContact(title)) return
        BearService.instance?.onNotification(title, text)
    }

    private fun isContact(title: String): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val t = title.trim()
        if (t.isEmpty()) return false
        if (t.any { it.isDigit() } && t.all { it.isDigit() || it in "+-() " }) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(t))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                return it.count > 0
            }
            return false
        }
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(t))
        contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                if (name.contains(t, ignoreCase = true)) return true
            }
        }
        return false
    }
}
