package com.android.purebilibili.feature.live.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.data.model.response.LiveMessageEmote
import com.android.purebilibili.data.repository.LiveEmoticonPackage
import com.android.purebilibili.feature.live.liveDraftEmoteResources
import com.android.purebilibili.feature.live.liveDraftPreviewSegments
import com.android.purebilibili.feature.live.projectLiveRichDraftEdit
import kotlin.math.roundToInt

/** 原始草稿由页面持有；原生可编辑跨度仅负责回复前缀与表情的行内呈现。 */
@Composable
internal fun LiveRichDanmakuEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    replyName: String?,
    onClearReply: () -> Unit,
    packages: List<LiveEmoticonPackage>,
    showingEmoticons: Boolean,
    isSending: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emotes = remember(packages) { liveDraftEmoteResources(packages) }
    val textColor = AppContentColors.primaryText().toArgb()
    val hintColor = AppContentColors.secondaryText().toArgb()
    val accent = AppContentColors.accent()
    val replyColor = accent.toArgb()
    val replyBackground = accent.copy(alpha = 0.12f).toArgb()
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize.value
    AndroidView(
        modifier = modifier,
        factory = { LiveRichEditText(it) },
        update = { editor ->
            editor.onDraftChanged = onValueChange
            editor.onReplyRemoved = onClearReply
            editor.onSend = onSend
            editor.setTextColor(textColor)
            editor.setHintTextColor(hintColor)
            editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            editor.highlightColor = accent.copy(alpha = 0.25f).toArgb()
            if (android.os.Build.VERSION.SDK_INT >= 29) editor.textCursorDrawable?.setTint(replyColor)
            editor.replyColor = replyColor
            editor.replyBackground = replyBackground
            editor.isEnabled = !isSending
            editor.bind(value, replyName?.let { "@$it " }.orEmpty(), emotes)
            editor.setKeyboardVisible(!showingEmoticons && !isSending)
        },
        onRelease = { it.release() },
    )
}

