package com.android.purebilibili.feature.cast

import com.android.purebilibili.core.ui.AppContentColors
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.CastPluginApi
import com.android.purebilibili.core.plugin.CastDiscoveryRequirement
import com.android.purebilibili.core.plugin.CastPluginRoute
import com.android.purebilibili.core.plugin.PluginManager
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private data class PluginRouteEntry(
    val plugin: CastPluginApi,
    val route: CastPluginRoute
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListDialog(
    onDismissRequest: () -> Unit,
    onPluginCastDeviceSelected: (CastPluginApi, CastPluginRoute) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    // Generic cast plugin routes
    val allPlugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val castPluginInfos = remember(allPlugins) {
        allPlugins.mapNotNull { info ->
            (info.plugin as? CastPluginApi)?.let { info to it }
        }
    }
    val castPlugins = remember(castPluginInfos) {
        castPluginInfos.filter { it.first.enabled }.map { it.second }
    }
    val rawLocalNetworkPlugins = remember(castPlugins) {
        castPlugins.filter { it.discoveryRequirement == CastDiscoveryRequirement.RAW_LOCAL_NETWORK }
    }
    var rawLocalNetworkAccessGranted by remember(context) {
        mutableStateOf(hasRawLocalNetworkAccess(context))
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequested = true
        rawLocalNetworkAccessGranted = hasRawLocalNetworkAccess(context)
        if (rawLocalNetworkAccessGranted) {
            rawLocalNetworkPlugins.forEach { it.startRouteDiscovery(context) }
        } else {
            rawLocalNetworkPlugins.forEach { it.onDiscoveryAccessRevoked() }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, rawLocalNetworkPlugins) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val accessNow = hasRawLocalNetworkAccess(context)
                if (rawLocalNetworkAccessGranted && !accessNow) {
                    rawLocalNetworkPlugins.forEach { it.onDiscoveryAccessRevoked() }
                }
                rawLocalNetworkAccessGranted = accessNow
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasDisabledCastPlugins = remember(castPluginInfos) {
        castPluginInfos.any { !it.first.enabled }
    }

    val pluginRouteEntries by produceState(emptyList<PluginRouteEntry>(), castPlugins) {
        if (castPlugins.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        combine(castPlugins.map { it.routes }) { arrays ->
            arrays.flatMapIndexed { index, routes ->
                routes.map { PluginRouteEntry(castPlugins[index], it) }
            }
        }.collect { value = it }
    }

    val isDiscovering by produceState(false, castPlugins) {
        if (castPlugins.isEmpty()) {
            value = false
            return@produceState
        }
        combine(castPlugins.map { it.isDiscovering }) { states ->
            states.any { it }
        }.collect { value = it }
    }

    DisposableEffect(castPlugins, rawLocalNetworkAccessGranted) {
        castPlugins
            .filter { plugin ->
                plugin.discoveryRequirement != CastDiscoveryRequirement.RAW_LOCAL_NETWORK ||
                    rawLocalNetworkAccessGranted
            }
            .forEach { it.startRouteDiscovery(context) }
        onDispose {
            castPlugins.forEach { it.stopRouteDiscovery() }
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { AppIcon(Icons.Rounded.Cast, null) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("选择投屏设备")
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    enabled = !isDiscovering,
                    onClick = {
                        if (!isDiscovering) {
                            castPlugins
                                .filter { plugin ->
                                    plugin.discoveryRequirement != CastDiscoveryRequirement.RAW_LOCAL_NETWORK ||
                                        rawLocalNetworkAccessGranted
                                }
                                .forEach { it.refreshRouteDiscovery(context) }
                        }
                    }
                ) {
                    AppIcon(Icons.Rounded.Refresh, "刷新")
                }
            }
        },
        text = {
            val hasDevices = pluginRouteEntries.isNotEmpty()

            if (castPlugins.isEmpty() && hasDisabledCastPlugins) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText("投屏插件未启用", style = MaterialTheme.typography.bodyMedium)
                        AppText(
                            "请前往 设置 > 插件，启用 DLNA 或 Google Cast",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
            } else if (
                !hasDevices &&
                !rawLocalNetworkAccessGranted &&
                rawLocalNetworkPlugins.isNotEmpty()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    AppText("搜索投屏设备需要本地网络权限", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    AppText(
                        if (permissionRequested) {
                            "权限未开启，需要本地网络权限的设备搜索已停止。"
                        } else {
                            "用于搜索和连接同一网络中的投屏设备，不会上传局域网信息。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AppTextButton(
                        onClick = {
                            val permissions = localNetworkRuntimePermissions()
                            if (permissions.isEmpty()) {
                                rawLocalNetworkAccessGranted = true
                                rawLocalNetworkPlugins.forEach { it.startRouteDiscovery(context) }
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        }
                    ) {
                        AppText("允许并搜索设备")
                    }
                    if (permissionRequested) {
                        AppTextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                }
                            }
                        ) {
                            AppText("前往系统设置")
                        }
                    }
                }
            } else if (!hasDevices && !isDiscovering) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        AppText("未找到设备", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        AppText(
                            "排查建议：\n" +
                                "1. 手机与电视/盒子同一 Wi‑Fi（勿用访客网络/AP 隔离）\n" +
                                "2. 系统设置中允许 BiliPai「附近设备/本地网络」权限\n" +
                                "3. 关闭 VPN；双卡手机请确认流量未抢走局域网\n" +
                                "4. DLNA 需接收端开启 DLNA/UPnP（CastFlow 选 DLNA 接收）\n" +
                                "5. 小米自带投屏多为 Miracast，不一定支持 DLNA\n" +
                                "6. Google Cast 需接收端支持 Chromecast 且手机有 GMS",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
            } else if (!hasDevices && isDiscovering) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AdaptiveLoadingIndicator(size = 32.dp)
                        Spacer(Modifier.height(8.dp))
                        AppText("搜索设备中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!rawLocalNetworkAccessGranted && rawLocalNetworkPlugins.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(
                                "授权本地网络后可搜索更多投屏设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.weight(1f)
                            )
                            AppTextButton(
                                onClick = { permissionLauncher.launch(localNetworkRuntimePermissions()) }
                            ) {
                                AppText("允许本地网络")
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(pluginRouteEntries, key = { "${it.plugin.id}:${it.route.routeId}" }) { entry ->
                            val plugin = entry.plugin
                            val route = entry.route
                            AppListItem(
                                headlineContent = { AppText(route.name) },
                                supportingContent = { AppText(route.description ?: plugin.name) },
                                leadingContent = { AppIcon(route.icon ?: plugin.icon ?: Icons.Rounded.Cast, null) },
                                modifier = Modifier
                                    .clickable { onPluginCastDeviceSelected(plugin, route) }
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(
                    onClick = {
                        com.android.purebilibili.core.util.LogCollector.exportAndShare(context)
                    }
                ) {
                    AppText("导出日志")
                }

                AppTextButton(onClick = onDismissRequest) {
                    AppText("取消")
                }
            }
        }
    )
}
