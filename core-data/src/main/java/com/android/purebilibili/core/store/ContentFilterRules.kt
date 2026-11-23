package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*

enum class ContentFilterScope { VIDEO, COMMENT, DANMAKU, DYNAMIC }
enum class ContentVideoSurface { RECOMMEND, POPULAR, RANK, SEARCH, RELATED, OTHER }

data class ContentVideoKeywordPolicy(
    val whitelist: Set<Long> = emptySet(),
    val exemptFollowed: Boolean = true,
    val applyToPopular: Boolean = false,
    val applyToRank: Boolean = false,
    val applyToSearch: Boolean = false,
) {
    fun applies(surface: ContentVideoSurface): Boolean = when (surface) {
        ContentVideoSurface.POPULAR -> applyToPopular
        ContentVideoSurface.RANK -> applyToRank
        ContentVideoSurface.SEARCH -> applyToSearch
        else -> true
    }
}

data class ContentFilterSnapshot(
    val keywords: List<String> = emptyList(),
    val scopes: Set<ContentFilterScope> = setOf(ContentFilterScope.COMMENT),
    val blockedMids: Set<Long> = emptySet(),
    val blockGoodsReplies: Boolean = false,
    val keywordFilteringEnabled: Boolean = false,
    val videoTitlePattern: String = "",
    val adVideoKeywords: List<String> = emptyList(),
    val adFilteringEnabled: Boolean = false,
    val legacyDanmakuKeywords: List<String> = emptyList(),
    val danmakuFilteringEnabled: Boolean = false,
    val videoPolicy: ContentVideoKeywordPolicy = ContentVideoKeywordPolicy(),
    val hideAiReplies: Boolean = false,
    val filterRepeatedReplies: Boolean = false,
    val aiReplyAllowedMids: Set<Long> = emptySet(),
) {
    private val titleRegex by lazy { compileContentTitlePattern(videoTitlePattern) }
    private val sharedKeywordMatcher by lazy { ContentKeywordMatcher.compile(keywords) }

    fun matchesSharedKeywords(text: String): Boolean = sharedKeywordMatcher.matches(text)

    fun firstMatchingSharedKeyword(text: String): String? = sharedKeywordMatcher.firstMatch(text)

    fun blocksText(text: String, scope: ContentFilterScope): Boolean =
        (keywordFilteringEnabled && scope in scopes && matchesSharedKeywords(text)) ||
            (scope == ContentFilterScope.VIDEO && (
                (keywordFilteringEnabled && titleRegex?.containsMatchIn(text) == true) ||
                    (adFilteringEnabled && adVideoKeywords.any { text.contains(it, ignoreCase = true) })
                )) ||
            (scope == ContentFilterScope.DANMAKU && danmakuFilteringEnabled &&
                legacyDanmakuKeywords.any { text.contains(it, ignoreCase = true) })

    val effectiveDanmakuKeywords: List<String> get() =
        (keywords.takeIf { keywordFilteringEnabled && ContentFilterScope.DANMAKU in scopes }.orEmpty() +
            legacyDanmakuKeywords.takeIf { danmakuFilteringEnabled }.orEmpty()).distinct()

    fun blocksUser(mid: Long): Boolean = mid in blockedMids

    fun blocksVideo(
        title: String, mid: Long, followed: Boolean = false,
        surface: ContentVideoSurface = ContentVideoSurface.OTHER,
    ): Boolean = videoFilterReason(title, mid, followed, surface) != null

    fun videoFilterReason(
        title: String, mid: Long, followed: Boolean = false,
        surface: ContentVideoSurface = ContentVideoSurface.OTHER,
    ): ContentFilterReason? {
        val contentAllowed = keywordFilteringEnabled && mid !in videoPolicy.whitelist &&
            !(surface == ContentVideoSurface.RECOMMEND && followed && videoPolicy.exemptFollowed)
        return when {
            blocksUser(mid) -> ContentFilterReason.BLOCKED_USER
            contentAllowed && ContentFilterScope.VIDEO in scopes && matchesSharedKeywords(title) ->
                ContentFilterReason.KEYWORD
            contentAllowed && videoPolicy.applies(surface) && titleRegex?.containsMatchIn(title) == true ->
                ContentFilterReason.TITLE_PATTERN
            adFilteringEnabled && adVideoKeywords.any { title.contains(it, true) } -> ContentFilterReason.AD_TITLE
            else -> null
        }
    }
}