private class LiveRichEditText(context: Context) : EditText(context) {
    var onDraftChanged: (TextFieldValue) -> Unit = {}
    var onReplyRemoved: () -> Unit = {}
    var onSend: (String) -> Unit = {}
    var replyColor: Int = 0
    var replyBackground: Int = 0
    private var draft = TextFieldValue()
    private var prefix = ""
    private var emotes = emptyMap<String, LiveMessageEmote>()
    private var ready = false
    private var applying = false
    private var changingText = false
    private var imeEditDepth = 0
    private var released = false
    private var keyboardVisible: Boolean? = null
    private val requests = mutableMapOf<String, Disposable>()
    private val images = mutableMapOf<String, Drawable>()
    private val spans = mutableListOf<Any>()
    private val keyboardRequest = Runnable {
        if (!released && isAttachedToWindow && keyboardVisible == true && isEnabled) {
            requestFocus()
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    private val inputMethodManager: InputMethodManager
        get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            isForceDarkAllowed = false
        }
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER_VERTICAL
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        maxLines = 3
        minHeight = (48 * resources.displayMetrics.density).roundToInt()
        hint = "输入弹幕内容"
        setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                publish()
                onSend(draft.text)
                true
            } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!applying) changingText = true
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (applying || s == null) return
                changingText = false
                publish()
            }
        })
        ready = true
    }

    fun bind(value: TextFieldValue, replyPrefix: String, resources: Map<String, LiveMessageEmote>) {
        if (released) return
        if (emotes != resources) {
            cancelImages()
            emotes = resources
        }
        draft = value
        prefix = replyPrefix
        applyDraft()
        decorate()
    }

    private fun applyDraft() {
        applying = true
        try {
            val displayed = prefix + draft.text
            if (text.toString() != displayed) {
                // 外部插入表情或清空时才替换文本；普通输入回传不重建 Editable。
                BaseInputConnection.removeComposingSpans(text)
                text.replace(0, text.length, displayed)
            }
            val start = (draft.selection.start + prefix.length).coerceIn(0, text.length)
            val end = (draft.selection.end + prefix.length).coerceIn(0, text.length)
            if (selectionStart != start || selectionEnd != end) setSelection(start, end)
        } finally { applying = false }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (ready && !applying && !changingText && imeEditDepth == 0 && selStart >= 0 && selEnd >= 0) publish()
    }

    // 等一次输入法操作完整提交文本、组合跨度和选区后再同步，避免在 TextWatcher 中途重写候选词。
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                imeEdit { super.commitText(text, newCursorPosition) }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
                imeEdit { super.setComposingText(text, newCursorPosition) }
            override fun finishComposingText(): Boolean = imeEdit { super.finishComposingText() }
            override fun setComposingRegion(start: Int, end: Int): Boolean =
                imeEdit { super.setComposingRegion(start, end) }
            override fun setSelection(start: Int, end: Int): Boolean = imeEdit { super.setSelection(start, end) }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
                imeEdit { super.deleteSurroundingText(beforeLength, afterLength) }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
                imeEdit { super.deleteSurroundingTextInCodePoints(beforeLength, afterLength) }

            @androidx.annotation.RequiresApi(33)
            override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean =
                imeEdit { super.commitText(text, newCursorPosition, textAttribute) }
            @androidx.annotation.RequiresApi(33)
            override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean =
                imeEdit { super.setComposingText(text, newCursorPosition, textAttribute) }
            @androidx.annotation.RequiresApi(33)
            override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean =
                imeEdit { super.setComposingRegion(start, end, textAttribute) }
            @androidx.annotation.RequiresApi(34)
            override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean =
                imeEdit { super.replaceText(start, end, text, newCursorPosition, textAttribute) }
        }
    }

    private fun imeEdit(action: () -> Boolean): Boolean {
        imeEditDepth++
        try { return action() }
        finally {
            imeEditDepth--
            if (imeEditDepth == 0) publish()
        }
    }

    private fun publish() {
        if (!ready || released || applying || imeEditDepth > 0 || !isEnabled) return
        val composingStart = BaseInputConnection.getComposingSpanStart(text)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(text)
        val edited = TextFieldValue(
            text.toString(),
            TextRange(selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0)),
            if (composingStart >= 0 && composingEnd > composingStart) TextRange(composingStart, composingEnd) else null,
        )
        val result = projectLiveRichDraftEdit(draft, edited, prefix, emotes.keys)
        val changed = result.value != draft
        draft = result.value
        if (result.clearReply) prefix = ""
        applyDraft()
        decorate()
        if (result.clearReply) onReplyRemoved()
        if (changed) onDraftChanged(draft)
    }

    private fun decorate() {
        applying = true
        try {
            spans.forEach(text::removeSpan)
            spans.clear()
            fun span(value: Any, start: Int, end: Int) {
                if (start < end && end <= text.length) {
                    text.setSpan(value, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spans.add(value)
                }
            }
            if (prefix.isNotEmpty()) {
                span(ForegroundColorSpan(replyColor), 0, prefix.length)
                span(BackgroundColorSpan(replyBackground), 0, prefix.length)
            }
            val used = mutableSetOf<String>()
            var offset = prefix.length
            liveDraftPreviewSegments(draft.text, emotes).forEach { (part, isEmote) ->
                val start = offset
                offset += part.length
                if (isEmote) {
                    used.add(part)
                    // 组合中的文本继续交给输入法排版，提交后再替换为图片跨度。
                    val composing = draft.composition
                    if (composing == null || start - prefix.length >= composing.max || offset - prefix.length <= composing.min) {
                        images[part]?.let { image ->
                            val ratio = (emotes.getValue(part).width.toFloat() / emotes.getValue(part).height.coerceAtLeast(1)).coerceIn(0.25f, 4f)
                            val height = (textSize * 1.35f).roundToInt().coerceAtLeast(1)
                            image.setBounds(0, 0, (height * ratio).roundToInt().coerceAtLeast(1), height)
                            span(ImageSpan(image, ImageSpan.ALIGN_BOTTOM), start, offset)
                        }
                    }
                }
            }
            (requests.keys - used).forEach { requests.remove(it)?.dispose() }
            images.keys.retainAll(used)
            used.forEach { marker ->
                if (marker !in requests && marker !in images) loadImage(marker, emotes.getValue(marker))
            }
        } finally { applying = false }
    }

    private fun loadImage(marker: String, resource: LiveMessageEmote) {
        val request = ImageRequest.Builder(context)
            .data(resource.url)
            .size((textSize * 6).roundToInt().coerceAtLeast(1))
            .allowHardware(false)
            .target { image ->
                if (!released && emotes[marker] == resource && draft.text.contains(marker)) {
                    // 编辑器使用静态帧，图片加载完成后只重绘跨度，不启动循环动画。
                    images[marker] = BitmapDrawable(resources, image.toBitmap())
                    post { if (!released) { decorate(); requestLayout(); invalidate() } }
                }
            }
            .build()
        requests[marker] = context.imageLoader.enqueue(request)
    }

    fun setKeyboardVisible(visible: Boolean) {
        if (keyboardVisible == visible) return
        keyboardVisible = visible
        showSoftInputOnFocus = visible
        removeCallbacks(keyboardRequest)
        if (visible) post(keyboardRequest)
        else if (hasFocus()) inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        released = false
        decorate()
        if (keyboardVisible == true) post(keyboardRequest)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && keyboardVisible == true) {
            removeCallbacks(keyboardRequest)
            post(keyboardRequest)
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun cancelImages() {
        requests.values.forEach { it.dispose() }
        requests.clear()
        images.clear()
    }

    fun release() {
        released = true
        removeCallbacks(keyboardRequest)
        if (hasFocus()) {
            inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
        }
        cancelImages()
    }
}
