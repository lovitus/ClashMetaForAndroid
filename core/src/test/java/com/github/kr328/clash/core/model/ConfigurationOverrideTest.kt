package com.github.kr328.clash.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationOverrideTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun fakeIpRangeNullKeepsNoOverride() {
        val encoded = json.encodeToString(ConfigurationOverride())

        assertFalse(encoded.contains("fake-ip-range"))
    }

    @Test
    fun fakeIpRangeSerializesAsDnsField() {
        val configuration = ConfigurationOverride().apply {
            dns.fakeIpRange = "198.18.0.1/16"
        }

        val encoded = json.encodeToString(configuration)

        assertTrue(encoded.contains("\"dns\":"))
        assertTrue(encoded.contains("\"fake-ip-range\":\"198.18.0.1/16\""))
    }

    @Test
    fun fakeIpRangeIsCompatibleWithOldOverrideJson() {
        val decoded = json.decodeFromString<ConfigurationOverride>("""{"dns":{"enable":true}}""")

        assertNull(decoded.dns.fakeIpRange)
    }
}