/** 进程只订阅一份规则；页面按新增数据批次读取快照，绘制阶段不访问存储。 */
object ContentFilterRules {
    private val words = stringPreferencesKey("content.shared_keywords")
    private val scopeKey = stringSetPreferencesKey("content.shared_keyword_scopes")
    private val legacyWords = stringPreferencesKey(ContentDisplaySettings.COMMENT_BLOCKED_KEYWORDS)
    private val repeatedReplies = booleanPreferencesKey("content.filter_repeated_replies")
    private val hideAiReplies = booleanPreferencesKey("content.hide_ai_replies")
    private val aiReplyAllowedMids = stringSetPreferencesKey("content.ai_reply_allowed_mids")
    private val goods = booleanPreferencesKey(ContentDisplaySettings.BLOCK_GOODS_REPLY)
    private val videoPattern = stringPreferencesKey("content.video_title_pattern")
    private val adWords = stringPreferencesKey("content.ad_video_keywords")
    private val danmakuWords = stringPreferencesKey("content.legacy_danmaku_keywords")
    private fun store(context: Context) = MultiProcessPreferences.get(context, "content_display_prefs")

    private val mutableCurrent = MutableStateFlow<ContentFilterSnapshot?>(null)
    val current = mutableCurrent.asStateFlow()
    private val enabled = MutableStateFlow(false)
    private val adEnabled = MutableStateFlow(false)
    private val danmakuEnabled = MutableStateFlow(false)
    private val videoPolicy = MutableStateFlow(ContentVideoKeywordPolicy())
    @Volatile private var processCollectorPrepared = false

    fun prepareProcessCollector() { processCollectorPrepared = true }
    fun setKeywordFilteringEnabled(value: Boolean) { enabled.value = value }
    fun setAdFilteringEnabled(value: Boolean) { adEnabled.value = value }
    fun setDanmakuFilteringEnabled(value: Boolean) { danmakuEnabled.value = value }
    fun setVideoPolicy(value: ContentVideoKeywordPolicy) { videoPolicy.value = value }

    fun observeSettings(context: Context): Flow<ContentFilterSnapshot> = store(context).data.map { prefs ->
        ContentFilterSnapshot(
            keywords = parseCommentKeywords(prefs[words] ?: prefs[legacyWords].orEmpty()),
            scopes = prefs[scopeKey]?.map { ContentFilterScope.valueOf(it) }?.toSet()
                ?: setOf(ContentFilterScope.COMMENT),
            blockGoodsReplies = prefs[goods] ?: false,
            hideAiReplies = prefs[hideAiReplies] ?: false,
            filterRepeatedReplies = prefs[repeatedReplies] ?: false,
            aiReplyAllowedMids = prefs[aiReplyAllowedMids].orEmpty().mapNotNull(String::toLongOrNull).toSet(),
            videoTitlePattern = prefs[videoPattern].orEmpty(),
            adVideoKeywords = parseCommentKeywords(prefs[adWords].orEmpty()),
            legacyDanmakuKeywords = parseCommentKeywords(prefs[danmakuWords].orEmpty()),
        )
    }.distinctUntilChanged()

    /** 由 Application 的进程作用域持有，取消与异常均交还调用方。 */
    suspend fun collect(context: Context, blockedMids: Flow<Set<Long>>) {
        combine(observeSettings(context), blockedMids.distinctUntilChanged(), enabled, adEnabled, danmakuEnabled) { rules, users, active, adActive, dmActive ->
            rules.copy(blockedMids = users, keywordFilteringEnabled = active, adFilteringEnabled = adActive,
                danmakuFilteringEnabled = dmActive)
        }.combine(videoPolicy) { rules, policy -> rules.copy(videoPolicy = policy) }
            .collect { mutableCurrent.value = it }
    }

