package com.example.buddy

import org.json.JSONObject

/**
 * Strict parser for AI-proposed phone actions.
 * Every action must pass the central registry before it can be queued.
 */
object ActionPlanParser {
    fun parse(raw: String): List<AiBrain.ActionStep> {
        return try {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return emptyList()
            val root = JSONObject(raw.substring(start, end + 1))
            if (root.keys().asSequence().toSet() != setOf("steps", "reason")) return emptyList()

            val reason = root.getString("reason").trim()
            if (reason.isBlank() || reason.length > 120 || reason.any { it.isISOControl() }) {
                return emptyList()
            }

            val steps = root.optJSONArray("steps") ?: return emptyList()
            if (steps.length() !in 1..3) return emptyList()

            val result = mutableListOf<AiBrain.ActionStep>()
            for (index in 0 until steps.length()) {
                val item = steps.optJSONObject(index) ?: return emptyList()
                if (item.keys().asSequence().toSet() != setOf("action", "value")) return emptyList()

                val action = item.getString("action").trim().uppercase()
                val value = item.getString("value").trim()
                if (!AgentToolRegistry.validate(action, value)) return emptyList()
                result.add(AiBrain.ActionStep(action, value))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
