package com.bilipai.aisummary.util

/**
 * 输入归一化。
 *
 * 真实场景：中文输入法（如 Gboard 在 zh-Hans 系统语言下）会把 `/` `:` `.` 等
 * ASCII 符号输入成全角 `／` `：` `．`，用户从聊天记录粘贴 URL 时也常带全角字符。
 * 这类 Base URL 会直接导致请求失败，且错误信息难以理解，因此在建配置时统一转换。
 */
object InputNormalizer {

    /** 全角 → 半角的映射（NFKC 常用子集，覆盖 URL/模型名会出现的字符）。 */
    private val FULLWIDTH_MAP: Map<Char, Char> = buildMap {
        // 全角 ASCII 区（FF01–FF5E）→ 对应半角（21–7E）
        for (code in 0xFF01..0xFF5E) put(code.toChar(), (code - 0xFEE0).toChar())
        // 中文标点里最容易被误打的几个
        put('\u3000', ' ') // 全角空格
        put('。', '.')
        put('，', ',')
        put('－', '-')
        put('—', '-')
        put('～', '~')
    }

    /** 把全角符号转成半角，并去掉首尾空白。 */
    fun normalizeAscii(input: String): String = buildString(input.length) {
        input.forEach { ch -> append(FULLWIDTH_MAP[ch] ?: ch) }
    }.trim()

    /**
     * 归一化 Base URL：全角转半角、去空白、去掉误加的 `/chat/completions` 尾巴、
     * 去掉结尾多余的 `/`（调用方会自行拼接）以及 HTTP 协议的前导空格。
     */
    fun normalizeBaseUrl(raw: String): String {
        var s = normalizeAscii(raw)
        if (s.isEmpty()) return s
        // 用户可能把完整 endpoint 贴进来
        s = s.replace(Regex("/chat/completions/?$", RegexOption.IGNORE_CASE), "")
        // 统一小写协议头，避免 HTTP:// 这类写法
        s = s.replace(Regex("^(https?)://", RegexOption.IGNORE_CASE)) { it.value.lowercase() }
        return s.trimEnd('/')
    }

    /** 归一化模型名（同样可能被中文输入法污染，比如 camelCase 里的符号）。 */
    fun normalizeModel(raw: String): String = normalizeAscii(raw)

    /** Base URL 是否看起来合法（只做格式判断，不发请求）。 */
    fun looksLikeHttpUrl(url: String): Boolean =
        Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE).containsMatchIn(url)
}
