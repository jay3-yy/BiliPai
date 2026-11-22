package com.android.purebilibili.core.network

import com.android.purebilibili.core.network.policy.GithubMirrorSettings
import com.android.purebilibili.core.network.policy.resolveGithubMirrorUrl
import com.android.purebilibili.core.store.GithubMirrorStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rewrites eligible public GitHub resource GET/HEAD requests to the configured mirror
 * when global GitHub mirroring is enabled, falling back directly to the original URL
 * on network errors or unsuccessful HTTP responses.
 */
class GithubMirrorInterceptor(
    private val settingsProvider: () -> GithubMirrorSettings = GithubMirrorStore::getSync,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.method != "GET" && original.method != "HEAD") {
            return chain.proceed(original)
        }
        if (original.header("Authorization") != null || original.header("Cookie") != null) {
            return chain.proceed(original)
        }
        val mirrorUrl = resolveGithubMirrorUrl(settingsProvider(), original.url.toString())
            ?: return chain.proceed(original)
        val mirrorHttpUrl = mirrorUrl.toHttpUrlOrNull() ?: return chain.proceed(original)
        val mirroredRequest = original.newBuilder()
            .url(mirrorHttpUrl)
            .removeHeader("Authorization")
            .removeHeader("Cookie")
            .build()

        val mirrorResponse = try {
            chain.proceed(mirroredRequest)
        } catch (error: IOException) {
            if (chain.call().isCanceled()) throw error
            CoreDataLog.w("GithubMirror", "Mirror request failed (${mirrorHttpUrl.host}), falling back to GitHub: ${error.message}")
            return chain.proceed(original)
        }

        if (mirrorResponse.isSuccessful || mirrorResponse.code == 304) {
            return mirrorResponse
        }

        val failedCode = mirrorResponse.code
        mirrorResponse.close()
        CoreDataLog.w("GithubMirror", "Mirror returned HTTP $failedCode (${mirrorHttpUrl.host}), falling back to GitHub")
        return chain.proceed(original)
    }
}
