package com.bear.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val f = File(filesDir, "crash.txt")
        if (f.exists()) {
            val tv = TextView(this).apply {
                text = f.readText()
                setTextIsSelectable(true)
                setPadding(24, 24, 24, 24)
            }
            val btn = Button(this).apply {
                text = "Очистить и запустить"
                setOnClickListener { f.delete(); recreate() }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(btn)
                addView(tv)
            }
            setContentView(ScrollView(this).apply { addView(box) })
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startForegroundService(Intent(this, BearService::class.java))
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabled == null || !enabled.contains(packageName)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        finish()
    }
}
