// 文件路径: feature/plugin/EyeProtectionPlugin.kt
package com.android.purebilibili.feature.plugin

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.plugin.Plugin
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.ui.components.*
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.Calendar

private const val TAG = "EyeProtectionPlugin"

/**
 *  夜间护眼提示插件
 * 
 * 功能：
 * 1. 自定义夜间护眼时间段（如 22:00 - 07:00）
 * 2. 使用时长提醒（如每 30 分钟提醒休息）
 * 3. 自动降低亮度（添加半透明覆盖层）
 * 4. 暖色滤镜（减少蓝光）
 */
class EyeProtectionPlugin : Plugin {
    
    override val id = "eye_protection"
    override val name = "夜间护眼"
    override val description = "护眼提醒、自动降低亮度和蓝光过滤"
    override val version = "1.0.0"
    override val author = "YangY"
    override val icon: ImageVector = CupertinoIcons.Default.Moon
    
    private var config: EyeProtectionConfig = EyeProtectionConfig()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var usageTrackingJob: Job? = null
    
    // 使用时长（分钟）
    private var usageMinutes = 0
    
    //  状态流 - 供 UI 层监听
    private val _showRestReminder = MutableStateFlow(false)
    val showRestReminder: StateFlow<Boolean> = _showRestReminder.asStateFlow()
    
    private val _isNightModeActive = MutableStateFlow(false)
    val isNightModeActive: StateFlow<Boolean> = _isNightModeActive.asStateFlow()
    
    //  护眼效果参数（供 Overlay 使用）
    private val _brightnessLevel = MutableStateFlow(1.0f)
    val brightnessLevel: StateFlow<Float> = _brightnessLevel.asStateFlow()
    
    private val _warmFilterStrength = MutableStateFlow(0f)
    val warmFilterStrength: StateFlow<Float> = _warmFilterStrength.asStateFlow()
    
    override suspend fun onEnable() {
        loadConfigSuspend()
        startUsageTracking()
        checkNightModeStatus()
        Logger.d(TAG, " 夜间护眼插件已启用")
    }
    
    override suspend fun onDisable() {
        usageTrackingJob?.cancel()
        usageMinutes = 0
        _showRestReminder.value = false
        _isNightModeActive.value = false
        _brightnessLevel.value = 1.0f
        _warmFilterStrength.value = 0f
        Logger.d(TAG, "🔴 夜间护眼插件已禁用")
    }
    
    /**
     * 开始使用时长追踪
     */
    private fun startUsageTracking() {
        usageTrackingJob?.cancel()
        usageTrackingJob = scope.launch {
            while (true) {
                delay(60_000) // 每分钟检查一次
                usageMinutes++
                
                // 检查夜间模式状态
                checkNightModeStatus()
                
                // 检查是否需要提醒休息
                if (config.usageReminderEnabled && 
                    usageMinutes > 0 && 
                    usageMinutes % config.usageDurationMinutes == 0) {
                    Logger.d(TAG, "⏰ 触发休息提醒：已使用 $usageMinutes 分钟")
                    _showRestReminder.value = true
                }
            }
        }
        Logger.d(TAG, " 开始追踪使用时长")
    }
    
    /**
     * 检查是否在夜间护眼时段
     */
    private fun checkNightModeStatus() {
        if (!config.nightModeEnabled && !config.forceEnabled) {
            _isNightModeActive.value = false
            _brightnessLevel.value = 1.0f
            _warmFilterStrength.value = 0f
            return
        }
        
        // 手动强制开启
        if (config.forceEnabled) {
            _isNightModeActive.value = true
            _brightnessLevel.value = config.brightnessLevel
            _warmFilterStrength.value = config.warmFilterStrength
            return
        }
        
        // 检查当前时间是否在夜间时段
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val inNightPeriod = if (config.nightModeStartHour > config.nightModeEndHour) {
            // 跨天情况：如 22:00 - 07:00
            currentHour >= config.nightModeStartHour || currentHour < config.nightModeEndHour
        } else {
            // 同天情况：如 20:00 - 23:00
            currentHour >= config.nightModeStartHour && currentHour < config.nightModeEndHour
        }
        
        if (inNightPeriod) {
            _isNightModeActive.value = true
            _brightnessLevel.value = config.brightnessLevel
            _warmFilterStrength.value = config.warmFilterStrength
            Logger.d(TAG, " 进入夜间护眼模式 (${config.nightModeStartHour}:00 - ${config.nightModeEndHour}:00)")
        } else {
            _isNightModeActive.value = false
            _brightnessLevel.value = 1.0f
            _warmFilterStrength.value = 0f
        }
    }
    
