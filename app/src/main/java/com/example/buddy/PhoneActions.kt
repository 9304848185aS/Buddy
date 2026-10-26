package com.example.buddy

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class PhoneActions(private val ctx: Context) {

    fun setAlarm(hour: Int, minute: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Buddy अलार्म")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(i)
            "ठीक है, $hour बजकर $minute मिनट का अलार्म लगा दिया"
        } catch (e: Exception) {
            "अलार्म नहीं लग पाया"
        }
    }

    fun setTimer(seconds: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(i)
            "टाइमर लगा दिया"
        } catch (e: Exception) {
            "टाइमर नहीं लग पाया"
        }
    }

    fun openApp(name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val aliases = mapOf(
            "व्हाट्सऐप" to "whatsapp", "व्हाट्सएप" to "whatsapp",
            "यूट्यूब" to "youtube", "कैमरा" to "camera",
            "गैलरी" to "gallery", "सेटिंग" to "settings", "क्रोम" to "chrome"
        )
        val target = (aliases[name] ?: name).lowercase()
        val hit = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(target)
        } ?: return "$name ऐप मुझे नहीं मिला"
        val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            ?: return "$name ऐप खुल नहीं पाया"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return "$name खोल दिया"
    }
}
