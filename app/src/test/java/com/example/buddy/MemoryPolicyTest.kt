package com.example.buddy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPolicyTest {
    @Test fun doesNotPersistWhenMemoryWasOffAtRequestTime() {
        assertFalse(MemoryPolicy.shouldPersistHistory(false, true, "reply"))
    }

    @Test fun doesNotPersistIfMemoryWasTurnedOffDuringRequest() {
        assertFalse(MemoryPolicy.shouldPersistHistory(true, false, "reply"))
    }

    @Test fun doesNotPersistBlankReplies() {
        assertFalse(MemoryPolicy.shouldPersistHistory(true, true, "  "))
    }

    @Test fun persistsOnlyWhenMemoryRemainsEnabled() {
        assertTrue(MemoryPolicy.shouldPersistHistory(true, true, "reply"))
    }
}
