package com.example.buddy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolRegistryTest {
    @Test fun exposesOnlyExplicitlyRegisteredTools() {
        assertTrue(AgentToolRegistry.supports("OPEN_APP"))
        assertTrue(AgentToolRegistry.supports("type_text"))
        assertTrue(AgentToolRegistry.supports("WEB_SEARCH"))
        assertTrue(AgentToolRegistry.supports("OPEN_URL"))
        assertFalse(AgentToolRegistry.supports("SEND_MESSAGE"))
        assertFalse(AgentToolRegistry.supports("PAYMENT"))
        assertFalse(AgentToolRegistry.supports("ARBITRARY_TAP"))
    }

    @Test fun validatesValuesBeforeQueueing() {
        assertTrue(AgentToolRegistry.validate("OPEN_APP", "Chrome"))
        assertTrue(AgentToolRegistry.validate("WEB_SEARCH", "weather in Pune"))
        assertTrue(AgentToolRegistry.validate("OPEN_URL", "https://www.india.gov.in/"))
        assertTrue(AgentToolRegistry.validate("OPEN_URL", "http://example.gov.in/form"))
        assertFalse(AgentToolRegistry.validate("WEB_SEARCH", "  "))
        assertFalse(AgentToolRegistry.validate("WEB_SEARCH", "bad\nvalue"))
        assertFalse(AgentToolRegistry.validate("OPEN_URL", "javascript:alert(1)"))
        assertFalse(AgentToolRegistry.validate("OPEN_URL", "not a url"))
        assertFalse(AgentToolRegistry.validate("OPEN_URL", "https://user:pass@example.gov.in/"))
        assertFalse(AgentToolRegistry.validate("UNKNOWN_TOOL", "value"))
        assertFalse(AgentToolRegistry.validate("TYPE_TEXT", "x".repeat(181)))
    }

    @Test fun websiteOpeningUsesTheUserCommandAsAuthorization() {
        assertNotNull(AgentToolRegistry.find("OPEN_URL"))
        assertFalse(AgentToolRegistry.find("OPEN_URL")!!.requiresUserConfirmation)
        assertTrue(AgentToolRegistry.find("OPEN_APP")!!.requiresUserConfirmation)
        assertTrue(AgentToolRegistry.find("TYPE_TEXT")!!.requiresUserConfirmation)
        assertTrue(AgentToolRegistry.find("WEB_SEARCH")!!.requiresUserConfirmation)
    }
}
