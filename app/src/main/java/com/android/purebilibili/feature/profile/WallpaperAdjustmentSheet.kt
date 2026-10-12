package com.android.purebilibili.feature.profile

import coil3.request.crossfade
import coil3.request.allowHardware

import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform
import com.android.purebilibili.core.ui.wallpaper.applyGestureToProfileWallpaperTransform
import com.android.purebilibili.core.ui.wallpaper.sanitizeProfileWallpaperTransform
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.util.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperAdjustmentSheet(
    imageUri: String,
    initialMobileBias: Float = 0f,
    initialTabletBias: Float = 0f,
    onSave: (mobileBias: Float, tabletBias: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewLoaded by remember(imageUri) { mutableStateOf(false) }
    var previewFailed by remember(imageUri) { mutableStateOf(false) }
    val previewRequest = remember(context, imageUri) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mobile, 1: Tablet
    var mobileBias by remember { mutableFloatStateOf(initialMobileBias) }
    var tabletBias by remember { mutableFloatStateOf(initialTabletBias) }
    
    val currentBias = if (selectedTab == 0) mobileBias else tabletBias
    
    // Bottom Sheet
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                AppText(
                    text = "取消",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onDismiss() },
                    color = MaterialTheme.colorScheme.primary
                )
                
                AppText(
                    text = "调整壁纸位置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                AppTextButton(
                    onClick = { onSave(mobileBias, tabletBias) },
                    enabled = previewLoaded && !previewFailed,
                    modifier = Modifier.align(Alignment.CenterEnd).sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    AppText(text = "保存", fontWeight = FontWeight.Bold)
                }
            }
            
            // Device target switcher follows the active native theme; liquid glass reuses the
            // shared moving indicator when the global glass option is enabled.
            WallpaperDeviceTabRow(
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp) // Fixed height container for preview
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                // Device Mock Frame
                val aspectRatio = if (selectedTab == 0) 9f / 18f else 16f / 10f
                val width = if (selectedTab == 0) 140.dp else 280.dp
                val height = width / aspectRatio
                val previewCornerRadius = if (selectedTab == 0) 16.dp else 12.dp
                
                // Card simulating the device screen
                AppCard(
                    shape = AppCardShape.Uniform(previewCornerRadius),
                    modifier = Modifier
                        .size(width = width, height = height)
                        .shadow(8.dp, RoundedCornerShape(previewCornerRadius)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = previewRequest,
                            onLoading = {
                                previewLoaded = false
                                previewFailed = false
                            },
                            onSuccess = {
                                previewLoaded = true
                                previewFailed = false
                            },
                            onError = {
                                previewLoaded = false
                                previewFailed = true
                                Logger.w("WallpaperAdjustment", "Wallpaper preview failed", it.result.throwable)
                            },
                            contentDescription = null,
                            alignment = androidx.compose.ui.BiasAlignment(0f, currentBias),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Fake UI Overlay to help context
                        // Gradient Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp) // Fake header height
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                        )
                        
                        // Text Overlay hint
                        if (previewLoaded) {
                            AppText(
                                text = "预览效果",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.3f), AppShapes.container(ContainerLevel.Tag))
                                    .padding(4.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (!previewFailed) {
                                    AppCircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(12.dp))
                                }
                                AppText(
                                    text = if (previewFailed) "图片加载失败，请返回重新选择" else "正在加载图片…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Slider Control
            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText("顶部对齐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    AppText("居中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    AppText("底部对齐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                
                AppSlider(
                    value = currentBias,
                    onValueChange = { newValue ->
                        if (selectedTab == 0) mobileBias = newValue else tabletBias = newValue
                    },
                    valueRange = -1f..1f,
                    steps = 0, // Continuous
                    modifier = Modifier.fillMaxWidth()
                )
                
                AppText(
                    text = "上下拖动滑块调整图片显示区域",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileWallpaperAdjustmentSheet(
    imageUri: String,
    initialMobileTransform: ProfileWallpaperTransform = ProfileWallpaperTransform(),
    initialTabletTransform: ProfileWallpaperTransform = ProfileWallpaperTransform(),
    onSave: (mobileTransform: ProfileWallpaperTransform, tabletTransform: ProfileWallpaperTransform) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var mobileTransform by remember {
        mutableStateOf(sanitizeProfileWallpaperTransform(initialMobileTransform))
    }
    var tabletTransform by remember {
        mutableStateOf(sanitizeProfileWallpaperTransform(initialTabletTransform))
    }

    val currentTransform = if (selectedTab == 0) mobileTransform else tabletTransform
    val currentTransformState = rememberUpdatedState(currentTransform)
    fun updateCurrentTransform(transform: ProfileWallpaperTransform) {
        if (selectedTab == 0) {
            mobileTransform = sanitizeProfileWallpaperTransform(transform)
        } else {
            tabletTransform = sanitizeProfileWallpaperTransform(transform)
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                AppText(
                    text = "取消",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onDismiss() },
                    color = MaterialTheme.colorScheme.primary
                )

                AppText(
                    text = "调整壁纸位置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                AppText(
                    text = "保存",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onSave(mobileTransform, tabletTransform) },
                    color = MaterialTheme.colorScheme.primary
                )
            }

            WallpaperDeviceTabRow(
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(328.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                val aspectRatio = if (selectedTab == 0) 9f / 18f else 16f / 10f
                val width = if (selectedTab == 0) 150.dp else 292.dp
                val height = width / aspectRatio
                val previewCornerRadius = if (selectedTab == 0) 16.dp else 12.dp

                AppCard(
                    shape = AppCardShape.Uniform(previewCornerRadius),
                    modifier = Modifier
                        .size(width = width, height = height)
                        .shadow(8.dp, RoundedCornerShape(previewCornerRadius)),
                ) {
                    var previewSize by remember(selectedTab) { mutableStateOf(IntSize.Zero) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { previewSize = it }
                            .pointerInput(selectedTab, previewSize) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    updateCurrentTransform(
                                        applyGestureToProfileWallpaperTransform(
                                            current = currentTransformState.value,
                                            panX = pan.x,
                                            panY = pan.y,
                                            zoomChange = zoom,
                                            containerWidthPx = previewSize.width.toFloat(),
                                            containerHeightPx = previewSize.height.toFloat()
                                        )
                                    )
                                }
                            }
                    ) {
                        com.android.purebilibili.core.ui.wallpaper.WallpaperMedia(
                            uri = imageUri,
                            imageModel = ImageRequest.Builder(LocalContext.current)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            alignment = androidx.compose.ui.BiasAlignment(
                                currentTransform.offsetX,
                                currentTransform.offsetY
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = currentTransform.scale,
                                    scaleY = currentTransform.scale
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.42f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(88.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.24f),
                                            Color.Black.copy(alpha = 0.46f)
                                        )
                                    )
                                )
                        )

                        AppText(
                            text = "双指缩放  单指拖动",
                            color = Color.White.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.3f), AppShapes.container(ContainerLevel.Tag))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    AppText(
                        text = if (selectedTab == 0) "手机端参数" else "平板端参数",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = "缩放 ${"%.2f".format(currentTransform.scale)}x  横向 ${"%.2f".format(currentTransform.offsetX)}  纵向 ${"%.2f".format(currentTransform.offsetY)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                AppTextButton(
                    onClick = { updateCurrentTransform(ProfileWallpaperTransform()) }
                ) {
                    AppText("重置位置")
                }
            }

            AppText(
                text = "不同设备分别保存；首次设置会以居中参数作为默认值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun WallpaperDeviceTabRow(
    selectedTab: Int,
    modifier: Modifier = Modifier,
    onSelectedTabChange: (Int) -> Unit,
) {
    val options = remember {
        listOf(
            AppSegmentOption(0, "手机端"),
            AppSegmentOption(1, "平板端"),
        )
    }
    AppThemeAdaptiveTabRow(
        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
        options = options,
        selectedValue = selectedTab,
        onSelectionChange = onSelectedTabChange,
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally),
        compactMiuixWhenTwoOptions = true,
        height = 48.dp,
        indicatorHeight = com.android.purebilibili.core.ui
            .roundMatchedLiquidIndicatorHeightDp(48f).dp,
        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
        dragSelectionEnabled = true,
        tapPressRefractionEnabled = true,
    )
}
