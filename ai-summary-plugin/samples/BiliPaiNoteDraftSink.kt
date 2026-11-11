// 接线示例（不是可直接编译的文件）：把本插件的结果写进 BiliPai 已有的「视频笔记草稿」。
// 复用 BiliPai 的 buildVideoNoteDraftFromAiSummary()，无需自己实现富文本/时间戳块。
package com.bilipai.aisummary.samples

import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.pipeline.NoteDraftSink

// TODO: 换成 BiliPai 的真实 import
// import com.android.purebilibili.data.model.response.AiModelResult
// import com.android.purebilibili.data.model.response.AiOutline
// import com.android.purebilibili.data.model.response.AiPartOutline
// import com.android.purebilibili.data.model.response.AiSummaryData
// import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
// import com.android.purebilibili.feature.video.note.buildVideoNoteDraftFromAiSummary

/**
 * saveDocument 接宿主现有链路，二选一：
 *  a) 交给 VideoNoteEditorSheet 的 onDocumentChange / onSave（推荐，用户还能再编辑）；
 *  b) 直接 VideoNoteRepository.savePrivateNote(VideoNoteSavePayload(...))（参数以宿主定义为准）。
 */
class BiliPaiNoteDraftSink(
    private val saveDocument: (Any /* VideoNoteEditorDocument */) -> Unit
) : NoteDraftSink {

    override fun save(result: SummaryResult, markdown: String) {
        // 1) 把自定义结果映射成 B 站官方 AiSummaryData 结构
        val aiSummary = AiSummaryData(
            code = 0,
            modelResult = AiModelResult(
                resultType = 2, // 2 = 摘要 + 大纲
                summary = result.overview,
                outline = result.chapters.map { ch ->
                    AiOutline(
                        title = ch.title,
                        timestamp = ch.startSec.toLong(), // 官方结构是「秒」
                        partOutline = ch.points.map { point ->
                            AiPartOutline(
                                timestamp = (if (point.hasTime) point.startSec else ch.startSec).toLong(),
                                content = point.text
                            )
                        }
                    )
                }
            )
        )

        // 2) 复用宿主已有的草稿构造函数
        val document = buildVideoNoteDraftFromAiSummary(
            title = result.title,
            aiSummary = aiSummary,
            cid = result.cid,
            pageIndex = 0,
            cidCount = 1,
            existingDocument = null // 若已有笔记，传进去会追加而不是覆盖
        )
        saveDocument(document)
    }
}

// --- 以下为占位声明，仅示意 BiliPai 侧的类型形状；接入时删除 ---
private data class AiSummaryData(val code: Int, val modelResult: AiModelResult?)
private data class AiModelResult(val resultType: Int, val summary: String, val outline: List<AiOutline>)
private data class AiOutline(val title: String, val timestamp: Long, val partOutline: List<AiPartOutline>)
private data class AiPartOutline(val timestamp: Long, val content: String)
private fun buildVideoNoteDraftFromAiSummary(
    title: String, aiSummary: AiSummaryData, cid: Long,
    pageIndex: Int, cidCount: Int, existingDocument: Any?
): Any = Any()
