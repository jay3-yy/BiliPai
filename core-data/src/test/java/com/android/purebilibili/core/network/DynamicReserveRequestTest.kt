package com.android.purebilibili.core.network

import com.android.purebilibili.data.model.response.DynamicReserveClickRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicReserveRequestTest {
    @Test
    fun `reserve and cancel send JSON with CSRF in the query and decode server updates`() = runBlocking {
        val dynamicId = "1063487284684259332"
        val actions = listOf(
            DynamicReserveClickRequest(42, 0, dynamicId, 0),
            DynamicReserveClickRequest(42, 1, dynamicId, 1),
        )
        var requestIndex = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = actions[requestIndex++]
            assertEquals("POST", request.method)
            assertEquals("/x/dynamic/feed/reserve/click", request.url.encodedPath)
            assertEquals("test-csrf", request.url.queryParameter("csrf"))
            val body = requireNotNull(request.body)
            assertEquals("application", body.contentType()?.type)
            assertEquals("json", body.contentType()?.subtype)
            val buffer = Buffer()
            body.writeTo(buffer)
            assertEquals(buildJsonObject {
                put("reserve_id", JsonPrimitive(42L))
                put("cur_btn_status", JsonPrimitive(action.cur_btn_status))
                put("dynamic_id_str", JsonPrimitive(dynamicId))
                put("reserve_total", JsonPrimitive(action.reserve_total))
            }, Json.parseToJsonElement(buffer.readUtf8()))
            val finalStatus = if (action.cur_btn_status == 0) 1 else 0
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"code":0,"data":{"desc_update":"${finalStatus}人预约","reserve_update":$finalStatus,"final_btn_status":$finalStatus}}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        try {
            val api = Retrofit.Builder()
                .baseUrl("https://api.bilibili.com/")
                .client(client)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DynamicApi::class.java)
            actions.forEachIndexed { index, action ->
                val data = requireNotNull(api.clickDynamicReserve("test-csrf", action).data)
                val expectedStatus = 1 - index
                assertEquals(expectedStatus, data.final_btn_status)
                assertEquals(expectedStatus.toLong(), data.reserve_update)
                assertEquals("${expectedStatus}人预约", data.desc_update)
            }
            assertEquals(2, requestIndex)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
