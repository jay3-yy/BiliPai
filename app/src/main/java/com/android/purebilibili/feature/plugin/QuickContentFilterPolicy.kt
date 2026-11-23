package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.dynamic.extractDynamicFilterText

enum class QuickDynamicTypeShortcut(val label: String) {
    FORWARD("以后自动隐藏所有转发动态"),
    LOTTERY("以后自动隐藏所有抽奖动态"),
    GOODS("以后自动隐藏所有带货动态"),
    RESERVE("以后自动隐藏所有预约动态"),
    ARTICLE("以后自动隐藏所有专栏动态"),
    LIVE_RCMD("以后自动隐藏所有直播推荐动态"),
}

data class QuickContentFilterTarget(
    val sourceLabel: String,
    val previewText: String,
    val defaultScope: ContentFilterScope,
    val candidateKeywords: List<String>,
    val authorMid: Long = 0L,
    val authorName: String = "",
    val authorFace: String = "",
    val dynamicTypeShortcuts: List<QuickDynamicTypeShortcut> = emptyList(),
) {
    companion object {
        fun fromVideo(video: VideoItem): QuickContentFilterTarget {
            val extras = listOfNotNull(video.tname.takeIf { it.isNotBlank() })
            return QuickContentFilterTarget(
                sourceLabel = "已隐藏该视频",
                previewText = video.title,
                defaultScope = ContentFilterScope.VIDEO,
                candidateKeywords = extractQuickFilterCandidates(video.title, extras),
                authorMid = video.owner.mid,
                authorName = video.owner.name,
                authorFace = video.owner.face,
            )
        }

        fun fromDynamic(item: DynamicItem): QuickContentFilterTarget {
            val text = extractDynamicFilterText(item)
            val author = item.modules.module_author
            val shortcuts = buildList {
                if (item.type == "DYNAMIC_TYPE_FORWARD") add(QuickDynamicTypeShortcut.FORWARD)
                val additional = item.modules.module_dynamic?.additional
                val origAdditional = item.orig?.modules?.module_dynamic?.additional
                if (additional?.goods != null || additional?.type == "ADDITIONAL_TYPE_GOODS" ||
                    origAdditional?.goods != null || origAdditional?.type == "ADDITIONAL_TYPE_GOODS"
                ) {
                    add(QuickDynamicTypeShortcut.GOODS)
                }
                if (additional?.type == "ADDITIONAL_TYPE_LOTTERY" ||
                    origAdditional?.type == "ADDITIONAL_TYPE_LOTTERY" ||
                    text.contains("互动抽奖")
                ) {
                    add(QuickDynamicTypeShortcut.LOTTERY)
                }
                if (additional?.reserve != null || additional?.type == "ADDITIONAL_TYPE_RESERVE" ||
                    origAdditional?.reserve != null || origAdditional?.type == "ADDITIONAL_TYPE_RESERVE"
                ) {
                    add(QuickDynamicTypeShortcut.RESERVE)
                }
                if (item.type == "DYNAMIC_TYPE_ARTICLE" || item.orig?.type == "DYNAMIC_TYPE_ARTICLE") {
                    add(QuickDynamicTypeShortcut.ARTICLE)
                }
                if (item.type == "DYNAMIC_TYPE_LIVE_RCMD" || item.orig?.type == "DYNAMIC_TYPE_LIVE_RCMD") {
                    add(QuickDynamicTypeShortcut.LIVE_RCMD)
                }
            }
            return QuickContentFilterTarget(
                sourceLabel = "已隐藏该动态",
                previewText = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    .ifBlank { author?.name?.let { "$it 的动态" } ?: "该动态" },
                defaultScope = ContentFilterScope.DYNAMIC,
                candidateKeywords = extractQuickFilterCandidates(text),
                authorMid = author?.mid ?: 0L,
                authorName = author?.name.orEmpty(),
                authorFace = author?.face.orEmpty(),
                dynamicTypeShortcuts = shortcuts,
            )
        }
    }
}

private val bracketTagRegex = Regex("""[#【《「\[]([^#】》」\]\n]{2,16})[#】》」\]]""")
private val urlCleanupRegex = Regex("""(?i)https?://\S+""")
private val splitTokenRegex = Regex("""[\s,，。！？!?、/|｜·:：;；()（）\[\]【】《》「」"'“”~～\-—]+""")

/** 从隐藏内容的标题或正文中提取适合一键屏蔽的候选词（话题/括注标签优先，最多 8 个）。 */
internal fun extractQuickFilterCandidates(
    rawText: String,
    extraLabels: List<String> = emptyList(),
    limit: Int = 8,
): List<String> {
    if (rawText.isBlank() && extraLabels.isEmpty()) return emptyList()
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<String>()
    fun tryAdd(candidate: String) {
        val word = candidate.trim()
        if (word.length !in 2..14) return
        if (word.all { it.isDigit() }) return
        if (seen.add(word.lowercase()) && result.size < limit) {
            result.add(word)
        }
    }

    val cleaned = rawText.replace(urlCleanupRegex, " ")
    bracketTagRegex.findAll(cleaned).forEach { match ->
        tryAdd(match.groupValues[1])
    }
    extraLabels.forEach(::tryAdd)
    splitTokenRegex.split(cleaned).forEach { segment ->
        if (segment.length in 2..12) {
            tryAdd(segment)
        }
    }
    return result
}
