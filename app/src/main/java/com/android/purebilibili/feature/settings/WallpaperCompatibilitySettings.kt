package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.util.toUserFriendlyMessage

import android.app.WallpaperManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.android.purebilibili.core.theme.LocalWallpaperColorCompatibility
import com.android.purebilibili.core.theme.saveManualWallpaperSeed
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit image fallback: never read a system default drawable as if it were the user's wallpaper. */
@Composable
internal fun WallpaperCompatibilitySettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val compatibility = LocalWallpaperColorCompatibility.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var requestedWallpaperId by rememberSaveable { mutableStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) busy = false
        else scope.launch {
            busy = true
            try {
                val seed = withContext(Dispatchers.IO) {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(uri).allowHardware(false).size(256, 256).build()
                    ) as? SuccessResult
                    val bitmap = (result?.image as? coil3.BitmapImage)?.bitmap
                        ?: error("无法读取图片，请选择静态壁纸图片")
                    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                    palette.vibrantSwatch?.rgb ?: palette.mutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb ?: error("未能从图片提取颜色")
                }
                val currentId = withContext(Dispatchers.IO) {
                    WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                }
                check(currentId >= 0 && currentId == requestedWallpaperId) { "系统壁纸已变化，请重新选择当前壁纸图片" }
                currentCoroutineContext().ensureActive()
                saveManualWallpaperSeed(context, seed, currentId)
                Toast.makeText(context, "已使用所选图片取色；更换系统壁纸后自动失效", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.toUserFriendlyMessage("图片取色失败"), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AppText(when {
            compatibility.manual -> "已使用所选壁纸图片取色。检测到更换系统壁纸后失效；系统无法提供颜色时需重新选图。"
            compatibility.seed != null -> "已启用壁纸颜色兼容取色，将随系统壁纸颜色更新。"
            !compatibility.wallpaperColorsAvailable -> "系统未提供壁纸颜色，当前沿用系统调色板。若颜色与壁纸不符，可选择当前壁纸原图取色。"
            else -> "当前使用系统调色板。若颜色与壁纸不符，可选择当前壁纸原图取色。"
        })
        AppTextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                requestedWallpaperId = withContext(Dispatchers.IO) {
                    runCatching { WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
                        .getOrDefault(-1)
                }
                try {
                    if (requestedWallpaperId >= 0) picker.launch("image/*")
                    else {
                        busy = false
                        Toast.makeText(context, "系统不支持读取壁纸标识，无法绑定图片取色", Toast.LENGTH_LONG).show()
                    }
                } catch (_: android.content.ActivityNotFoundException) {
                    busy = false
                    Toast.makeText(context, "未找到可用的图片选择器", Toast.LENGTH_LONG).show()
                }
            }
        }) { AppText(if (busy) "正在取色…" else "选择当前壁纸图片取色") }
        if (compatibility.manual) {
            AppTextButton(enabled = !busy, onClick = {
                scope.launch {
                    try {
                        saveManualWallpaperSeed(context, null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, "恢复系统取色失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { AppText("恢复自动取色") }
        }
    }
}
