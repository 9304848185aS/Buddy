package com.example.buddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandMatcherTest {
    @Test fun recognizesEnglishAndHindiWakeWords() {
        listOf("Buddy", "buddy, open YouTube", "बडी खोलो", "बड्डी, क्रोम खोलो").forEach { phrase ->
            assertTrue(phrase, VoiceCommandMatcher.splitWakeWord(phrase).recognized)
        }
    }

    @Test fun doesNotMatchWakeWordInsideAnotherWord() {
        assertFalse(VoiceCommandMatcher.splitWakeWord("somebuddy open chrome").recognized)
        assertFalse(VoiceCommandMatcher.splitWakeWord("बडीपन खोलो").recognized)
    }

    @Test fun separatesWakeOnlyAndCommandPhrases() {
        val wakeOnly = VoiceCommandMatcher.splitWakeWord("Buddy")
        assertTrue(wakeOnly.wakeOnly)
        assertEquals("", wakeOnly.command)

        val command = VoiceCommandMatcher.splitWakeWord("Buddy, open YouTube")
        assertFalse(command.wakeOnly)
        assertEquals("open youtube", command.command)
    }

    @Test fun acceptsExplicitConfirmationPhrases() {
        listOf("हाँ", "हां", "जी हाँ", "yes", "haan", "confirm", "कर दो").forEach {
            assertTrue(it, VoiceCommandMatcher.isConfirmation(it))
        }
    }

    @Test fun rejectsAmbiguousLongerConfirmationPhrases() {
        assertFalse(VoiceCommandMatcher.isConfirmation("हाँ शायद"))
        assertFalse(VoiceCommandMatcher.isConfirmation("yes but later"))
    }

    @Test fun recognizesExplicitCancellationPhrases() {
        listOf("नहीं", "नही", "मत करो", "रद्द कर दो", "cancel", "no").forEach {
            assertTrue(it, VoiceCommandMatcher.isCancellation(it))
        }
    }

    @Test fun recognizesStopPhrasesWithoutSubstringFalsePositives() {
        assertTrue(VoiceCommandMatcher.isStopCommand("stop listening"))
        assertTrue(VoiceCommandMatcher.isStopCommand("रुक जाओ"))
        assertFalse(VoiceCommandMatcher.isStopCommand("please don't stop the music"))
    }
}
