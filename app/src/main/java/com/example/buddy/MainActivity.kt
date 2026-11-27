package com.example.buddy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var memoryCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("buddy_ai", MODE_PRIVATE)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(Color.rgb(10, 17, 32))
        }
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 15f
            setPadding(0, 12, 0, 4)
        }
        fun field(hintText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) =
            EditText(this).apply {
                hint = hintText
                setText(value)
                textSize = 15f
                inputType = type
                setSingleLine(true)
            }

        content.addView(TextView(this).apply {
            text = "BUDDY"
            textSize = 34f
            setTextColor(Color.rgb(111, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "YOUR VOICE • YOUR CONTROL"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(155, 177, 204))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        })
        status = TextView(this).apply {
            text = "Phone assistant ready. Start listening and speak a command."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = rounded(Color.rgb(25, 39, 61), 16)
        }
        content.addView(status)
        content.addView(Button(this).apply {
            text = "START LISTENING"
            setOnClickListener { requestAndStart() }
        })
        content.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener {
                stopService(Intent(this@MainActivity, BuddyListeningService::class.java))
                status.text = "Stopped"
            }
        })

        content.addView(Button(this).apply {
            text = "ENABLE SCREEN READING & TEXT TYPING"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Android Accessibility settings will open. Enable Buddy only if you want on-demand screen reading and voice typing into the field you have focused.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        content.addView(TextView(this).apply {
            text = "Optional Accessibility access: when you explicitly ask Buddy what is on the screen, it reads visible text and descriptions from the current app. Password fields are skipped. It can also type into the text field you focused. It does not automatically send messages, submit forms, or make payments."
            textSize = 13f
            setPadding(0, 4, 0, 12)
        })

        content.addView(Button(this).apply {
            text = "ENABLE NOTIFICATION READING"
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })
        content.addView(TextView(this).apply {
            text = "Optional: allow Buddy to read recent notification previews when you explicitly ask, for example: Buddy, who messaged me? Android may show private notification content. Previews stay in app memory and are not sent to the AI service."
            textSize = 13f
            setPadding(0, 4, 0, 12)
        })

        content.addView(label("AI BRAIN SETTINGS (OpenAI-compatible API)"))
        content.addView(label("API endpoint"))
        endpointInput = field("https://api.groq.com/openai/v1/chat/completions",
            prefs.getString("endpoint", "https://api.groq.com/openai/v1/chat/completions").orEmpty())
        content.addView(endpointInput)
        content.addView(label("Model name"))
        modelInput = field("Enter the model ID from your provider",
            prefs.getString("model", "").orEmpty())
        content.addView(modelInput)
        content.addView(label("API key (encrypted with Android Keystore)"))
        keyInput = field("Leave blank to keep the saved key",
            "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                setAutofillHints()
            }
        content.addView(keyInput)
        content.addView(Button(this).apply {
            text = "SAVE AI SETTINGS"
            setOnClickListener {
                val endpoint = endpointInput.text.toString().trim()
                val model = modelInput.text.toString().trim()
                val key = keyInput.text.toString().trim()
                if (endpoint.isNotBlank() && !endpoint.startsWith("https://", ignoreCase = true)) {
                    Toast.makeText(this@MainActivity, "Use a secure https:// endpoint.", Toast.LENGTH_LONG).show()
                } else {
                    if (key.isNotBlank() && !SecureKeyStore.set(this@MainActivity, key)) {
                        Toast.makeText(this@MainActivity, "API key सुरक्षित रूप से save नहीं हुई। पुरानी settings नहीं बदलीं।", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    keyInput.text.clear()
                    prefs.edit().putString("endpoint", endpoint)
                        .putString("model", model)
                        .remove("api_key")
                        .putBoolean("memory_enabled", memoryCheck.isChecked).apply()
                    if (!memoryCheck.isChecked) AiBrain.clearHistory(this@MainActivity)
                    Toast.makeText(this@MainActivity, "AI settings saved on this phone.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        content.addView(Button(this).apply {
            text = "CLEAR SAVED API KEY"
            setOnClickListener {
                SecureKeyStore.set(this@MainActivity, "")
                keyInput.text.clear()
                Toast.makeText(this@MainActivity, "Saved API key cleared from this app.", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(label("OPTIONAL CONVERSATION MEMORY"))
        memoryCheck = CheckBox(this).apply {
            text = "Remember recent AI chat on this phone"
            isChecked = prefs.getBoolean("memory_enabled", false)
        }
        content.addView(memoryCheck)
        content.addView(TextView(this).apply {
            text = "Memory is OFF by default. If enabled, Buddy stores up to 5 recent exchanges locally on this phone and sends that context with your next AI question. Do not store passwords, payment details, or private information."
            textSize = 13f
            setPadding(0, 4, 0, 8)
        })
        content.addView(Button(this).apply {
            text = "CLEAR AI CHAT MEMORY"
            setOnClickListener {
                AiBrain.clearHistory(this@MainActivity)
                Toast.makeText(this@MainActivity, "Recent AI chat memory cleared.", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(TextView(this).apply {
            text = "AI chat requires internet and a provider API key. The key is encrypted with Android Keystore on this phone; free quotas vary. Never share your key or commit it to GitHub. Voice commands for opening apps, settings, alarms and web search work without an AI key. Memory preference is saved when you tap SAVE AI SETTINGS."
            textSize = 13f
            setPadding(0, 12, 0, 16)
        })
        // Apply a consistent high-contrast dark interface to every control.
        for (index in 0 until content.childCount) {
            when (val child = content.getChildAt(index)) {
                is Button -> {
                    child.background = rounded(Color.rgb(24, 111, 158), 13)
                    child.setTextColor(Color.WHITE)
                    child.textSize = 14f
                    child.setPadding(dp(14), dp(13), dp(14), dp(13))
                    child.isAllCaps = false
                }
                is EditText -> {
                    child.setTextColor(Color.WHITE)
                    child.setHintTextColor(Color.rgb(139, 157, 180))
                }
                is TextView -> {
                    if (child !== status && child.textColors.defaultColor == Color.BLACK) {
                        child.setTextColor(Color.rgb(218, 229, 243))
                    }
                }
            }
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 17, 32))
            addView(content)
        }
        setContentView(scroll)
    }

    private fun requestAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 7)
        else startBuddy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 7 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) startBuddy()
        else Toast.makeText(this, "Microphone permission is needed to listen.", Toast.LENGTH_LONG).show()
    }

    private fun startBuddy() {
        try {
            val i = Intent(this, BuddyListeningService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "Listening — speak a command directly…"
        } catch (_: Exception) {
            status.text = "Could not start. Open Buddy and try again."
        }
    }
}
