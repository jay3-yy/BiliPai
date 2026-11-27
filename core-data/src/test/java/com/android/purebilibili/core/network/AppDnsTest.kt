package com.android.purebilibili.core.network

import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppDnsTest {

    @Test
    fun `network switch isolates in-flight lookup and drops stale cache write-back`() {
        var now = 1_000L
        val cache = AppDnsCache(nowMs = { now })
        val oldStarted = CountDownLatch(1)
        val allowOldFinish = CountDownLatch(1)
        val lookupCount = AtomicInteger(0)
        val oldIp = InetAddress.getByAddress("api.bilibili.com", byteArrayOf(10, 0, 0, 1))
        val newIp = InetAddress.getByAddress("api.bilibili.com", byteArrayOf(10, 0, 0, 2))

        val dns = buildCachedAppDns(cache = cache) { _ ->
            when (lookupCount.incrementAndGet()) {
                1 -> {
                    oldStarted.countDown()
                    assertTrue(allowOldFinish.await(2, TimeUnit.SECONDS))
                    listOf(oldIp)
                }
                else -> listOf(newIp)
            }
        }

        val oldResult = AtomicReference<List<InetAddress>>()
        val oldThread = thread(isDaemon = true) {
            oldResult.set(dns.lookup("api.bilibili.com"))
        }

        assertTrue(oldStarted.await(2, TimeUnit.SECONDS))
        // 模拟切网（Wi-Fi / 蜂窝 / VPN）：推进网络代次并清空缓存。
        cache.clear()

        // 新代次请求必须发起独立解析，不能复用到旧在途解析。
        val freshAfterSwitch = dns.lookup("api.bilibili.com")
        assertEquals(listOf(newIp), freshAfterSwitch)
        assertEquals(2, lookupCount.get())

        // 放行旧网络在途解析完成：不得把 oldIp 写回新代次缓存。
        allowOldFinish.countDown()
        oldThread.join(2_000)
        assertEquals(listOf(oldIp), oldResult.get())

        // 再次查询必须仍命中新代次缓存 newIp，且不会再次触发系统解析。
        now += 1_000L
        assertEquals(listOf(newIp), dns.lookup("api.bilibili.com"))
        assertEquals(2, lookupCount.get())
    }

    @Test
    fun `network dns signature reflects dns servers private dns toggle and server name`() {
        val plain = buildNetworkDnsSignature(
            dnsServerAddresses = listOf("1.1.1.1", "8.8.8.8"),
            isPrivateDnsActive = false,
            privateDnsServerName = null,
        )
        val privateOpportunistic = buildNetworkDnsSignature(
            dnsServerAddresses = listOf("1.1.1.1", "8.8.8.8"),
            isPrivateDnsActive = true,
            privateDnsServerName = null,
        )
        val privateStrictGoogle = buildNetworkDnsSignature(
            dnsServerAddresses = listOf("1.1.1.1", "8.8.8.8"),
            isPrivateDnsActive = true,
            privateDnsServerName = "dns.google",
        )
        val privateStrictCloudflare = buildNetworkDnsSignature(
            dnsServerAddresses = listOf("1.1.1.1", "8.8.8.8"),
            isPrivateDnsActive = true,
            privateDnsServerName = "one.one.one.one",
        )

        assertNotEquals(plain, privateOpportunistic)
        assertNotEquals(privateOpportunistic, privateStrictGoogle)
        assertNotEquals(privateStrictGoogle, privateStrictCloudflare)
    }

    @Test
    fun `first available network and reconnect always invalidate dns`() {
        val tracker = NetworkDnsInvalidationTracker()
        assertTrue(tracker.onAvailable("net-wifi-1"))
        assertFalse(tracker.onAvailable("net-wifi-1"))
        assertTrue(tracker.onLost("net-wifi-1"))
        assertTrue(tracker.onAvailable("net-wifi-1"))
        assertTrue(tracker.onAvailable("net-cell-2"))
    }

    @Test
    fun `only the current default network can update the dns baseline`() {
        val tracker = NetworkDnsInvalidationTracker()
        val plain = buildNetworkDnsSignature(listOf("1.1.1.1"), isPrivateDnsActive = false, privateDnsServerName = null)
        val privateDns = buildNetworkDnsSignature(listOf("1.1.1.1"), isPrivateDnsActive = true, privateDnsServerName = "dns.google")

        assertTrue(tracker.onAvailable("net-wifi-1"))
        assertTrue(tracker.onAvailable("net-cell-2"))
        assertFalse(tracker.onLost("net-wifi-1"))
        assertFalse(tracker.onLinkPropertiesChanged("net-wifi-1", privateDns))
        assertFalse(tracker.onLinkPropertiesChanged("net-cell-2", plain))
        assertTrue(tracker.onLinkPropertiesChanged("net-cell-2", privateDns))
    }
}
