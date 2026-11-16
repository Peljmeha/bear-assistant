package com.bear.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PanelActivity : Activity() {
    private var screen = "menu"
    private val avatars = listOf("🐻", "🐼", "🧸", "🦊", "🐱", "🐶", "🐰", "🦁", "🐸", "🤖")

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (getSharedPreferences("bear", MODE_PRIVATE).getBoolean("dark", false))
                android.R.style.Theme_Material_Dialog
            else android.R.style.Theme_Material_Light_Dialog
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getString("screen") == "settings") showSettings() else showMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("screen", screen)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun column(): LinearLayout =
        LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun title(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 20f
        setPadding(0, dp(8), 0, dp(12))
    }

    private fun btn(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun show(content: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(content)
        }
        setContentView(ScrollView(this).apply { addView(box) })
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun showMenu() {
        screen = "menu"
        val c = column()
        c.addView(title("🐻 Медвежонок"))
        c.addView(btn("💬 Разговор с Gemini") {
            startActivity(Intent(this, ChatActivity::class.java))
        })
        c.addView(btn("🔑 API-ключ") { askKey() })
        c.addView(btn("🎭 Аватары") { showAvatars() })
        c.addView(btn("⚙️ Настройки") { showSettings() })
        c.addView(btn("⟳ Обновление") { showUpdate() })
        show(c)
    }

    private fun askKey() {
        val p = getSharedPreferences("bear", MODE_PRIVATE)
        val et = EditText(this).apply {
            hint = "Вставь ключ Gemini"
            setText(p.getString("key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("API-ключ Gemini")
            .setView(et)
            .setPositiveButton("Сохранить") { _, _ ->
                p.edit().putString("key", et.text.toString().trim()).apply()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAvatars() {
        screen = "sub"
        val c = column()
        c.addView(title("Выбери аватар"))
        val cur = getSharedPreferences("bear", MODE_PRIVATE).getString("avatar", "🐻")
        for (a in avatars) {
            val label = if (a == cur) "$a  ✓" else a
            c.addView(btn(label) {
                getSharedPreferences("bear", MODE_PRIVATE).edit().putString("avatar", a).apply()
                BearService.instance?.setAvatar(a)
                showAvatars()
            })
        }
        c.addView(btn("← Назад") { showMenu() })
        show(c)
    }

    private fun showSettings() {
        screen = "settings"
        val p = getSharedPreferences("bear", MODE_PRIVATE)
        val c = column()
        c.addView(title("Настройки"))

        val theme = CheckBox(this)
        theme.text = "🌙 Тёмная тема (выключено: белая)"
        theme.isChecked = p.getBoolean("dark", false)
        theme.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("dark", on).apply()
            recreate()
        }
        c.addView(theme)

        c.addView(title("Озвучивать уведомления от:"))
        val muted = (p.getStringSet("muted", emptySet()) ?: emptySet()).toMutableSet()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launch, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        for ((label, pkg) in apps) {
            val box = CheckBox(this)
            box.text = label
            box.isChecked = pkg !in muted
            box.setOnCheckedChangeListener { _, on ->
                if (on) muted.remove(pkg) else muted.add(pkg)
                p.edit().putStringSet("muted", HashSet(muted)).apply()
            }
            c.addView(box)
        }
        c.addView(btn("← Назад") { showMenu() })
        show(c)
    }

    private fun showUpdate() {
        screen = "sub"
        val c = column()
        c.addView(title("Обновление"))
        val status = TextView(this)
        status.textSize = 16f
        status.text = "Текущая версия: " + packageManager.getPackageInfo(packageName, 0).versionName
        c.addView(status)
        c.addView(btn("Проверить обновления") {
            Updater.checkAndUpdate(this) { status.text = it }
        })
        c.addView(btn("← Назад") { showMenu() })
        show(c)
    }

    override fun onBackPressed() {
        if (screen != "menu") showMenu() else super.onBackPressed()
    }
}
