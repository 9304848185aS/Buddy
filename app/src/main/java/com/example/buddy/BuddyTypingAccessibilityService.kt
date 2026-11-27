package com.example.buddy

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BuddyTypingAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    private fun enterFocusedText(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!field.isEditable || !field.isEnabled || field.isPassword) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, output: MutableList<String>, budget: IntArray) {
        if (node == null || budget[0] <= 0) return
        if (node.isPassword) return
        val value = node.text?.toString()?.trim().orEmpty()
            .ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
        if (value.isNotBlank() && output.lastOrNull() != value) {
            output.add(value.take(300))
            budget[0]--
        }
        for (i in 0 until node.childCount) {
            if (budget[0] <= 0 || output.joinToString(" ").length >= 2400) break
            try { collectVisibleText(node.getChild(i), output, budget) } catch (_: Exception) { }
        }
    }

    private fun readVisibleScreen(): String {
        val root = rootInActiveWindow ?: return "इस ऐप की स्क्रीन का text उपलब्ध नहीं है। Accessibility permission चालू है या नहीं, जाँचें।"
        val output = mutableListOf<String>()
        collectVisibleText(root, output, intArrayOf(40))
        val result = output.distinct().joinToString(". ").take(2400)
        return if (result.isBlank()) "इस स्क्रीन पर पढ़ने योग्य text नहीं मिला।" else result
    }

    companion object {
        @Volatile private var active: BuddyTypingAccessibilityService? = null
        private val main = Handler(Looper.getMainLooper())

        fun typeIntoFocusedField(value: String, result: (Boolean) -> Unit) {
            val service = active
            if (service == null) { result(false); return }
            main.post {
                val success = try { service.enterFocusedText(value) } catch (_: Exception) { false }
                result(success)
            }
        }

        fun readScreen(result: (String) -> Unit) {
            val service = active
            if (service == null) {
                result("स्क्रीन पढ़ने के लिए Buddy की Accessibility permission चालू करनी होगी।")
                return
            }
            main.post {
                val value = try { service.readVisibleScreen() } catch (_: Exception) { "अभी स्क्रीन पढ़ना संभव नहीं हुआ।" }
                result(value)
            }
        }
    }
}
