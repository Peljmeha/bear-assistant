package com.bear.assistant

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    private const val REPO = "Peljmeha/bear-assistant"
    private const val ACTION = "com.bear.assistant.INSTALL_RESULT"

    fun checkAndUpdate(act: Activity, say: (String) -> Unit) {
        if (!act.packageManager.canRequestPackageInstalls()) {
            say("Разреши установку приложений для Bear Assistant и нажми ⟳ ещё раз")
            act.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${act.packageName}")
                )
            )
            return
        }
        say("Проверяю обновления…")
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                if (conn.responseCode != 200) throw Exception("GitHub HTTP ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val latest = json.getString("tag_name").removePrefix("v").toInt()
                val current = act.packageManager
                    .getPackageInfo(act.packageName, 0).longVersionCode.toInt()
                if (latest <= current) {
                    act.runOnUiThread { say("У тебя последняя версия ($current)") }
                    return@Thread
                }
                var url = ""
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
                }
                if (url.isEmpty()) throw Exception("В релизе нет APK")
                act.runOnUiThread { say("Скачиваю версию $latest…") }
                install(act.applicationContext, url)
                act.runOnUiThread { say("Подтверди установку в окне системы") }
            } catch (e: Exception) {
                act.runOnUiThread { say("Ошибка обновления: ${e.message}") }
            }
        }.start()
    }

    private fun install(ctx: Context, url: String) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.inputStream.use { input ->
                session.openWrite("update", 0, -1).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        @Suppress("DEPRECATION")
                        val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (confirm != null) c.startActivity(confirm)
                    }
                }
            }
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
            val intent = Intent(ACTION).setPackage(ctx.packageName)
            val pi = PendingIntent.getBroadcast(
                ctx, id, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(pi.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }
}
