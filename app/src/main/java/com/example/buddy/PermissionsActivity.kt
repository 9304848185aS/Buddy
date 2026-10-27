package com.example.buddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }
        scroll.addView(box)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        draw()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasNotif() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        this, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun hasBattery(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun draw() {
        box.removeAllViews()
        title("Buddy को ये इजाज़तें चाहिए")
        row("माइक्रोफ़ोन, ताकि आपकी आवाज़ सुन सके", hasMic()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        row("नोटिफ़िकेशन, ताकि चालू होने की सूचना दिखा सके", hasNotif()) {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }
        row("दूसरे ऐप के ऊपर दिखाना, ताकि रोकने का बटन दिखे", hasOverlay()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        row("बैटरी छूट, ताकि पीछे चलता रहे", hasBattery()) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        val done = Button(this).apply { text = "हो गया, वापस जाएँ" }
        done.setOnClickListener { finish() }
        box.addView(done)
    }

    private fun title(s: String) {
        box.addView(TextView(this).apply {
            text = s
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        })
    }

    private fun row(label: String, granted: Boolean, onGrant: () -> Unit) {
        box.addView(TextView(this).apply {
            text = (if (granted) "✅ " else "⬜ ") + label
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })
        if (!granted) {
            val b = Button(this).apply { text = "इजाज़त दें" }
            b.setOnClickListener { onGrant() }
            box.addView(b)
        }
    }
}