    /**
     * 用户确认休息提醒后调用
     */
    fun dismissRestReminder() {
        _showRestReminder.value = false
    }
    
    /**
     * 用户休息后重置使用时长
     */
    fun resetUsageTime() {
        usageMinutes = 0
        _showRestReminder.value = false
        Logger.d(TAG, " 使用时长已重置")
    }
    
    /**
     * 手动切换护眼模式
     */
    fun toggleForceEnabled(enabled: Boolean) {
        config = config.copy(forceEnabled = enabled)
        saveConfig()
        checkNightModeStatus()
        Logger.d(TAG, " 手动${if (enabled) "开启" else "关闭"}护眼模式")
    }
    
    private suspend fun loadConfigSuspend() {
        try {
            val context = PluginManager.getContext()
            val jsonStr = PluginStore.getConfigJson(context, id)
            if (jsonStr != null) {
                config = Json.decodeFromString<EyeProtectionConfig>(jsonStr)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "加载配置失败", e)
        }
    }
    
    private fun saveConfig() {
        ioScope.launch {
            try {
                val context = PluginManager.getContext()
                PluginStore.setConfigJson(context, id, Json.encodeToString(config))
            } catch (e: Exception) {
                Logger.e(TAG, "保存配置失败", e)
            }
        }
    }
    
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        // 状态
        var nightModeEnabled by remember { mutableStateOf(config.nightModeEnabled) }
        var nightModeStartHour by remember { mutableStateOf(config.nightModeStartHour) }
        var nightModeEndHour by remember { mutableStateOf(config.nightModeEndHour) }
        var usageReminderEnabled by remember { mutableStateOf(config.usageReminderEnabled) }
        var usageDurationMinutes by remember { mutableStateOf(config.usageDurationMinutes) }
        var brightnessLevel by remember { mutableStateOf(config.brightnessLevel) }
        var warmFilterStrength by remember { mutableStateOf(config.warmFilterStrength) }
        var forceEnabled by remember { mutableStateOf(config.forceEnabled) }
        
