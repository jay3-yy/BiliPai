package com.android.purebilibili.feature.nearby

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.login.BiliPaiTransferScanner
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 在后台线程使用 ZXing 生成白底高对比度配对二维码，不阻塞主线程。 */
@Composable
internal fun rememberNearbyQrBitmap(
    content: String?,
    sizePx: Int = 520,
): Bitmap? {
    var bitmap by remember(content) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(content, sizePx) {
        if (content.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.Default) {
            encodeNearbyQrBitmap(content, sizePx)
        }
    }
    return bitmap
}

internal fun encodeNearbyQrBitmap(content: String, sizePx: Int = 520): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}.getOrNull()

/** 展示已生成的配对二维码与一键复制文字入口。 */
@Composable
internal fun NearbyPairingQrCard(
    pairingCode: String,
    onCopyText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = rememberNearbyQrBitmap(pairingCode)
    AppSurface(
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.surfaceContainerHigh(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(188.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "设备接力配对二维码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AppText(
                text = "另一台设备打开「设备接力 → 扫码配对」扫描即可（5 分钟内有效）",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            AppTextButton(onClick = onCopyText) {
                AppIcon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                AppText("复制文字配对码")
            }
        }
    }
}

/** 扫码配对弹窗：复用 CameraX 扫码与相册识图，仅接受合法 bilipai-pair://v1 配对码。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NearbyQrScannerSheet(
    onDismiss: () -> Unit,
    onScannedPairCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var errorHint by remember { mutableStateOf<String?>(null) }

    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = "扫码配对设备",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = errorHint ?: "对准另一台 BiliPai 生成的接力配对二维码，或从相册选图",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorHint != null) AppSurfaceTokens.error() else AppContentColors.secondaryText(),
                    )
                }
                AppIconButton(onClick = onDismiss) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭扫码",
                        tint = AppContentColors.secondaryText(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 380.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            ) {
                BiliPaiTransferScanner(
                    onCode = { raw ->
                        val parsed = runCatching { NearbyPairCode.decode(raw) }
                        if (parsed.isSuccess) {
                            errorHint = null
                            onScannedPairCode(raw)
                        } else {
                            errorHint = "不是 BiliPai 设备接力配对码，请扫描另一台设备生成的配对二维码"
                        }
                    },
                    singleShot = false,
                    acceptAnyQr = true,
                    onError = { msg -> errorHint = msg },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
