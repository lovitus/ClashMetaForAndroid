package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunAddressTest {
    @Test
    fun resolveKeepsDefaultAddressWhenIsolationDisabled() {
        val address = TunAddress.resolve(autoIsolation = false, uid = 10 * 100000 + 12345)

        assertEquals(10, address.androidUserId)
        assertEquals(19, address.bucket)
        assertEquals("172.19.0.1", address.gateway4)
        assertEquals("172.19.0.2", address.portal4)
        assertEquals("fdfe:dcba:9876::1", address.gateway6)
        assertEquals("fdfe:dcba:9876::2", address.portal6)
        assertFalse(address.isolated)
    }

    @Test
    fun resolveKeepsDefaultAddressForPrimaryUser() {
        val address = TunAddress.resolve(autoIsolation = true, uid = 10000)

        assertEquals(0, address.androidUserId)
        assertEquals(19, address.bucket)
        assertEquals("172.19.0.1", address.gateway4)
        assertEquals("172.19.0.2", address.portal4)
        assertFalse(address.isolated)
    }

    @Test
    fun resolveUsesWorkProfileBucket() {
        val address = TunAddress.resolve(autoIsolation = true, uid = 10 * 100000 + 12345)

        assertEquals(10, address.androidUserId)
        assertEquals(20, address.bucket)
        assertEquals("172.20.0.1", address.gateway4)
        assertEquals("172.20.0.2", address.portal4)
        assertEquals("fdfe:dcba:9876:20::1", address.gateway6)
        assertEquals("fdfe:dcba:9876:20::2", address.portal6)
        assertTrue(address.isolated)
    }

    @Test
    fun resolveUsesAppCloneBucket() {
        val address = TunAddress.resolve(autoIsolation = true, uid = 999 * 100000 + 12345)

        assertEquals(999, address.androidUserId)
        assertEquals(29, address.bucket)
        assertEquals("172.29.0.1", address.gateway4)
        assertEquals("172.29.0.2", address.portal4)
        assertTrue(address.isolated)
    }

    @Test
    fun resolveUsesFloorModFallbackBucket() {
        val address = TunAddress.resolve(autoIsolation = true, uid = 11 * 100000 + 12345)

        assertEquals(11, address.androidUserId)
        assertEquals(21, address.bucket)
        assertEquals("172.21.0.1", address.gateway4)
        assertEquals("fdfe:dcba:9876:21::1", address.gateway6)
        assertTrue(address.isolated)
    }

    @Test
    fun resolveHandlesInvalidNegativeUidAsPrimaryUser() {
        val address = TunAddress.resolve(autoIsolation = true, uid = -1)

        assertEquals(0, address.androidUserId)
        assertEquals(19, address.bucket)
        assertEquals("172.19.0.1", address.gateway4)
        assertFalse(address.isolated)
    }
}
