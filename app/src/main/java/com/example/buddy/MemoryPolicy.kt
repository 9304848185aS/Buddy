package com.example.buddy

/** Keeps optional conversation memory disabled unless it remains enabled at save time. */
internal object MemoryPolicy {
    fun shouldPersistHistory(enabledAtRequest: Boolean, enabledAtSave: Boolean, content: String): Boolean =
        enabledAtRequest && enabledAtSave && content.isNotBlank()
}
