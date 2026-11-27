package com.example.buddy

import java.io.InputStream
import java.net.InetAddress
import java.net.URI

/**
 * Defensive outbound-network guard for the user-configured AI endpoint.
 * HTTPS only, no credentials in URL, no non-standard ports, no local/private destinations.
 */
object NetworkSecurity {
    private val trustedProviderHosts = setOf(
        "api.groq.com",
        "api.openai.com",
        "openrouter.ai",
        "api.together.xyz",
        "api.deepseek.com",
        "api.cerebras.ai",
        "api.mistral.ai"
    )

    fun isSafeHttpsEndpoint(value: String): Boolean {
        return try {
            val uri = URI(value.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            if (uri.userInfo != null || uri.fragment != null) return false
            if (uri.port != -1 && uri.port != 443) return false
            val host = uri.host?.lowercase() ?: return false
            if (host !in trustedProviderHosts || host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal") ||
                (host.contains(".") && host.all { it.isDigit() || it == '.' }) ||
                host.contains(":")) return false
            val addresses = InetAddress.getAllByName(host)
            addresses.isNotEmpty() && addresses.all { address ->
                val bytes = address.address
                val ipv6UniqueLocal = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
                !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress && !address.isSiteLocalAddress &&
                    !address.isMulticastAddress && !ipv6UniqueLocal
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readUtf8Limited(input: InputStream, maxBytes: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (total < maxBytes) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
