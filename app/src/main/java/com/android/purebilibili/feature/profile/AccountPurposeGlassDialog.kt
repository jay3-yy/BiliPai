package com.android.purebilibili.feature.profile

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.popup.AppGlassPopupHost
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig

/** 各账号入口发布自己的快照，不把页面或上一轮触发器当作本次锚点。 */
internal val LocalProfileAccountPopupSource = compositionLocalOf<((AppGlassMenuSource?) -> Unit)?> { null }

internal data class ProfileAccountPopupAction(val modifier: Modifier, val onClick: () -> Unit)

@Composable
internal fun rememberProfileAccountPopupAction(
    onClick: () -> Unit,
): ProfileAccountPopupAction {
    val source = rememberAppGlassMenuSource()
    val publish = LocalProfileAccountPopupSource.current
    val action = {
        onClick()
        publish?.invoke(source.takeIf { it.isAvailable })
        Unit
    }
    return ProfileAccountPopupAction(
        Modifier.appGlassMenuSource(source, onOpen = action), action,
    )
}

/** 本入口显式选择同窗容器采样；玻璃关闭或同窗宿主不可用时保留原生弹窗参数。 */
@Composable
internal fun AccountPurposeDialogSurface(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    contentLayout: AppContentDialogLayoutPolicy,
    source: AppGlassMenuSource? = null,
) {
    if (LocalAppThemeConfig.current.liquidGlassEnabled && Build.VERSION.SDK_INT >= 33 && appGlassMenuAvailable()) {
        val maxHeight = minOf(640.dp, (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
        val tuning = LocalLiquidGlassRenderConfig.current.tuning
        val handled = AppGlassMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            placement = if (source?.isAvailable == true) AppGlassMenuPlacement.ANCHOR_END else AppGlassMenuPlacement.CENTER,
            source = source,
            minWidth = contentLayout.minWidthDp.dp,
            maxWidth = contentLayout.maxWidthDp.dp,
            maxHeight = maxHeight,
            interactiveContent = true,
        ) {
            // 子菜单使用独立同窗宿主，避免选择账号时替换或关闭父容器。
            // 采样源仅录制容器正文，子菜单位于其兄弟覆盖层，不采样自身。
            Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                AppGlassPopupHost(glassAvailable = true, tuning = tuning) {
                    // 面板按内容自适应收合；正文超出时在剩余空间内滚动，按钮紧随其后，
                    // 不再用 fillMaxSize 恒定撑满最大高度留下大段空面板。
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        AppText(title, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { text() }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dismissButton()
                            Spacer(Modifier.width(8.dp))
                            confirmButton()
                        }
                    }
                }
            }
        }
        if (handled) return
    }
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { AppText(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        contentLayout = contentLayout,
    )
}

/** 玻璃用途入口与设置单选行尾同款锚点（值文本 + 展开箭头，无底色），菜单沿真实锚点展开并收回。 */
@Composable
internal fun AccountPurposeGlassAnchor(label: String, actions: List<AppWindowAction>) {
    var expanded by remember { mutableStateOf(false) }
    val source = rememberAppGlassMenuSource()
    val elastic = rememberAppElasticPressState(feedbackStrength = 0.5f)
    val motionAllowed = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current &&
        rememberVisualWorkActive()
    val open = { expanded = true }
    Box {
        Row(
            Modifier
                .widthIn(min = 80.dp, max = 160.dp)
                .heightIn(min = 32.dp)
                .appElasticPress(enabled = motionAllowed, state = elastic, transformInBackdrop = true)
                .appGlassMenuSource(source, onOpen = open)
                .clickable(role = Role.Button, onClick = open)
                .padding(start = 10.dp, top = 6.dp, end = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
                tapToCopyEnabled = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            AppIcon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = AppContentColors.secondaryText(),
                modifier = Modifier.size(16.dp),
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, glassSource = source) {
            actions.forEach { action ->
                AppDropdownMenuItem(
                    text = { AppText(action.label) },
                    selected = action.selected,
                    trailingIcon = if (action.selected) ({ AppText("✓") }) else null,
                    onClick = {
                        expanded = false
                        action.onClick?.invoke()
                    },
                )
            }
        }
    }
}
