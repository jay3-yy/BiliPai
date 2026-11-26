package com.android.purebilibili.feature.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.push.BiliPaiNotificationDispatcher
import com.android.purebilibili.core.push.CustomPushConfig
import com.android.purebilibili.core.push.ImportedPushConfig
import com.android.purebilibili.core.push.PushChannelMode
import com.android.purebilibili.core.push.PushConfigStore
import com.android.purebilibili.core.push.PushConfigTransfer
import com.android.purebilibili.core.push.PushDeliveryLogEntry
import com.android.purebilibili.core.push.PushIngressRouter
import com.android.purebilibili.core.push.fcm.FcmAvailability
import com.android.purebilibili.core.push.fcm.FcmPushController
import com.android.purebilibili.core.push.fcm.FcmTokenResult
import com.android.purebilibili.core.push.unifiedpush.UnifiedPushDistributorInfo
import com.android.purebilibili.core.push.unifiedpush.UnifiedPushProtocol
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppChoiceOption
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSingleChoicePreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.feature.settings.ui.LocalSettingsTopContentPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomPushSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val destinationCopy = remember { settingsDestinationCopy(SettingsSearchTarget.CUSTOM_PUSH) }
    val configFlow = remember(context) { PushConfigStore.observeConfig(context) }
    val config by configFlow.collectAsStateWithLifecycle(initialValue = CustomPushConfig())
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var distributors by remember { mutableStateOf<List<UnifiedPushDistributorInfo>>(emptyList()) }
    var fcmAvailability by remember { mutableStateOf<FcmAvailability>(FcmAvailability.Ready) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isFcmFetching by remember { mutableStateOf(false) }

    var showRelayDialog by remember { mutableStateOf(false) }
    var draftRelayUrl by remember { mutableStateOf("") }
    var showRotateKeyConfirm by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showQrExportSheet by remember { mutableStateOf(false) }
    var showQrImportSheet by remember { mutableStateOf(false) }
    var pendingImportedConfig by remember { mutableStateOf<ImportedPushConfig?>(null) }
    var pendingTestEncrypted by remember { mutableStateOf<Boolean?>(null) }

    fun refreshEnvironment() {
        distributors = UnifiedPushProtocol.listInstalledDistributors(context)
        fcmAvailability = FcmPushController.checkAvailability(context)
        BiliPaiNotificationDispatcher.ensureChannels(context)
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
    }

    fun openSystemNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun triggerLocalSelfTest(encryptedLoopback: Boolean) {
        scope.launch {
            try {
                val outcome = PushIngressRouter.runLocalSelfTest(context, encryptedLoopback)
                statusMessage = if (outcome.accepted) {
                    if (encryptedLoopback) "加密回环测试已弹出通知" else "本地测试通知已发送"
                } else {
                    "测试未弹出：${outcome.detail}（请检查系统通知与渠道权限）"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val encrypted = pendingTestEncrypted
        pendingTestEncrypted = null
        if (granted && encrypted != null) {
            triggerLocalSelfTest(encrypted)
        } else if (!granted) {
            statusMessage = "未授予通知权限，无法弹出推送通知，可在下方打开系统通知设置"
        }
    }

    fun requestOrRunSelfTest(encryptedLoopback: Boolean) {
        if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingTestEncrypted = encryptedLoopback
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            triggerLocalSelfTest(encryptedLoopback)
        }
    }

    fun registerUnifiedPush(distributorPackage: String) {
        scope.launch {
            try {
                val initialized = PushConfigStore.ensureInitialized(context)
                PushConfigStore.setUnifiedPushDistributor(context, distributorPackage)
                UnifiedPushProtocol.register(
                    context = context,
                    distributorPackage = distributorPackage,
                    instanceToken = initialized.unifiedPushInstanceToken,
                )
                statusMessage = "已向分发器发送注册请求，等待返回 Endpoint…（若长时间无响应，请在系统设置中为分发器与 BiliPai 开启「自启动 / 允许关联启动」）"
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    fun refreshFcmToken() {
        scope.launch {
            isFcmFetching = true
            try {
                when (val result = FcmPushController.fetchAndStoreToken(context)) {
                    is FcmTokenResult.Success -> {
                        statusMessage = "已更新 FCM Token"
                    }
                    is FcmTokenResult.Failure -> {
                        statusMessage = result.reason
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                isFcmFetching = false
            }
        }
    }

    fun switchChannelMode(targetMode: PushChannelMode) {
        scope.launch {
            try {
                val initialized = PushConfigStore.ensureInitialized(context)
                statusMessage = null
                if (initialized.mode == PushChannelMode.UNIFIED_PUSH &&
                    targetMode != PushChannelMode.UNIFIED_PUSH &&
                    initialized.unifiedPushDistributor.isNotBlank()
                ) {
                    UnifiedPushProtocol.unregister(
                        context = context,
                        distributorPackage = initialized.unifiedPushDistributor,
                        instanceToken = initialized.unifiedPushInstanceToken,
                    )
                    PushConfigStore.setUnifiedPushEndpoint(context, "")
                }
                if (targetMode != PushChannelMode.FCM_RELAY) {
                    FcmPushController.setAutoInitEnabled(context, false)
                }

                PushConfigStore.setMode(context, targetMode)

                when (targetMode) {
                    PushChannelMode.DISABLED -> {
                        statusMessage = "已关闭自建推送"
                    }
                    PushChannelMode.UNIFIED_PUSH -> {
                        refreshEnvironment()
                        val selectedPkg = initialized.unifiedPushDistributor
                            .takeIf { pkg -> distributors.any { it.packageName == pkg } }
                            ?: distributors.firstOrNull()?.packageName
                        if (selectedPkg != null) {
                            registerUnifiedPush(selectedPkg)
                        } else {
                            statusMessage = "未检测到 UnifiedPush 分发器，请先安装 ntfy 或 Sunup 等开源分发器"
                        }
                    }
                    PushChannelMode.FCM_RELAY -> {
                        refreshEnvironment()
                        refreshFcmToken()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    LaunchedEffect(context) {
        PushConfigStore.ensureInitialized(context)
        refreshEnvironment()
    }

    LaunchedEffect(config.unifiedPushEndpoint) {
        if (config.mode == PushChannelMode.UNIFIED_PUSH && config.unifiedPushEndpoint.isNotBlank()) {
            statusMessage = null
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshEnvironment()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsPageScaffold(
        title = destinationCopy.title,
        onBack = onBack,
        backContentDescription = "返回",
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
        externalContentHandlesTopPadding = true,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(LocalSettingsTopContentPadding.current))
            AppText(
                text = "由你自己的服务器、Cloudflare Worker 或脚本监控更新并发送推送，客户端不在后台轮询。默认强制 AES-256-GCM 端到端加密，并限制跳转链接与图床域名白名单。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            statusMessage?.let { message ->
                AppText(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.warning(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            AppPreferenceSectionTitle("推送通道")
            SettingsCardGroup {
                SettingsItemAnchor("custom_push.mode") {
                    AppSingleChoicePreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.NOTIFICATION_DELIVERY_MODE),
                        title = "接收通道",
                        subtitle = when (config.mode) {
                            PushChannelMode.DISABLED -> "当前未启用远程自建推送"
                            PushChannelMode.UNIFIED_PUSH -> "通过 UnifiedPush 分发器（如 ntfy）唤醒接收，国内外通用"
                            PushChannelMode.FCM_RELAY -> "通过 FCM 无状态中转直达，需设备支持 Google Play 服务"
                        },
                        selectedValue = config.mode,
                        options = listOf(
                            AppChoiceOption(
                                value = PushChannelMode.DISABLED,
                                label = "关闭",
                                description = "不接收外部自建推送",
                            ),
                            AppChoiceOption(
                                value = PushChannelMode.UNIFIED_PUSH,
                                label = "UnifiedPush（推荐）",
                                description = "配合 ntfy / Sunup 等分发器使用，零中转运维成本",
                            ),
                            AppChoiceOption(
                                value = PushChannelMode.FCM_RELAY,
                                label = "FCM 无状态中转",
                                description = "免装分发器，适合具备 GMS 且自建 Worker 中转的环境",
                            ),
                        ),
                        onValueChange = ::switchChannelMode,
                        iconTint = AppContentColors.secondaryText(),
                    )
                }

                if (config.mode == PushChannelMode.UNIFIED_PUSH) {
                    AppPreferenceDivider()
                    if (distributors.isNotEmpty()) {
                        val activeDistributor = config.unifiedPushDistributor
                            .takeIf { pkg -> distributors.any { it.packageName == pkg } }
                            ?: distributors.first().packageName
                        AppSingleChoicePreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLUGINS),
                            title = "UnifiedPush 分发器",
                            subtitle = "选择用于接收推送并唤醒 BiliPai 的分发器应用",
                            selectedValue = activeDistributor,
                            options = distributors.map { info ->
                                AppChoiceOption(
                                    value = info.packageName,
                                    label = info.label,
                                    description = info.packageName,
                                )
                            },
                            onValueChange = ::registerUnifiedPush,
                            iconTint = AppContentColors.secondaryText(),
                        )
                    } else {
                        AppPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLUGINS),
                            title = "UnifiedPush 分发器",
                            subtitle = "未检测到已安装的分发器（如 ntfy / Sunup），安装后点此重新扫描",
                            value = "未安装",
                            onClick = {
                                refreshEnvironment()
                                statusMessage = if (distributors.isEmpty()) {
                                    "仍未检测到 UnifiedPush 分发器，请先安装 ntfy 或 Sunup"
                                } else {
                                    "已检测到 ${distributors.size} 个分发器"
                                }
                            },
                            iconTint = AppContentColors.secondaryText(),
                        )
                    }

                    AppPreferenceDivider()
                    val hasEndpoint = config.unifiedPushEndpoint.isNotBlank()
                    AppPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.OPEN_LINKS),
                        title = "推送端点（Endpoint）",
                        subtitle = if (hasEndpoint) {
                            config.unifiedPushEndpoint
                        } else {
                            "尚未收到分发器端点，点按重新请求注册"
                        },
                        value = if (hasEndpoint) "复制" else "重试",
                        onClick = {
                            if (hasEndpoint) {
                                copyToClipboard("UnifiedPush Endpoint", config.unifiedPushEndpoint)
                            } else {
                                val targetPkg = config.unifiedPushDistributor
                                    .ifBlank { distributors.firstOrNull()?.packageName.orEmpty() }
                                if (targetPkg.isNotBlank()) {
                                    registerUnifiedPush(targetPkg)
                                } else {
                                    statusMessage = "请先安装并选择 UnifiedPush 分发器"
                                }
                            }
                        },
                        showChevron = !hasEndpoint,
                        iconTint = AppContentColors.secondaryText(),
                    )
                }

                if (config.mode == PushChannelMode.FCM_RELAY) {
                    AppPreferenceDivider()
                    val fcmStatusSubtitle = when {
                        isFcmFetching -> "正在向 Firebase 请求设备 Token…"
                        config.fcmToken.isNotBlank() -> maskTokenPreview(config.fcmToken)
                        fcmAvailability == FcmAvailability.MissingPlayServices ->
                            "设备缺少 Google Play 服务，建议使用 UnifiedPush"
                        fcmAvailability == FcmAvailability.MissingFirebaseConfig ->
                            "当前安装包未附带 Firebase 配置，建议使用 UnifiedPush"
                        else -> "尚未获取 Token，点按立即获取"
                    }
                    AppPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.OPEN_LINKS),
                        title = "FCM 设备 Token",
                        subtitle = fcmStatusSubtitle,
                        value = if (config.fcmToken.isNotBlank()) "复制" else "获取",
                        onClick = {
                            if (config.fcmToken.isNotBlank()) {
                                copyToClipboard("FCM Token", config.fcmToken)
                            } else {
                                refreshFcmToken()
                            }
                        },
                        showChevron = config.fcmToken.isBlank(),
                        iconTint = AppContentColors.secondaryText(),
                    )

                    AppPreferenceDivider()
                    AppPreference(
                        icon = rememberSettingsSemanticIcon(SettingsIconRole.WEBDAV_BACKUP),
                        title = "中转网关地址（可选）",
                        subtitle = config.fcmRelayUrl.ifBlank {
                            "填写你部署的 Cloudflare Worker 无状态中转地址，导出配置时自动带入"
                        },
                        value = if (config.fcmRelayUrl.isNotBlank()) "已配置" else "未配置",
                        onClick = {
                            draftRelayUrl = config.fcmRelayUrl
                            showRelayDialog = true
                        },
                        iconTint = AppContentColors.secondaryText(),
                    )
                }
            }

            AppPreferenceSectionTitle("端到端加密与凭据")
            SettingsCardGroup {
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_PERMISSION),
                    title = "AES-256-GCM 共享密钥",
                    subtitle = if (config.secretKeyBase64.isNotBlank()) {
                        "仅存于本机 Keystore：${maskTokenPreview(config.secretKeyBase64)}"
                    } else {
                        "正在初始化密钥…"
                    },
                    value = "复制",
                    onClick = {
                        if (config.secretKeyBase64.isNotBlank()) {
                            copyToClipboard("BiliPai 推送密钥", config.secretKeyBase64)
                        }
                    },
                    showChevron = false,
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.REFRESH_COUNT),
                    title = "重置共享密钥",
                    subtitle = "立即生成新的 256 位密钥并作废旧密钥，服务端需同步更新",
                    onClick = { showRotateKeyConfirm = true },
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppSwitchPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.DISCLAIMER),
                    title = "允许明文调试",
                    subtitle = "默认关闭且强制拒绝明文包；仅建议在可信内网临时联调时开启",
                    checked = config.allowPlaintext,
                    onCheckedChange = { allow ->
                        scope.launch {
                            PushConfigStore.setAllowPlaintext(context, allow)
                        }
                    },
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.COPY_TEXT),
                    title = "复制服务端配置 JSON",
                    subtitle = "一键复制当前通道、端点/Token、AES 密钥与 AAD，供测试脚本或自建服务端导入",
                    value = "复制",
                    onClick = {
                        val json = PushConfigStore.exportServerConfigJson(config)
                        copyToClipboard("BiliPai 推送配置 JSON", json)
                    },
                    showChevron = false,
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = Icons.Outlined.QrCode2,
                    title = "导出配置二维码",
                    subtitle = "生成包含加密密钥与当前通道凭据的二维码，方便服务端或另一台设备扫码",
                    onClick = { showQrExportSheet = true },
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "扫码导入配置",
                    subtitle = "扫描二维码或从相册识图导入共享密钥与中转网关地址（不覆盖本机 Token）",
                    onClick = { showQrImportSheet = true },
                    iconTint = AppContentColors.secondaryText(),
                )
            }

            AppPreferenceSectionTitle("测试与诊断")
            SettingsCardGroup {
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.MESSAGE_NOTIFICATION),
                    title = "发送本地测试通知",
                    subtitle = "不依赖服务器，验证通知权限、渠道样式与点击直达动态页",
                    onClick = { requestOrRunSelfTest(encryptedLoopback = false) },
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_VERIFICATION),
                    title = "运行加密回环测试",
                    subtitle = "使用本机密钥执行 AES-256-GCM 加密 → 解密验签 → 弹出通知全流程",
                    onClick = { requestOrRunSelfTest(encryptedLoopback = true) },
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PERMISSION),
                    title = "系统通知渠道设置",
                    subtitle = "分别配置投稿、直播开播、动态、回复与测试通知的响铃和静音",
                    onClick = ::openSystemNotificationSettings,
                    iconTint = AppContentColors.secondaryText(),
                )
                AppPreferenceDivider()
                AppPreference(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.EXPORT_LOGS),
                    title = "最近接收记录",
                    subtitle = if (config.deliveryLogs.isEmpty()) {
                        "暂无接收记录"
                    } else {
                        val latest = config.deliveryLogs.first()
                        "${if (latest.accepted) "成功" else "拦截"} · ${latest.summary}"
                    },
                    value = "${config.deliveryLogs.size} 条",
                    onClick = { showLogsDialog = true },
                    iconTint = AppContentColors.secondaryText(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRelayDialog) {
        AppAlertDialog(
            onDismissRequest = { showRelayDialog = false },
            title = { AppText("中转网关地址") },
            text = {
                Column {
                    AppText(
                        text = "填入无状态 Cloudflare Worker 转发地址（例如 https://your-worker.workers.dev/push），留空表示不记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppTextField(
                        value = draftRelayUrl,
                        onValueChange = { draftRelayUrl = it },
                        placeholder = "https://bilipai-push.example.workers.dev/push",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        val normalized = draftRelayUrl.trim()
                        if (normalized.isNotEmpty() && !normalized.startsWith("https://")) {
                            Toast.makeText(context, "中转网关必须使用 https:// 地址", Toast.LENGTH_SHORT).show()
                            return@AppDialogAction
                        }
                        showRelayDialog = false
                        scope.launch {
                            PushConfigStore.setFcmRelayUrl(context, normalized)
                        }
                    },
                ) {
                    AppText("保存")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { showRelayDialog = false }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showRotateKeyConfirm) {
        AppAlertDialog(
            onDismissRequest = { showRotateKeyConfirm = false },
            title = { AppText("重置共享密钥？") },
            text = {
                AppText(
                    "重置后会立即生成新的 AES-256-GCM 密钥，旧密钥加密的推送将被全部拒绝。请在重置后更新你的服务端配置。",
                )
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        showRotateKeyConfirm = false
                        scope.launch {
                            PushConfigStore.rotateSecretKey(context)
                            statusMessage = "已重置共享密钥，请重新复制配置到服务端"
                        }
                    },
                ) {
                    AppText("确认重置")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { showRotateKeyConfirm = false }) {
                    AppText("取消")
                }
            },
        )
    }

    if (showLogsDialog) {
        val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
        AppAlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { AppText("最近接收记录") },
            text = {
                if (config.deliveryLogs.isEmpty()) {
                    AppText("还没有收到自建推送记录。")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        config.deliveryLogs.forEach { entry: PushDeliveryLogEntry ->
                            val timeText = formatter.format(Date(entry.timestampMs))
                            val statusTag = if (entry.accepted) "成功" else "拦截"
                            AppText(
                                text = "[$timeText] ${entry.source} · $statusTag\n${entry.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.accepted) {
                                    AppContentColors.primaryText()
                                } else {
                                    AppContentColors.warning()
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppDialogAction(onClick = { showLogsDialog = false }) {
                    AppText("关闭")
                }
            },
            dismissButton = if (config.deliveryLogs.isNotEmpty()) {
                {
                    AppDialogAction(
                        onClick = {
                            scope.launch { PushConfigStore.clearLogs(context) }
                            showLogsDialog = false
                        },
                    ) {
                        AppText("清空记录")
                    }
                }
            } else {
                null
            },
        )
    }

    if (showQrExportSheet) {
        val qrPayload = remember(config) { PushConfigTransfer.encodeQrPayload(config) }
        CustomPushQrExportSheet(
            qrPayload = qrPayload,
            onCopyJson = {
                copyToClipboard("BiliPai 推送配置 JSON", PushConfigStore.exportServerConfigJson(config))
            },
            onDismiss = { showQrExportSheet = false },
        )
    }

    if (showQrImportSheet) {
        CustomPushQrImportSheet(
            onDismiss = { showQrImportSheet = false },
            onImported = { imported ->
                showQrImportSheet = false
                pendingImportedConfig = imported
            },
        )
    }

    pendingImportedConfig?.let { imported ->
        val summaryItems = buildList {
            if (imported.secretKeyBase64 != null) add("• 覆盖本机 AES-256-GCM 共享密钥")
            if (imported.fcmRelayUrl != null) add("• 更新中转网关地址：${imported.fcmRelayUrl}")
        }.joinToString("\n")
        AppAlertDialog(
            onDismissRequest = { pendingImportedConfig = null },
            title = { AppText("导入推送配置？") },
            text = {
                AppText(
                    "将应用以下配置（不会覆盖本机已注册的 Endpoint 或 FCM Token）：\n\n$summaryItems",
                )
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        pendingImportedConfig = null
                        scope.launch {
                            try {
                                PushConfigStore.importConfig(context, imported)
                                val appliedParts = buildList {
                                    if (imported.secretKeyBase64 != null) add("共享密钥")
                                    if (imported.fcmRelayUrl != null) add("中转网关地址")
                                }.joinToString("与")
                                statusMessage = "已导入$appliedParts"
                                Toast.makeText(context, "已导入$appliedParts", Toast.LENGTH_SHORT).show()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            }
                        }
                    },
                ) {
                    AppText("确认导入")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { pendingImportedConfig = null }) {
                    AppText("取消")
                }
            },
        )
    }
}

private fun maskTokenPreview(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length <= 16) return trimmed
    return "${trimmed.take(8)}…${trimmed.takeLast(6)}"
}
