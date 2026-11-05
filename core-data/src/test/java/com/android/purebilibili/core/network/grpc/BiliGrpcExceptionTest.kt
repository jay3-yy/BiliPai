package com.android.purebilibili.core.network.grpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BiliGrpcExceptionTest {
    @Test
    fun `numeric business failure preserves structured status`() {
        val failure = BiliGrpcException(200, "2", "12006")
        assertEquals(200, failure.httpCode)
        assertEquals("2", failure.grpcStatus)
        assertEquals("12006", failure.grpcMessage)
        assertEquals(12006, failure.businessCode)
        assertEquals(-412, BiliGrpcException(200, "2", "-412").businessCode)
    }

    @Test
    fun `transport errors and text containing digits are not business errors`() {
        for (failure in listOf(
            BiliGrpcException(503, "2", "12006"),
            BiliGrpcException(200, "7", "12006"),
            BiliGrpcException(200, null, "12006"),
            BiliGrpcException(200, "2", "error 12006"),
            BiliGrpcException(200, "2", "12006000000000000000000"),
            BiliGrpcException(200, "2", null),
        )) {
            assertNull(failure.businessCode)
        }
    }
}
