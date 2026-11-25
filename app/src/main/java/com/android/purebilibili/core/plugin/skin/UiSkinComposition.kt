package com.android.purebilibili.core.plugin.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.purebilibili.data.model.response.EmoteItem
import com.android.purebilibili.data.model.response.EmotePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val LocalUiSkinState = compositionLocalOf { UiSkinState() }

private const val LOCAL_SKIN_EMOTE_PACKAGE_ID = -990_001L

fun UiSkinState.toSkinEmotePackage(): EmotePackage? {
    val skin = activeSkin?.takeIf { enabled } ?: return null
    val emotes = skin.manifest.assets.emojiImages.entries.mapIndexedNotNull { index, (name, assetPath) ->
        val filePath = skin.assetFilePath(assetPath) ?: return@mapIndexedNotNull null
        val code = if (name.startsWith("[") && name.endsWith("]")) name else "[$name]"
        EmoteItem(
            id = LOCAL_SKIN_EMOTE_PACKAGE_ID - index - 1,
            text = code,
            url = File(filePath).toURI().toString(),
        )
    }
    if (emotes.isEmpty()) return null
    val coverIconUrl = skin.assetFilePath(skin.manifest.assets.coverImage)
        ?.let { File(it).toURI().toString() }
        ?: emotes.first().url
    return EmotePackage(
        id = LOCAL_SKIN_EMOTE_PACKAGE_ID,
        text = skin.displayName,
        url = coverIconUrl,
        emote = emotes,
    )
}

fun parseUiSkinColor(value: String?, fallback: Color): Color {
    val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
    return runCatching {
        Color(android.graphics.Color.parseColor(if (normalized.startsWith('#')) normalized else "#$normalized"))
    }.getOrDefault(fallback)
}

fun UiSkinState.assetPath(
    surface: UiSkinSurface,
    selector: (UiSkinAssets) -> String?
): String? {
    val skin = activeSkin?.takeIf { enabled && surface in it.manifest.surfaces } ?: return null
    return skin.assetFilePath(selector(skin.manifest.assets))
}

@Composable
fun UiSkinAnimatedAsset(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    iterations: Int = LottieConstants.IterateForever,
    contentDescription: String? = null,
) {
    if (path.endsWith(".json", ignoreCase = true)) {
        val jsonText by produceState<String?>(initialValue = null, key1 = path) {
            value = withContext(Dispatchers.IO) {
                runCatching { File(path).readText() }.getOrNull()
            }
        }
        val text = jsonText ?: return
        val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(text))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = iterations,
            isPlaying = animationGate.active,
            restartOnPlay = false,
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier.size(size).then(animationGate.modifier),
        )
    } else {
        AsyncImage(
            model = File(path),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    }
}
