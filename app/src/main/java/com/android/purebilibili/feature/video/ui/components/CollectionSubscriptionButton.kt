package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.AppContentColors

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.repository.ActionRepository
import kotlinx.coroutines.launch

@Composable
internal fun CollectionSubscriptionButton(
    collectionId: Long,
    currentBvid: String,
    currentAid: Long,
    fontSize: TextUnit,
    immersive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
) {
    key(collectionId) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val subscriptionHaptic = com.android.purebilibili.core.util.rememberVisibleHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
        val cachedSubscription = remember(context, collectionId) {
            SettingsManager.getCollectionSubscriptionState(context, collectionId)
        }
        val cachedIsSubscribed by cachedSubscription.collectAsStateWithLifecycle(initialValue = null)
        var remoteIsSubscribed by remember(collectionId) {
            mutableStateOf<Boolean?>(null)
        }
        var isUpdating by remember(collectionId) { mutableStateOf(false) }
        var queryFailed by remember { mutableStateOf(false) }
        var retryRequest by remember { mutableStateOf(0) }
        val isSubscribed = remoteIsSubscribed ?: cachedIsSubscribed

        LaunchedEffect(collectionId, currentBvid, currentAid, isUpdating, retryRequest) {
            if (collectionId <= 0L || currentBvid.isBlank() || isUpdating) return@LaunchedEffect
            val result = ActionRepository.checkCollectionSubscriptionStatus(bvid = currentBvid, aid = currentAid)
            currentCoroutineContext().ensureActive()
            result
                .onSuccess { subscribed ->
                    remoteIsSubscribed = subscribed
                    SettingsManager.setCollectionSubscription(context, collectionId, subscribed)
                }
                .onFailure { queryFailed = true }
        }

        AppTextButton(
            enabled = !isUpdating && (isSubscribed != null || queryFailed),
            onClick = {
                if (isSubscribed == null) {
                    queryFailed = false
                    retryRequest += 1
                    return@AppTextButton
                }
                if (collectionId <= 0L) {
                    Toast.makeText(context, "无法识别合集 ID", Toast.LENGTH_SHORT).show()
                    return@AppTextButton
                }
                if (isUpdating) return@AppTextButton
                isUpdating = true
                scope.launch {
                    try {
                        val targetSubscribed = !(isSubscribed ?: return@launch)
                        ActionRepository
                            .setCollectionSubscription(
                                seasonId = collectionId,
                                subscribe = targetSubscribed
                            )
                            .onSuccess { subscribed ->
                                currentCoroutineContext().ensureActive()
                                if (subscribed != isSubscribed) subscriptionHaptic(HapticType.LIGHT)
                                remoteIsSubscribed = subscribed
                                SettingsManager.setCollectionSubscription(context, collectionId, subscribed)
                                Toast.makeText(
                                    context,
                                    if (subscribed) "已订阅合集" else "已取消订阅合集",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: "合集订阅操作失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } finally {
                        isUpdating = false
                    }
                }
            },
            contentPadding = contentPadding
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 按最长文案测量，缓存与服务器回填只更新文字，不改变合集行尾宽度。
                AppText(
                    text = "已订阅",
                    fontSize = fontSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {},
                )
                AppText(
                    text = when (isSubscribed) { true -> "已订阅"; false -> "订阅"; null -> if (queryFailed) "重试" else "…" },
                    color = if (immersive) {
                        Color.White.copy(alpha = if (isSubscribed == true) 0.92f else 0.78f)
                    } else if (isSubscribed == true) {
                        AppContentColors.accent()
                    } else {
                        AppContentColors.secondaryText()
                    },
                    fontSize = fontSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
