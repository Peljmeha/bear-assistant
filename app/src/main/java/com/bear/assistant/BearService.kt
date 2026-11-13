
package com.bear.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class BearService : Service() {
    companion object {
        var instance: BearService? = null
    }

    private lateinit var wm: WindowManager
    private var bear: TextView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (tts == null) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val r = tts?.setLanguage(Locale("ru", "RU"))
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA &&
                        r != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        }
        if (bear == null) showBear()
        return START_STICKY
    }

    private fun say(text: String, queue: Int = TextToSpeech.QUEUE_FLUSH) {
        if (ttsReady) {
            tts?.speak(text, queue, null, "bear")
        } else {
            Toast.makeText(this, "Нет русского голоса: $text", Toast.LENGTH_LONG).show()
        }
    }

    fun onNotification(title: String, text: String) {
        val phrase = listOf("Уведомление", title, text)
            .filter { it.isNotBlank() }
            .joinToString(". ")
            .take(300)
        say(phrase, TextToSpeech.QUEUE_ADD)
        bear?.animate()
            ?.scaleX(1.4f)?.scaleY(1.4f)?.setDuration(200)
            ?.withEndAction { bear?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start() }
            ?.start()
    }

    private fun startAsForeground() {
        val channelId = "bear"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Медвежонок", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, channelId)
            .setContentTitle("Медвежонок работает")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun showBear() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "🐻"
            textSize = 48f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 600
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(v, params)
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        say("Привет! Я медвежонок")
                    } else {
                        val screenW = resources.displayMetrics.widthPixels
                        params.x = if (params.x + v.width / 2 < screenW / 2) 0 else screenW - v.width
                        wm.updateViewLayout(v, params)
                    }
                }
            }
            true
        }

        wm.addView(view, params)
        bear = view
    }

    override fun onDestroy() {
        instance = null
        tts?.shutdown()
        tts = null
        bear?.let { wm.removeView(it) }
        bear = null
        super.onDestroy()
    }
}
