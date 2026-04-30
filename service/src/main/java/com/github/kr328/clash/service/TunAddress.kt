package com.github.kr328.clash.service

import kotlin.math.max

data class TunAddress(
    val gateway4: String,
    val portal4: String,
    val prefix4: Int,
    val gateway6: String,
    val portal6: String,
    val prefix6: Int,
    val androidUserId: Int,
    val bucket: Int,
    val isolated: Boolean,
) {
    val dns4: String
        get() = portal4

    val dns6: String
        get() = portal6

    fun gatewaySpec(allowIpv6: Boolean): String {
        return "$gateway4/$prefix4" + if (allowIpv6) ",$gateway6/$prefix6" else ""
    }

    fun portalSpec(allowIpv6: Boolean): String {
        return portal4 + if (allowIpv6) ",$portal6" else ""
    }

    fun dnsSpec(allowIpv6: Boolean): String {
        return dns4 + if (allowIpv6) ",$dns6" else ""
    }

    companion object {
        private const val UID_PER_USER = 100000
        private const val DEFAULT_BUCKET = 19
        private const val IPV4_PREFIX = 30
        private const val IPV6_PREFIX = 126

        fun resolve(autoIsolation: Boolean, uid: Int): TunAddress {
            val userId = max(uid, 0) / UID_PER_USER
            val bucket = resolveBucket(autoIsolation, userId)

            return TunAddress(
                gateway4 = "172.$bucket.0.1",
                portal4 = "172.$bucket.0.2",
                prefix4 = IPV4_PREFIX,
                gateway6 = ipv6Address(bucket, 1),
                portal6 = ipv6Address(bucket, 2),
                prefix6 = IPV6_PREFIX,
                androidUserId = userId,
                bucket = bucket,
                isolated = autoIsolation && userId != 0,
            )
        }

        private fun resolveBucket(autoIsolation: Boolean, userId: Int): Int {
            if (!autoIsolation || userId == 0) {
                return DEFAULT_BUCKET
            }

            return when (userId) {
                10 -> 20
                999 -> 29
                else -> 20 + userId % 10
            }
        }

        private fun ipv6Address(bucket: Int, suffix: Int): String {
            return if (bucket == DEFAULT_BUCKET) {
                "fdfe:dcba:9876::$suffix"
            } else {
                "fdfe:dcba:9876:$bucket::$suffix"
            }
        }
    }
}