        // 加载配置
        LaunchedEffect(Unit) {
            loadConfigSuspend()
            nightModeEnabled = config.nightModeEnabled
            nightModeStartHour = config.nightModeStartHour
            nightModeEndHour = config.nightModeEndHour
            usageReminderEnabled = config.usageReminderEnabled
            usageDurationMinutes = config.usageDurationMinutes
            brightnessLevel = config.brightnessLevel
            warmFilterStrength = config.warmFilterStrength
            forceEnabled = config.forceEnabled
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ========== 手动开关 ==========
            IOSSwitchItem(
                icon = CupertinoIcons.Default.Star,
                title = "立即开启护眼模式",
                subtitle = "手动强制开启，不受时间段限制",
                checked = forceEnabled,
                onCheckedChange = { newValue ->
                    forceEnabled = newValue
                    config = config.copy(forceEnabled = newValue)
                    scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                    toggleForceEnabled(newValue)
                },
                iconTint = Color(0xFFFFB74D)
            )
            
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
            
            // ========== 定时护眼模式 ==========
            IOSSwitchItem(
                icon = CupertinoIcons.Default.Moon,
                title = "定时护眼模式",
                subtitle = "${nightModeStartHour}:00 - ${nightModeEndHour}:00 自动开启",
                checked = nightModeEnabled,
                onCheckedChange = { newValue ->
                    nightModeEnabled = newValue
                    config = config.copy(nightModeEnabled = newValue)
                    scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                    checkNightModeStatus()
                },
                iconTint = Color(0xFF7E57C2)
            )
            
            // 时间段选择（仅在定时模式开启时显示）
            if (nightModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 开始时间
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "开始时间",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TimePickerDropdown(
                            selectedHour = nightModeStartHour,
                            onHourSelected = { hour ->
                                nightModeStartHour = hour
                                config = config.copy(nightModeStartHour = hour)
                                scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                                checkNightModeStatus()
                            }
                        )
                    }
                    
                    Text(
                        "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 结束时间
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "结束时间",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TimePickerDropdown(
                            selectedHour = nightModeEndHour,
                            onHourSelected = { hour ->
                                nightModeEndHour = hour
                                config = config.copy(nightModeEndHour = hour)
                                scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                                checkNightModeStatus()
                            }
                        )
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
            
            // ========== 使用时长提醒 ==========
            IOSSwitchItem(
                icon = CupertinoIcons.Default.Clock,
                title = "使用时长提醒",
                subtitle = "每 ${usageDurationMinutes} 分钟提醒休息",
                checked = usageReminderEnabled,
                onCheckedChange = { newValue ->
                    usageReminderEnabled = newValue
                    config = config.copy(usageReminderEnabled = newValue)
                    scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                },
                iconTint = Color(0xFF42A5F5)
            )
            
            // 时长选择（仅在开启时显示）
            if (usageReminderEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier.padding(start = 56.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            FilterChip(
                                selected = usageDurationMinutes == minutes,
                                onClick = {
                                    usageDurationMinutes = minutes
                                    config = config.copy(usageDurationMinutes = minutes)
                                    scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                                },
                                label = { Text("${minutes}分钟") }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== 显示调节 ==========
            Text(
                text = "显示调节",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 亮度调节
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            CupertinoIcons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("亮度", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "${(brightnessLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = brightnessLevel,
                    onValueChange = { newValue ->
                        brightnessLevel = newValue
                        config = config.copy(brightnessLevel = newValue)
                        _brightnessLevel.value = newValue
                    },
                    onValueChangeFinished = {
                        scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                    },
                    valueRange = 0.3f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 暖色滤镜
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            CupertinoIcons.Default.SunMax,
                            contentDescription = null,
                            tint = Color(0xFFFF7043),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("暖色滤镜", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "${(warmFilterStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = warmFilterStrength,
                    onValueChange = { newValue ->
                        warmFilterStrength = newValue
                        config = config.copy(warmFilterStrength = newValue)
                        _warmFilterStrength.value = newValue
                    },
                    onValueChangeFinished = {
                        scope.launch { PluginStore.setConfigJson(context, id, Json.encodeToString(config)) }
                    },
                    valueRange = 0f..0.5f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "增加暖色可减少蓝光，保护眼睛",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== 效果预览 ==========
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 1f - brightnessLevel)
                    .copy(red = (1f - brightnessLevel) + warmFilterStrength * 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(
                                red = warmFilterStrength * 0.2f,
                                green = warmFilterStrength * 0.1f,
                                blue = 0f,
                                alpha = warmFilterStrength
                            )
                        )
                ) {
                    Text(
                        "效果预览",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    companion object {
        //  单例获取插件实例（用于 Overlay 层访问状态）
        fun getInstance(): EyeProtectionPlugin? {
            return PluginManager.plugins.find { it.plugin.id == "eye_protection" }?.plugin as? EyeProtectionPlugin
        }
    }
}

/**
 * 时间选择器下拉组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDropdown(
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = String.format("%02d:00", selectedHour),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (0..23).forEach { hour ->
                DropdownMenuItem(
                    text = { Text(String.format("%02d:00", hour)) },
                    onClick = {
                        onHourSelected(hour)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 夜间护眼配置
 */
@Serializable
data class EyeProtectionConfig(
    // 定时护眼模式
    val nightModeEnabled: Boolean = false,
    val nightModeStartHour: Int = 22,     // 22:00 开始
    val nightModeEndHour: Int = 7,        // 07:00 结束
    
    // 使用时长提醒
    val usageReminderEnabled: Boolean = true,
    val usageDurationMinutes: Int = 30,   // 每 30 分钟提醒
    
    // 显示调节
    val brightnessLevel: Float = 0.7f,    // 亮度等级 (0.3 ~ 1.0)
    val warmFilterStrength: Float = 0.2f, // 暖色滤镜强度 (0 ~ 0.5)
    
    // 手动强制开启
    val forceEnabled: Boolean = false
)
