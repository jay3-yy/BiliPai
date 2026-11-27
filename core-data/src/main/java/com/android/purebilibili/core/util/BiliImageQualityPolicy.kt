package com.android.purebilibili.core.util

/** 列表图片按受支持 CDN 的压缩质量与视口宽度桶裁剪，保留已有尺寸与查询参数边界。 */
object BiliImageQualityPolicy {
    data class ResolvedImageUrl(
        val url: String,
        val appliedWidthBucket: Int? = null,
    )

    const val HIGH_QUALITY = 100
    const val DATA_SAVER_QUALITY = 10
    private val cdnWidthBuckets = intArrayOf(240, 320, 480, 672, 960, 1280)
    private val supportedHosts = arrayOf("hdslb.com", "biliimg.com")
    private val qualityParameter = Regex("(?:^|_)\\d+q(?=_|$)")
    private val widthParameter = Regex("(?:^|_)\\d+w(?=_|$)")
    private val rasterExtension = Regex("\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)

    /** 将目标像素宽度量化到标准 CDN 边缘缓存桶；超大尺寸返回 null 保留原宽。 */
    fun resolveCdnWidthBucket(targetWidthPx: Int): Int? {
        if (targetWidthPx <= 0) return null
        return cdnWidthBuckets.firstOrNull { it >= targetWidthPx }
    }

    fun supportedCdnWidthBuckets(): List<Int> = cdnWidthBuckets.toList()

    fun resolveUrl(url: String, dataSaver: Boolean): String =
        resolveUrl(url, if (dataSaver) DATA_SAVER_QUALITY else HIGH_QUALITY)

    /** 大图预览的质量独立于列表；用户选择原图时保留源地址。 */
    fun resolvePreviewUrl(url: String, quality: Int, originalQuality: Boolean): String =
        if (originalQuality || quality <= 0) url else resolveUrl(url, quality)

    fun resolveUrl(url: String, quality: Int, targetWidthPx: Int? = null): String =
        resolveUrlWithMetadata(url, quality, targetWidthPx).url

    /**
     * 解析最终图片 URL 并返回本次实际追加到 URL 中的 CDN 宽度桶；
     * 若原 URL 已自带宽度参数（如 `640w`）或不受支持，则 [ResolvedImageUrl.appliedWidthBucket] 为 null，
     * 避免预取与显示路径因附带未生效的宽度桶而产生不同的缓存键。
     */
    fun resolveUrlWithMetadata(
        url: String,
        quality: Int,
        targetWidthPx: Int? = null,
    ): ResolvedImageUrl {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()
            ?: return ResolvedImageUrl(url)
        if (!supportedHosts.any { host == it || host.endsWith(".$it") }) return ResolvedImageUrl(url)
        // 签名和查询参数地址、动图与装饰素材保持服务端原有语义。
        if ('?' in url || '#' in url || "/bfs/emote/" in url || "/bfs/garb/" in url) {
            return ResolvedImageUrl(url)
        }
        val original = url.substringBefore('@')
        if (!rasterExtension.containsMatchIn(original)) return ResolvedImageUrl(url)
        val safeQuality = quality.coerceIn(1, 100)
        val suffix = url.substringAfter('@', "")
        // 命名预设由 CDN 定义，不在客户端拼接未知指令。
        if (suffix.startsWith('!')) return ResolvedImageUrl(url)
        val transform = suffix.substringBeforeLast('.', suffix)
            .replace(qualityParameter, "").trim('_')
        val widthBucket = targetWidthPx
            ?.takeUnless { widthParameter.containsMatchIn(transform) }
            ?.let(::resolveCdnWidthBucket)
        val sizedTransform = when {
            widthBucket == null -> transform
            transform.isEmpty() -> "${widthBucket}w"
            else -> "${transform}_${widthBucket}w"
        }
        val parameters = if (sizedTransform.isEmpty()) "${safeQuality}q" else "${sizedTransform}_${safeQuality}q"
        return ResolvedImageUrl(
            url = "$original@$parameters.webp",
            appliedWidthBucket = widthBucket,
        )
    }
}
