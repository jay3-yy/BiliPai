package com.android.purebilibili.feature.login

import android.app.Application
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class GeetestConfigPolicyTest {
    @Test
    fun serviceRejectionRetainsItsReasonWithoutDumpingResponse() {
        val error = assertFailsWith<IllegalStateException> {
            parseGeetestConfig(
                """{"status":"error","error":"invalid gt","challenge":"session-secret"}""",
                "test-gt", "session-secret",
            )
        }
        assertEquals("极验配置服务拒绝请求: invalid gt", error.message)
        assertFalse(error.message.orEmpty().contains("session-secret"))
    }

    @Test
    fun successKeepsDynamicConfigurationAndBindsCurrentChallenge() {
        val config = JSONObject(parseGeetestConfig(
            """({"status":"success","data":{"type":"fullpage","static_servers":["static.geetest.com"]}})""",
            "current-gt", "current-challenge",
        ))
        assertEquals("fullpage", config.getString("type"))
        assertEquals("static.geetest.com", config.getJSONArray("static_servers").getString(0))
        assertEquals("current-gt", config.getString("gt"))
        assertEquals("current-challenge", config.getString("challenge"))
    }

    @Test
    fun successWithoutConfigurationIsNotTreatedAsUsableCaptcha() {
        assertFailsWith<IllegalStateException> {
            parseGeetestConfig("""{"status":"success"}""", "test-gt", "test-challenge")
        }
    }
}
