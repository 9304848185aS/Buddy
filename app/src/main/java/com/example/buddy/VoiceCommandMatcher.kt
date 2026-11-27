package com.example.buddy

import java.util.Locale

/**
 * Deterministic parsing for wake-word and confirmation phrases.
 * Keep this independent of Android so language variants can be unit-tested.
 */
object VoiceCommandMatcher {
    data class WakeCommand(
        val recognized: Boolean,
        val command: String,
        val wakeOnly: Boolean
    )

    private val wakePrefix = Regex("^(buddy|बडी|बड्डी)(?=$|[\\s,،.!?;:।])")

    fun normalize(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .trim()
            .replace(Regex("\\s+"), " ")
            .trim { it in " ,،.!?;:।" }

    fun splitWakeWord(raw: String): WakeCommand {
        val normalized = normalize(raw)
        val match = wakePrefix.find(normalized)
            ?: return WakeCommand(false, normalized, false)
        val command = normalized.substring(match.range.last + 1).trim()
            .trimStart(',', '،', '.', '!', '?', ';', ':', '।')
            .trim()
        return WakeCommand(true, command, command.isBlank())
    }

    fun isConfirmation(raw: String): Boolean {
        val phrase = normalize(raw)
        return phrase in setOf(
            "हाँ", "हां", "हा", "जी हाँ", "जी हां",
            "yes", "yeah", "yep", "haan", "han", "confirm",
            "कर दो", "कर दीजिए", "कर दीजिये", "हाँ कर दो", "हां कर दो"
        )
    }

    fun isCancellation(raw: String): Boolean {
        val phrase = normalize(raw)
        return phrase in setOf(
            "नहीं", "नही", "ना", "मत करो", "रद्द करो", "रद्द कर दो",
            "cancel", "no", "nope", "नहीं करना", "मत कीजिए"
        )
    }

    fun isStopCommand(raw: String): Boolean {
        val phrase = normalize(raw)
        return phrase in setOf(
            "रुक", "रुक जा", "रुक जाओ", "बंद सुनना", "सुनना बंद करो",
            "stop", "stop listening", "buddy stop"
        )
    }
}
