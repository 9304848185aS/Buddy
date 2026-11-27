package com.example.buddy

import java.net.URI

/**
 * Central, explicit allowlist for actions the agent may propose.
 *
 * This registry is deliberately platform-independent so it can be unit-tested.
 * Adding a tool does not grant Android permissions or bypass per-step confirmation.
 */
object AgentToolRegistry {
    data class ToolDefinition(
        val name: String,
        val description: String,
        val requiresUserConfirmation: Boolean = true,
        val maxValueLength: Int = 180
    )

    private val definitions = listOf(
        ToolDefinition("OPEN_APP", "Open an installed app by its visible name."),
        ToolDefinition("OPEN_URL", "Open the exact website address explicitly requested by the user.", requiresUserConfirmation = false, maxValueLength = 500),
        ToolDefinition("TYPE_TEXT", "Insert exact dictated text into the field the user has focused.", maxValueLength = 180),
        ToolDefinition("WEB_SEARCH", "Open a web search for the user's query.")
    )

    private val byName = definitions.associateBy { it.name }

    fun find(name: String): ToolDefinition? = byName[name.trim().uppercase()]

    fun supports(name: String): Boolean = find(name) != null

    fun all(): List<ToolDefinition> = definitions.toList()

    /**
     * Validates an AI-proposed tool call before it can enter the execution queue.
     * Website navigation accepts only explicit HTTP(S) URLs with a host.
     */
    fun validate(name: String, value: String): Boolean {
        val definition = find(name) ?: return false
        if (value.isBlank() || value.length > definition.maxValueLength || value.any { it.isISOControl() }) {
            return false
        }
        if (definition.name == "OPEN_URL") {
            return try {
                val uri = URI(value.trim())
                (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            } catch (_: Exception) {
                false
            }
        }
        return true
    }
}
