package com.android.purebilibili.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor

/** 普通响应协商 br/gzip；分段媒体请求保留字节范围与原有编码语义。 */
internal object ResponseCompressionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        if (chain.request().header("Range") != null) {
            chain.proceed(chain.request())
        } else {
            BrotliInterceptor.intercept(chain)
        }
}
