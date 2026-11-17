package com.android.purebilibili.core.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.Proxy
import java.net.UnknownHostException

class NetworkFailureEventListenerTest {
    @Test
    fun dnsFailureRecordsStageWithoutRequestSecrets() {
        val previousConfig = CoreNetworkRuntime.config
        val messages = mutableListOf<String>()
        CoreNetworkRuntime.config = CoreNetworkConfig(log = { _, _, message, _ -> messages.add(message) })
        try {
            val client = OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .dns(Dns { throw UnknownHostException("resolver unavailable") })
                .eventListenerFactory { NetworkFailureEventListener() }
                .build()
            val request = Request.Builder()
                .url("https://network-test.invalid/private-path?token=private-token")
                .header("Cookie", "session=private-cookie")
                .build()

            assertThrows(UnknownHostException::class.java) { client.newCall(request).execute() }

            assertEquals(1, messages.size)
            val message = messages.single()
            assertTrue(message.contains("host=network-test.invalid"))
            assertTrue(message.contains("dnsStart"))
            assertTrue(message.contains("UnknownHostException"))
            listOf("private-path", "private-token", "private-cookie").forEach {
                assertFalse(message.contains(it))
            }
        } finally {
            CoreNetworkRuntime.config = previousConfig
        }
    }

    @Test
    fun canceledCallDoesNotReportNetworkFault() {
        val previousConfig = CoreNetworkRuntime.config
        val messages = mutableListOf<String>()
        CoreNetworkRuntime.config = CoreNetworkConfig(log = { _, _, message, _ -> messages.add(message) })
        try {
            val client = OkHttpClient.Builder()
                .eventListenerFactory { NetworkFailureEventListener() }
                .build()
            val call = client.newCall(Request.Builder().url("https://network-test.invalid").build())
            call.cancel()

            assertThrows(IOException::class.java) { call.execute() }

            assertTrue(messages.isEmpty())
        } finally {
            CoreNetworkRuntime.config = previousConfig
        }
    }
}
