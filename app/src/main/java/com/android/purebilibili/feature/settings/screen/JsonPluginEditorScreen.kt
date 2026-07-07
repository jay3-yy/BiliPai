// 文件路径: feature/settings/JsonPluginEditorScreen.kt
package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.android.purebilibili.core.plugin.json.JsonRulePlugin
import com.android.purebilibili.core.plugin.json.Rule
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.components.IOSAdaptiveTextField
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.rememberAppBackIcon
import kotlinx.serialization.json.JsonPrimitive

/**
 * 🔧 JSON 插件编辑器界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonPluginEditorScreen(
    plugin: JsonRulePlugin,
    onBack: () -> Unit,
    onSave: (JsonRulePlugin) -> Unit
) {
    var name by remember { mutableStateOf(plugin.name) }
    var description by remember { mutableStateOf(plugin.description) }
    var rules by remember { mutableStateOf(plugin.rules.toMutableList()) }
    
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = "编辑 JSON 插件",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val updated = plugin.copy(
                            name = name,
                            description = description,
                            rules = rules
                        )
                        onSave(updated)
                    }) {
                        Icon(CupertinoIcons.Default.Checkmark, null)
                    }
                },
                colors = settingsSubpageTopAppBarColors()
            )
        },
        containerColor = settingsSubpageContainerColor()
    ) { padding ->
        JsonPluginEditorContent(
            modifier = Modifier.padding(padding),
            name = name,
            onNameChange = { name = it },
            description = description,
            onDescriptionChange = { description = it },
            rules = rules,
            onRulesChange = { rules = it.toMutableList() },
            pluginType = plugin.type
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonPluginEditorContent(
    modifier: Modifier = Modifier,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    rules: List<Rule>,
    onRulesChange: (List<Rule>) -> Unit,
    pluginType: String
) {
    if (pluginType != "json_rule") {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("内置插件无法编辑规则", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 基本信息
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("基本信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    IOSAdaptiveTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "插件名称"
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    IOSAdaptiveTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = "插件描述",
                        singleLine = false,
                        minLines = 2
                    )
                }
            }
        }
        
        // 规则列表
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("过滤规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                TextButton(onClick = {
                    onRulesChange(rules + Rule(field = "title", op = "contains", value = JsonPrimitive(""), action = "hide"))
                }) {
                    Icon(CupertinoIcons.Default.Plus, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加规则")
                }
            }
        }
        
        itemsIndexed(rules, key = { index, _ -> index }) { index, rule ->
            RuleEditor(
                rule = rule,
                pluginType = pluginType,
                onUpdate = { updatedRule ->
                    val newRules = rules.toMutableList()
                    newRules[index] = updatedRule
                    onRulesChange(newRules)
                },
                onDelete = {
                    val newRules = rules.toMutableList()
                    newRules.removeAt(index)
                    onRulesChange(newRules)
                }
            )
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    text = "点击 + 添加规则",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RuleEditor(
    rule: Rule,
    pluginType: String,
    onUpdate: (Rule) -> Unit,
    onDelete: () -> Unit
) {
    val fieldOptions = if (pluginType == "feed") {
        listOf(
            "title",
            "duration",
            "tid",
            "tname",
            "owner.mid",
            "owner.name",
            "stat.view",
            "stat.like",
            "stat.reply",
            "stat.danmaku",
            "stat.coin",
            "stat.favorite"
        )
    } else {
        listOf("content", "userId", "type")
    }
    
    val opOptions = listOf("eq", "ne", "lt", "le", "gt", "ge", "contains", "startsWith", "endsWith", "regex")
    val actionOptions = if (pluginType == "feed") listOf("hide") else listOf("hide", "highlight")
    
    var field by remember { mutableStateOf(rule.field ?: "title") }
    var op by remember { mutableStateOf(rule.op ?: "contains") }
    var value by remember { mutableStateOf(
        (rule.value as? JsonPrimitive)?.content ?: ""
    ) }
    var action by remember { mutableStateOf(rule.action) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card)),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "规则",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        CupertinoIcons.Default.Trash,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 字段选择
            DropdownSelector(
                label = "字段",
                value = field,
                options = fieldOptions,
                onSelect = { 
                    field = it
                    onUpdate(rule.copy(field = it))
                }
            )
            
            // 操作符选择
            DropdownSelector(
                label = "操作符",
                value = op,
                options = opOptions,
                onSelect = { 
                    op = it
                    onUpdate(rule.copy(op = it))
                }
            )
            
            // 值输入
            IOSAdaptiveTextField(
                value = value,
                onValueChange = { 
                    value = it
                    onUpdate(rule.copy(value = JsonPrimitive(it)))
                },
                label = "值",
                singleLine = true
            )
            
            // 动作选择
            DropdownSelector(
                label = "动作",
                value = action,
                options = actionOptions,
                onSelect = { 
                    action = it
                    onUpdate(rule.copy(action = it))
                }
            )
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value.ifEmpty { "选择..." })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
