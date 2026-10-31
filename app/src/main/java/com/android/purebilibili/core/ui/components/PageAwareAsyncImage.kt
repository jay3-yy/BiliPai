package com.android.purebilibili.core.ui.components

import android.graphics.drawable.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.DrawableImage
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.store.DataSaverState
import com.android.purebilibili.core.util.withBiliImageQuality
import com.android.purebilibili.feature.home.LocalHomeCardCoverLoadingAllowed

/** 页面覆盖、滚动及返回时频繁切换，只使实际读取加载许可的组件失效。 */
internal val LocalPageImageLoadingAllowed = compositionLocalOf { true }

/**
 * Removing AsyncImage cancels its request and forgets its animated painter. Keep only the
 * decoded result for a static replay; do not change the request/cache identity on resume.
 */
@Composable
internal fun PageAwareAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    loadingAllowed: Boolean = LocalPageImageLoadingAllowed.current,
    dimInDarkTheme: Boolean = false,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolvedImageLoader = imageLoader ?: context.imageLoader
    val imageSettings = LocalContentImageSettings.current
    val darkTheme = LocalContentImageDarkTheme.current
    val imageFilter = remember(dimInDarkTheme, darkTheme, imageSettings.darkDimPercent) {
        contentImageDimFilter(dimInDarkTheme && darkTheme, imageSettings.darkDimPercent)
    }
    // 请求和暂停时的缓存回放使用同一质量身份，避免返回列表时丢失封面占位。
    val dataSaver by remember(context) { DataSaverState.get(context).active }
    // 首页封面、头像直接显示。只按是否属于首页选择策略，不把滚动/加载许可作为请求 key，
    // 避免滚动启停重建请求；其他列表继续使用各自的淡入配置。
    val homeFeedImage = LocalHomeCardCoverLoadingAllowed.current != null
    val request = remember(model, dataSaver, imageSettings.listQuality, homeFeedImage, context) {
        val source = model as? ImageRequest
            ?: if (model != null) ImageRequest.Builder(context).data(model).build() else null
        source?.withBiliImageQuality(dataSaver, imageSettings.listQuality)?.let {
            if (homeFeedImage) it.newBuilder().crossfade(false).build() else it
        }
    }
    val imageKey = request?.data ?: model
    var retainedImage by remember(imageKey, request?.memoryCacheKey, request?.memoryCacheKeyExtras, resolvedImageLoader) {
        mutableStateOf<coil3.Image?>(null)
    }
    // A cached placeholder can already be visible before onSuccess, or this slot
    // can remount while its page is paused. Replay only this request's exact cache
    // identity; pausing must not discard a decoded cover or start another request.
    val cachedImage = remember(
        imageKey, request?.memoryCacheKey, request?.memoryCacheKeyExtras, request?.memoryCachePolicy,
        resolvedImageLoader, loadingAllowed,
    ) {
        if (!loadingAllowed && request?.memoryCachePolicy?.readEnabled == true) {
            request.memoryCacheKey?.let { key ->
                resolvedImageLoader.memoryCache?.get(MemoryCache.Key(key, request.memoryCacheKeyExtras))?.image
            }
        } else null
    }
    val replayImage = retainedImage ?: cachedImage
    val retainedPainter = remember(replayImage, context) {
        replayImage?.asPainter(context, FilterQuality.Low)
    }
    // 首页静态位图已成功加载且关闭淡入，不再有加载或动画工作。保留同一个
    // AsyncImage，避免开合详情时把所有封面/头像拆成 Image 再重新挂载请求。
    // 未完成的请求仍立即取消；动图仍走静态回放，不能保活其逐帧回调。
    val keepDecodedHomeImageMounted = homeFeedImage && retainedImage is BitmapImage
    if (loadingAllowed || keepDecodedHomeImageMounted) {
        AsyncImage(
            model = request ?: model,
            imageLoader = resolvedImageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = retainedPainter,
            contentScale = contentScale,
            colorFilter = imageFilter,
            alignment = alignment,
            onSuccess = {
                retainedImage = it.result.image
                onSuccess?.invoke(it)
            },
            onError = onError,
        )
    } else {
        // AsyncImage's forgotten callback runs during apply. Restore drawable visibility
        // afterwards for static drawing, without restarting its frame clock.
        SideEffect {
            (replayImage as? DrawableImage)?.drawable?.let { drawable ->
                drawable.setVisible(true, false)
                (drawable as? Animatable)?.stop()
            }
        }
        if (retainedPainter != null) {
            Image(
                painter = retainedPainter,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = imageFilter,
                alignment = alignment,
            )
        } else {
            Box(modifier.semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            })
        }
    }
}
