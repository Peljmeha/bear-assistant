package com.bear.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class MainActivity : Activity() {
    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startForegroundService(Intent(this, BearService::class.java))
            finish()
        }
    }
}
