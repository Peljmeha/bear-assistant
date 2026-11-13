package com.bear.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatActivity : Activity() {
    companion object {
        val history = JSONArray()
    }

    private lateinit var answer: TextView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        answer = TextView(this).apply {
            textSize = 16f
            text = "Привет! Спроси меня о чём-нибудь 🐻"
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(answer) }
        input = EditText(this).apply { hint = "Напиши вопрос" }
        val mic = Button(this).apply {
            text = "🎤"
            setOnClickListener { startVoice() }
        }
        sendBtn = Button(this).apply {
            text = "Отправить"
            setOnClickListener { send() }
        }
        val keyBtn = Button(this).apply {
            text = "🔑"
            setOnClickListener { askKey() }
        }
        val row = LinearLayout(this).apply {
            addView(mic)
            addView(sendBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(keyBtn)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            minimumHeight = (400 * d).toInt()
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(input)
            addView(row)
        }
        setContentView(root)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (getKey().isBlank()) askKey()
    }

    private fun getKey(): String =
        getSharedPreferences("bear", MODE_PRIVATE).getString("key", "") ?: ""

    private fun askKey() {
        val et = EditText(this).apply {
            hint = "Вставь ключ Gemini"
            setText(getKey())
        }
        AlertDialog.Builder(this)
            .setTitle("Ключ Gemini")
            .setView(et)
            .setPositiveButton("Сохранить") { _, _ ->
                getSharedPreferences("bear", MODE_PRIVATE)
                    .edit().putString("key", et.text.toString().trim()).apply()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            Toast.makeText(this, "Голосовой ввод недоступен", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) {
                input.setText(t)
                send()
            }
        }
    }

    private fun send() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        val key = getKey()
        if (key.isBlank()) {
            askKey()
            return
        }
        input.setText("")
        answer.text = "Думаю…"
        sendBtn.isEnabled = false
        history.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", q)))
        )
        Thread {
            var ok = true
            val result = try {
                callGemini(key)
            } catch (e: Exception) {
                ok = false
                history.remove(history.length() - 1)
                "Ошибка: ${e.message}"
            }
            runOnUiThread {
                sendBtn.isEnabled = true
                answer.text = result
                if (ok) BearService.instance?.say(result)
            }
        }.start()
    }

    private fun callGemini(key: String): String {
        val system = "Ты дружелюбный медвежонок-помощник на телефоне. " +
            "Отвечай по-русски, кратко (1-3 предложения), простым текстом без markdown и списков."
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put("contents", history)
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val conn = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
        ).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", key)
        conn.connectTimeout = 15000
        conn.readTimeout = 40000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("HTTP $code ${text.take(200)}")

        val parts = JSONObject(text)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        val reply = sb.toString().trim()
        history.put(
            JSONObject().put("role", "model")
                .put("parts", JSONArray().put(JSONObject().put("text", reply)))
        )
        return reply
    }
}
