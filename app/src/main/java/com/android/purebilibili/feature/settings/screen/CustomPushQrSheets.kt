package com.android.purebilibili.feature.settings

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
import com.android.purebilibili.core.push.ImportedPushConfig
import com.android.purebilibili.core.push.PushConfigImportResult
import com.android.purebilibili.core.push.PushConfigTransfer
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.login.BiliPaiTransferScanner
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPushQrExportSheet(
    qrPayload: String,
    onCopyJson: () -> Unit,
    onDismiss: () -> Unit,
) {
    var qrBitmap by remember(qrPayload) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(qrPayload) {
        qrBitmap = withContext(Dispatchers.Default) {
            encodePushConfigQrBitmap(qrPayload)
        }
    }

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = "导出推送配置二维码",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = "包含端到端加密密钥与当前通道凭据，请仅向可信服务端或本人设备展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                AppIconButton(onClick = onDismiss) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = AppContentColors.secondaryText(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = qrBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "自建推送配置二维码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppText(
                        text = "正在生成二维码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                    )
                }
            }

            AppTextButton(onClick = onCopyJson) {
                AppIcon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                AppText("复制配置 JSON")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPushQrImportSheet(
    onDismiss: () -> Unit,
    onImported: (ImportedPushConfig) -> Unit,
) {
    var errorHint by remember { mutableStateOf<String?>(null) }

    AppModalBottomSheet(onDismissRequest = onDismiss) {
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
                        text = "扫码导入推送配置",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = errorHint ?: "扫描另一台 BiliPai 或自建服务端生成的配置二维码，也支持从相册选图",
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
                        when (val result = PushConfigTransfer.parseImportedPayload(raw)) {
                            is PushConfigImportResult.Success -> {
                                errorHint = null
                                onImported(result.config)
                            }
                            is PushConfigImportResult.Invalid -> {
                                errorHint = result.reason
                            }
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

private fun encodePushConfigQrBitmap(content: String, sizePx: Int = 560): Bitmap? = runCatching {
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