    suspend fun snapshot(context: Context): ContentFilterSnapshot =
        if (processCollectorPrepared) current.filterNotNull().first()
        else observeSettings(context).first().copy(keywordFilteringEnabled = enabled.value, adFilteringEnabled = adEnabled.value,
            danmakuFilteringEnabled = danmakuEnabled.value)

    suspend fun migrateDanmakuKeywords(context: Context, keywords: List<String>) {
        store(context).edit { if (it[danmakuWords] == null) it[danmakuWords] = keywords.joinToString("\n") }
    }

    suspend fun saveDanmakuKeywords(context: Context, text: String) {
        store(context).edit { it[danmakuWords] = parseCommentKeywords(text).joinToString("\n") }
    }

    suspend fun migrateVideoPattern(context: Context, pattern: String) {
        compileContentTitlePattern(pattern)
        store(context).edit { if (it[videoPattern] == null) it[videoPattern] = pattern }
    }

    suspend fun migrateAdKeywords(context: Context, keywords: List<String>) {
        store(context).edit { if (it[adWords] == null) it[adWords] = keywords.joinToString("\n") }
    }

    suspend fun saveVideoPattern(context: Context, pattern: String) {
        compileContentTitlePattern(pattern)
        store(context).edit { it[videoPattern] = pattern }
    }

    suspend fun saveAdKeywords(context: Context, text: String) {
        store(context).edit { it[adWords] = parseCommentKeywords(text).joinToString("\n") }
    }

    suspend fun saveRepeatedReplyFilter(context: Context, enabled: Boolean) {
        store(context).edit { it[repeatedReplies] = enabled }
    }

    suspend fun saveAiReplyFilter(context: Context, hide: Boolean, allowedMids: Set<Long>) {
        store(context).edit {
            it[hideAiReplies] = hide
            it[aiReplyAllowedMids] = allowedMids.map(Long::toString).toSet()
        }
    }

    suspend fun save(context: Context, text: String, scopes: Set<ContentFilterScope>) {
        store(context).edit {
            it[words] = parseCommentKeywords(text).joinToString("\n")
            it[scopeKey] = scopes.map { scope -> scope.name }.toSet()
            it.remove(legacyWords)
        }
    }

    /** 增量追加共享屏蔽词并合并作用域，不覆盖已有词条；返回本次实际新增词数。 */
    suspend fun appendKeywords(
        context: Context,
        newKeywords: List<String>,
        ensureScopes: Set<ContentFilterScope>,
    ): Int {
        val cleaned = newKeywords.map(String::trim).filter(String::isNotEmpty)
        var addedCount = 0
        store(context).edit { prefs ->
            val existing = parseCommentKeywords(prefs[words] ?: prefs[legacyWords].orEmpty())
            val seenLower = existing.mapTo(hashSetOf()) { it.lowercase() }
            val merged = existing.toMutableList()
            for (word in cleaned) {
                if (seenLower.add(word.lowercase())) {
                    merged.add(word)
                    addedCount++
                }
            }
            val currentScopes = prefs[scopeKey]?.mapNotNull { name ->
                ContentFilterScope.entries.firstOrNull { it.name == name }
            }?.toSet() ?: setOf(ContentFilterScope.COMMENT)
            val nextScopes = currentScopes + ensureScopes
            prefs[words] = merged.joinToString("\n")
            prefs[scopeKey] = nextScopes.map { it.name }.toSet()
            prefs.remove(legacyWords)
        }
        return addedCount
    }

    /** 从共享屏蔽词中移除指定规则（忽略大小写），返回是否确实移除了词条。 */
    suspend fun removeKeyword(context: Context, keyword: String): Boolean {
        val target = keyword.trim()
        if (target.isEmpty()) return false
        var removed = false
        store(context).edit { prefs ->
            val existing = parseCommentKeywords(prefs[words] ?: prefs[legacyWords].orEmpty())
            val remaining = existing.filterNot { it.equals(target, ignoreCase = true) }
            if (remaining.size != existing.size) {
                removed = true
                prefs[words] = remaining.joinToString("\n")
                prefs.remove(legacyWords)
            }
        }
        return removed
    }
}

