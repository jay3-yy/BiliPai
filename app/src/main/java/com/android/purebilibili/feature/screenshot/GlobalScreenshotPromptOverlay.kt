package com.android.purebilibili.feature.screenshot

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import kotlinx.coroutines.delay

private const val GLOBAL_SCREENSHOT_PROMPT_TIMEOUT_MS = 8_000L

@Composable
fun GlobalScreenshotPromptOverlay(
    previewBitmap: Bitmap?,
    watermarkContext: AppScreenshotWatermarkContext? = null,
    busy: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalSystemReduceMotion.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 仅在应用处于前台 RESUMED 状态时计时，避免系统截屏编辑页抢占期间提前收起浮层。
    LaunchedEffect(previewBitmap, busy, lifecycleOwner) {
        if (previewBitmap != null && !busy) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(GLOBAL_SCREENSHOT_PROMPT_TIMEOUT_MS)
                onDismiss()
            }
        }
    }

    val safePadding = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(safePadding)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = previewBitmap != null,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(AppMotionTokens.standardSpec()) +
                    slideInVertically(AppMotionTokens.standardSpec()) { it / 2 }
            },
            exit = if (reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(AppMotionTokens.expressiveSpec()) +
                    slideOutVertically(AppMotionTokens.expressiveSpec()) { it / 2 }
            },
        ) {
            val bitmap = previewBitmap ?: return@AnimatedVisibility
            val context = LocalContext.current
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            val appIconImageBitmap = remember(context) {
                renderAppIconBitmap(context = context, sizePx = 48)?.asImageBitmap()
            }
            val shape = RoundedCornerShape(20.dp)
            val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

            AppSurface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, outlineColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 缩略图 + 顶部品牌水印示意条
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(0.75.dp, outlineColor, RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (appIconImageBitmap != null) {
                                Image(
                                    bitmap = appIconImageBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            AppText(
                                text = "BiliPai",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "截图预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = "带 BiliPai 水印分享",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitleText = remember(watermarkContext) {
                                    buildWatermarkVideoMetadataLine(
                                        title = watermarkContext?.title,
                                        bvid = watermarkContext?.bvid,
                                    )?.let { "附带：$it" } ?: "自动拼接顶部客户端标识"
                                }
                                AppText(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                enabled = !busy,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ms_close_24),
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppButton(
                                onClick = onShare,
                                enabled = !busy,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                AppText(
                                    text = if (busy) "生成中..." else "分享",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                            AppOutlinedButton(
                                onClick = onSave,
                                enabled = !busy,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                AppText(
                                    text = "保存",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
