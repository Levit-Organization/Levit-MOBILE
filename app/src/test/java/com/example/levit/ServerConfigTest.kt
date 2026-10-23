package com.example.levit

import com.example.levit.data.local.ServerConfig
import org.junit.Assert.*
import org.junit.Test

class ServerConfigTest {
    @Test fun adicionaOPrefixoCorretoDaApi() {
        assertEquals("http://10.0.2.2:8080/api/v1/", ServerConfig.normalizarBaseUrl("10.0.2.2:8080"))
        assertEquals("https://api.exemplo.test/api/v1/", ServerConfig.normalizarBaseUrl("https://api.exemplo.test/api/v1/"))
    }

    @Test fun recusaDestinosInadequados() {
        listOf("", "file:///tmp/teste", "http://usuario@localhost", "http://localhost/outro",
            "http://localhost?token=123", "http://localhost:99999").forEach {
            assertTrue(it, runCatching { ServerConfig.normalizarBaseUrl(it) }.isFailure)
        }
    }
}