/** 保留旧标题规则中各行合并、分组及忽略大小写的匹配语义。 */
fun compileContentTitlePattern(text: String): Regex? {
    val patterns = parseCommentKeywords(text).map {
        if ('|' in it && !it.startsWith("(")) "($it)" else it
    }
    return patterns.takeIf { it.isNotEmpty() }?.joinToString("|")?.let { Regex(it, RegexOption.IGNORE_CASE) }
}

class ContentKeywordMatcher private constructor(
    private val rules: List<CompiledContentKeywordRule>,
) {
    fun matches(text: String): Boolean = rules.any { it.matches(text) }

    fun firstMatch(text: String): String? = rules.firstOrNull { it.matches(text) }?.rawRule

    companion object {
        val EMPTY = ContentKeywordMatcher(emptyList())

        fun compile(rawKeywords: List<String>): ContentKeywordMatcher =
            if (rawKeywords.isEmpty()) EMPTY
            else ContentKeywordMatcher(compileSharedContentKeywordRules(rawKeywords))
    }
}

internal sealed interface CompiledContentKeywordRule {
    val rawRule: String
    fun matches(text: String): Boolean

    data class Contains(override val rawRule: String, val keyword: String) : CompiledContentKeywordRule {
        override fun matches(text: String): Boolean = text.contains(keyword, ignoreCase = true)
    }

    data class ExactWord(override val rawRule: String, val regex: Regex) : CompiledContentKeywordRule {
        override fun matches(text: String): Boolean = regex.containsMatchIn(text)
    }

    data class AllOf(override val rawRule: String, val parts: List<String>) : CompiledContentKeywordRule {
        override fun matches(text: String): Boolean = parts.all { text.contains(it, ignoreCase = true) }
    }

    data class Pattern(override val rawRule: String, val regex: Regex) : CompiledContentKeywordRule {
        override fun matches(text: String): Boolean = regex.containsMatchIn(text)
    }
}

/**
 * 共享屏蔽词支持四种写法（均忽略大小写）：
 * - 普通词：包含即屏蔽
 * - `=词`：精准匹配（仅在词边界/标点/括注处命中，避免误杀长词）
 * - `词1+词2`：同时包含所有词才屏蔽
 * - `regex:表达式`：正则匹配
 */
internal fun compileSharedContentKeywordRules(rawKeywords: List<String>): List<CompiledContentKeywordRule> {
    if (rawKeywords.isEmpty()) return emptyList()
    return rawKeywords.mapNotNull { raw ->
        val token = raw.trim()
        if (token.isEmpty()) return@mapNotNull null
        when {
            token.startsWith("regex:", ignoreCase = true) -> {
                val pattern = token.substringAfter(':').trim()
                if (pattern.isEmpty()) null
                else runCatching { CompiledContentKeywordRule.Pattern(token, Regex(pattern, RegexOption.IGNORE_CASE)) }.getOrNull()
            }
            token.startsWith("=") -> {
                val word = token.drop(1).trim()
                if (word.isEmpty()) null
                else {
                    val escaped = Regex.escape(word)
                    val boundaryRegex = Regex(
                        "(?:^|[\\s,，。！？!?、/|｜·:：;；()（）\\[\\]【】《》「」\"'“”~～\\-—#])$escaped(?=$|[\\s,，。！？!?、/|｜·:：;；()（）\\[\\]【】《》「」\"'“”~～\\-—#])",
                        RegexOption.IGNORE_CASE,
                    )
                    CompiledContentKeywordRule.ExactWord(token, boundaryRegex)
                }
            }
            '+' in token -> {
                val parts = token.split('+').map(String::trim).filter(String::isNotEmpty)
                when {
                    parts.isEmpty() -> null
                    parts.size == 1 -> CompiledContentKeywordRule.Contains(token, parts.first())
                    else -> CompiledContentKeywordRule.AllOf(token, parts)
                }
            }
            else -> CompiledContentKeywordRule.Contains(token, token)
        }
    }
}
