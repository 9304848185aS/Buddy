package com.example.buddy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class NetworkSecurityTest {
    @Test fun rejectsHttp() {
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("http://example.com/api"))
    }

    @Test fun rejectsLocalAndIpLiteralHosts() {
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://localhost/api"))
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://127.0.0.1/api"))
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://192.168.1.2/api"))
    }

    @Test fun rejectsCredentialsAndNonStandardPorts() {
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://user:pass@example.com/api"))
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://example.com:8443/api"))
    }

    @Test fun acceptsTrustedProviderHttpsHost() {
        assertTrue(NetworkSecurity.isSafeHttpsEndpoint("https://api.groq.com/openai/v1/chat/completions"))
    }

    @Test fun rejectsUnlistedHttpsHosts() {
        assertFalse(NetworkSecurity.isSafeHttpsEndpoint("https://example.com/v1/chat/completions"))
    }

    @Test fun responseReaderHonoursByteLimit() {
        assertTrue(NetworkSecurity.readUtf8Limited(ByteArrayInputStream("abcdefghij".toByteArray()), 4) == "abcd")
    }
}
