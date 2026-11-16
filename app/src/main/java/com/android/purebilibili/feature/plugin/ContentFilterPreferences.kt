package com.android.purebilibili.feature.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.feature.settings.SettingsIconRole
import com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon
import kotlinx.coroutines.launch

@Composable
internal fun SharedContentFilterPreferences() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by remember(context) { ContentFilterRules.observeSettings(context) }
        .collectAsStateWithLifecycle<ContentFilterSnapshot?>(initialValue = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var video by rememberSaveable { mutableStateOf(false) }
    var comment by rememberSaveable { mutableStateOf(true) }
    var danmaku by rememberSaveable { mutableStateOf(false) }
    AppPreference(
        title = "共享屏蔽词",
        subtitle = "一处编辑，按所选范围应用；已有标题正则和弹幕规则继续生效",
        value = rules?.let { "${it.keywords.size} 个" } ?: "加载中",
        icon = rememberSettingsSemanticIcon(SettingsIconRole.COMMENT_BLOCKED_KEYWORDS),
        onClick = rules?.let { current -> {
            text = current.keywords.joinToString("\n")
            video = ContentFilterScope.VIDEO in current.scopes
            comment = ContentFilterScope.COMMENT in current.scopes
            danmaku = ContentFilterScope.DANMAKU in current.scopes
            editing = true
        } },
    )
    LegacyContentKeywordEditors(rules)
    if (editing) AppAlertDialog(
        onDismissRequest = { editing = false },
        title = { AppText("共享屏蔽词") },
        text = {
            Column {
                AppText("每行一个普通文本关键词，不区分大小写。保存后更新已加载内容，解除屏蔽后恢复原内容。关闭内容过滤插件后暂停共享关键词过滤。")
                AppTextField(value = text, onValueChange = { text = it }, label = "屏蔽词",
                    singleLine = false, minLines = 3, maxLines = 5)
                AppSwitchPreference(title = "视频标题", checked = video, onCheckedChange = { video = it },
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK))
                AppSwitchPreference(title = "评论正文", checked = comment, onCheckedChange = { comment = it },
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTION_COMMENT))
                AppSwitchPreference(title = "弹幕内容", checked = danmaku, onCheckedChange = { danmaku = it },
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PIP_DANMAKU))
            }
        },
        confirmButton = { AppTextButton(onClick = {
            scope.launch {
                ContentFilterRules.save(context, text, buildSet {
                    if (video) add(ContentFilterScope.VIDEO)
                    if (comment) add(ContentFilterScope.COMMENT)
                    if (danmaku) add(ContentFilterScope.DANMAKU)
                })
                editing = false
            }
        }, enabled = video || comment || danmaku || text.isBlank()) { AppText("保存") } },
        dismissButton = { AppTextButton(onClick = { editing = false }) { AppText("取消") } },
    )
}

@Composable
internal fun SharedBlockedUsersPreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { com.android.purebilibili.data.repository.BlockedUpRepository.getInstance(context) }
    val users by remember(repository) { repository.getAllBlockedUps() }
        .collectAsStateWithLifecycle<List<com.android.purebilibili.core.database.entity.BlockedUp>?>(initialValue = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val lines = remember(text) { text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList() }
    val valid = lines.all { it.toLongOrNull()?.let { mid -> mid > 0L } == true }
    AppPreference(
        title = "用户黑名单", subtitle = "与本地黑名单共用，跨页面生效，不受插件开关影响",
        value = users?.let { "${it.size} 个" } ?: "加载中",
        icon = rememberSettingsSemanticIcon(SettingsIconRole.BLOCKED_LIST),
        onClick = users?.let { current -> {
            text = current.joinToString("\n") { it.mid.toString() }
            editing = true
        } },
    )
    if (editing) AppAlertDialog(
        onDismissRequest = { editing = false },
        title = { AppText("用户黑名单") },
        text = { Column {
            AppText("每行一个正整数 UID。此处编辑本地黑名单；已有用户的名称和头像会保留。")
            AppTextField(value = text, onValueChange = { text = it }, singleLine = false, minLines = 3, maxLines = 8,
                isError = !valid, supportingText = { if (!valid) AppText("请填写有效的正整数 UID") })
        } },
        confirmButton = { AppTextButton(enabled = valid, onClick = {
            scope.launch {
                val wanted = lines.map(String::toLong).toSet()
                repository.replaceLocalBlockedUserIds(wanted)
                editing = false
            }
        }) { AppText("保存") } },
        dismissButton = { AppTextButton(onClick = { editing = false }) { AppText("取消") } },
    )
}

@Composable
private fun LegacyContentKeywordEditors(rules: ContentFilterSnapshot?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val regexError = remember(text, editor) {
        if (editor != "video") null else try {
            com.android.purebilibili.core.store.compileContentTitlePattern(text)
            null
        } catch (_: IllegalArgumentException) { "正则表达式无效，请修改后保存" }
    }
    AppTextButton(enabled = rules != null, onClick = {
        text = rules!!.videoTitlePattern
        editor = "video"
    }) { AppText("视频标题正则") }
    AppTextButton(enabled = rules != null, onClick = {
        text = rules!!.adVideoKeywords.joinToString("\n")
        editor = "ad"
    }) { AppText("去广告增强的标题屏蔽词") }
    AppTextButton(enabled = rules != null, onClick = {
        text = rules!!.legacyDanmakuKeywords.joinToString("\n")
        editor = "danmaku"
    }) { AppText("弹幕增强的屏蔽词") }
    if (editor != null) AppAlertDialog(
        onDismissRequest = { editor = null },
        title = { AppText(when (editor) {
            "video" -> "视频标题正则"
            "danmaku" -> "弹幕增强的屏蔽词"
            else -> "标题屏蔽词"
        }) },
        text = {
            Column {
                AppText(if (editor == "video") "保留原有标题正则规则，仅过滤视频，随内容过滤插件启停。每行一条。"
                    else if (editor == "danmaku") "保留原有弹幕普通文本规则，随弹幕增强插件及其过滤开关启停。每行一个关键词。"
                    else "保留原有普通文本规则，仅过滤视频，随去广告增强插件启停。每行一个关键词。")
                AppTextField(value = text, onValueChange = { text = it }, singleLine = false,
                    minLines = 3, maxLines = 8, isError = regexError != null,
                    supportingText = { regexError?.let { AppText(it) } })
            }
        },
        confirmButton = { AppTextButton(enabled = regexError == null, onClick = {
            scope.launch {
                if (editor == "video") ContentFilterRules.saveVideoPattern(context, text)
                else if (editor == "danmaku") ContentFilterRules.saveDanmakuKeywords(context, text)
                else ContentFilterRules.saveAdKeywords(context, text)
                editor = null
            }
        }) { AppText("保存") } },
        dismissButton = { AppTextButton(onClick = { editor = null }) { AppText("取消") } },
    )
}
