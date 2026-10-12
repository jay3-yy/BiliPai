package com.android.purebilibili.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentGrpcPaginationPolicyTest {
    @Test
    fun `grpc paged request continues only with first page or cursor offset`() {
        assertTrue(CommentRepository.shouldTryGrpcPagedRequest(page = 1, paginationOffset = null))
        assertTrue(CommentRepository.shouldTryGrpcPagedRequest(page = 2, paginationOffset = "next-offset"))
        assertFalse(CommentRepository.shouldTryGrpcPagedRequest(page = 2, paginationOffset = null))
    }

    @Test
    fun `later grpc pages reject empty and whitespace cursor offsets`() {
        assertFalse(CommentRepository.shouldTryGrpcPagedRequest(page = 2, paginationOffset = ""))
        assertFalse(CommentRepository.shouldTryGrpcPagedRequest(page = 2, paginationOffset = " \t\n"))
    }

    @Test
    fun `first grpc page does not require a cursor offset`() {
        assertTrue(CommentRepository.shouldTryGrpcPagedRequest(page = 1, paginationOffset = ""))
        assertTrue(CommentRepository.shouldTryGrpcPagedRequest(page = 1, paginationOffset = " \t\n"))
    }
}
