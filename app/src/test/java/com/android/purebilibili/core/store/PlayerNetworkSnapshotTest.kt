package com.android.purebilibili.core.store

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class PlayerNetworkSnapshotTest {
    @Test
    fun `network handoff and disconnect update player snapshot without repeating system queries`() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("data_saver", Context.MODE_PRIVATE)
        preferences.edit().putInt("mode", SettingsManager.DataSaverMode.MOBILE_ONLY.value).commit()
        val wifi = mockk<Network>()
        val mobile = mockk<Network>()
        val wifiCapabilities = NetworkCapabilities.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val mobileCapabilities = NetworkCapabilities.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build()
        val callback = slot<ConnectivityManager.NetworkCallback>()
        val connectivity = mockk<ConnectivityManager>()
        every { connectivity.activeNetwork } returns wifi
        every { connectivity.getNetworkCapabilities(wifi) } returns wifiCapabilities
        every { connectivity.registerDefaultNetworkCallback(capture(callback), any<Handler>()) } just Runs
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("data_saver", Context.MODE_PRIVATE) } returns preferences
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivity

        val state = DataSaverState.get(context)
        assertTrue(state.isOnWifi)
        assertFalse(state.active.value)

        callback.captured.onAvailable(mobile)
        callback.captured.onCapabilitiesChanged(mobile, mobileCapabilities)
        assertFalse(state.isOnWifi)
        assertTrue(state.active.value)
        // 旧默认网络迟到的断开通知不能清掉当前移动网络。
        callback.captured.onLost(wifi)
        assertTrue(state.active.value)

        callback.captured.onAvailable(wifi)
        callback.captured.onCapabilitiesChanged(wifi, wifiCapabilities)
        assertTrue(state.isOnWifi)
        assertFalse(state.active.value)
        callback.captured.onLost(wifi)
        assertFalse(state.isOnWifi)
        assertFalse(state.active.value)
        verify(exactly = 1) { connectivity.activeNetwork }
        verify(exactly = 1) { connectivity.getNetworkCapabilities(any()) }
    }
}
