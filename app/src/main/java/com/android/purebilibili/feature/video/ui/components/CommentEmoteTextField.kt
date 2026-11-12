package com.android.purebilibili.feature.video.ui.components

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.target

/** Image spans replace only the drawing: the editable buffer, clipboard and draft retain [doge]. */
@Composable
internal fun CommentEmoteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    emoteUrls: Map<String, String>,
    enabled: Boolean,
    readOnly: Boolean,
    autoShowKeyboard: Boolean = false,
    hint: String,
    textStyle: TextStyle,
    hintColor: Color,
    cursorColor: Color,
    onBeginEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { textStyle.fontSize.toPx() }
    val emoteSizePx = with(density) { 22.dp.roundToPx() }
    val emotePaddingPx = with(density) { 2.dp.roundToPx() }
    val cursorWidthPx = with(density) { 2.dp.roundToPx() }
    val typeface = LocalFontFamilyResolver.current.resolve(
        fontFamily = textStyle.fontFamily,
        fontWeight = textStyle.fontWeight ?: FontWeight.Normal,
        fontStyle = textStyle.fontStyle ?: FontStyle.Normal,
        fontSynthesis = textStyle.fontSynthesis ?: FontSynthesis.All,
    ).value as Typeface
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnBeginEditing by rememberUpdatedState(onBeginEditing)
    var editor by remember { mutableStateOf<CommentEmoteEditText?>(null) }
    var editorFocused by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.semantics {
            editableText = AnnotatedString(value.text)
            textSelectionRange = value.selection
            focused = editorFocused
            if (!enabled) disabled()
            requestFocus {
                if (!enabled || readOnly) false else editor?.requestFocus() == true
            }
            setText { replacement ->
                val view = editor
                if (view == null || !enabled || readOnly) false else {
                    view.replaceAll(replacement.text)
                    true
                }
            }
            insertTextAtCursor { replacement ->
                val view = editor
                if (view == null || !enabled || readOnly) false else {
                    view.replaceSelection(replacement.text)
                    true
                }
            }
            setSelection { start, end, _ ->
                val view = editor
                if (view == null || start !in 0..value.text.length || end !in 0..value.text.length) {
                    false
                } else {
                    view.setSelection(start, end)
                    true
                }
            }
        },
        factory = { context ->
            CommentEmoteEditText(context, emoteSizePx, emotePaddingPx).also { view ->
                editor = view
                view.onValueChange = { currentOnValueChange(it) }
                view.onEditingRequested = { currentOnBeginEditing() }
                view.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus ->
                    editorFocused = hasFocus
                }
            }
        },
        update = { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            view.typeface = typeface
            view.setTextColor(textStyle.color.toArgb())
            view.setHintTextColor(hintColor.toArgb())
            view.highlightColor = cursorColor.copy(alpha = 0.3f).toArgb()
            view.hint = hint
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val color = cursorColor.toArgb()
                if (view.cursorColor != color) {
                    view.textCursorDrawable = GradientDrawable().apply {
                        setColor(color)
                        setSize(cursorWidthPx, textSizePx.toInt())
                    }
                    view.cursorColor = color
                }
            }
            view.bind(value, emoteUrls, enabled, readOnly)
            view.setAutoShowKeyboard(autoShowKeyboard)
        },
        onReset = null,
        onRelease = { it.release() },
    )
}

private class CommentEmoteEditText(
    context: Context,
    private val emoteSizePx: Int,
    private val emotePaddingPx: Int,
) : EditText(context) {
    var onValueChange: ((TextFieldValue) -> Unit)? = null
    var onEditingRequested: (() -> Unit)? = null
    var cursorColor: Int? = null
    private var emoteUrls: Map<String, String> = emptyMap()
    private val images = mutableMapOf<String, Drawable>()
    private val requests = mutableMapOf<String, Disposable>()
    private val visibleImageUrls = mutableSetOf<String>()
    private var renderedComposition: TextRange? = null
    private var syncing = false
    private var changingText = false
    private var initialized = false
    private var panelReadOnly = false
    private var previousCaret = 0
    private var autoShowKeyboard = false
    private var keyboardShowPending = false
    private val showKeyboard = Runnable {
        if (keyboardShowPending && isAttachedToWindow && hasWindowFocus() && isEnabled && !panelReadOnly) {
            if (requestFocus()) {
                context.getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)
                keyboardShowPending = false
            }
        }
    }

    fun setAutoShowKeyboard(show: Boolean) {
        if (autoShowKeyboard == show) return
        autoShowKeyboard = show
        keyboardShowPending = show
        removeCallbacks(showKeyboard)
        if (show) scheduleKeyboardShow()
    }

    private fun scheduleKeyboardShow() {
        if (!keyboardShowPending || !isAttachedToWindow || !hasWindowFocus()) return
        // 对实际 EditText 请求焦点；等窗口焦点分发完成后再建立输入法连接。
        requestFocus()
        removeCallbacks(showKeyboard)
        post(showKeyboard)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) scheduleKeyboardShow()
    }

    init {
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setHorizontallyScrolling(false)
        filters = arrayOf(InputFilter.LengthFilter(1000))
        isFocusableInTouchMode = true
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                changingText = true
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                changingText = false
                if (!syncing) {
                    renderEmotes()
                    publishValue()
                }
            }
        })
        initialized = true
    }

    fun bind(value: TextFieldValue, urls: Map<String, String>, enabled: Boolean, readOnly: Boolean) {
        val catalogChanged = emoteUrls !== urls
        emoteUrls = urls
        isEnabled = enabled
        panelReadOnly = readOnly
        showSoftInputOnFocus = enabled && !readOnly
        isCursorVisible = enabled && !readOnly
        if (readOnly && hasFocus()) {
            clearFocus()
            context.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(windowToken, 0)
        }
        val textChanged = text.toString() != value.text
        syncing = true
        try {
            if (textChanged) setText(value.text)
            if (selectionStart != value.selection.start || selectionEnd != value.selection.end) {
                setSelection(value.selection.start, value.selection.end)
            }
        } finally {
            syncing = false
        }
        if (textChanged || catalogChanged || renderedComposition != value.composition) renderEmotes()
    }

    fun replaceAll(replacement: String) {
        editableText.replace(0, length(), replacement)
        setSelection(length())
    }

    fun replaceSelection(replacement: String) {
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        editableText.replace(start, end, replacement)
    }

    private fun publishValue() {
        if (!initialized || syncing || changingText) return
        val editable = editableText
        val composingStart = BaseInputConnection.getComposingSpanStart(editable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(editable)
        onValueChange?.invoke(
            TextFieldValue(
                text = editable.toString(),
                selection = TextRange(selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0)),
                composition = if (composingStart >= 0 && composingEnd > composingStart) {
                    TextRange(composingStart, composingEnd)
                } else null,
            )
        )
    }

    private fun renderEmotes() {
        if (!initialized) return
        val editable = editableText
        visibleImageUrls.clear()
        val composingStart = BaseInputConnection.getComposingSpanStart(editable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(editable)
        renderedComposition = if (composingStart >= 0 && composingEnd > composingStart) {
            TextRange(composingStart, composingEnd)
        } else null
        editable.getSpans(0, editable.length, CommentEmoteSpan::class.java).forEach(editable::removeSpan)
        EMOTE_TOKEN_PATTERN.findAll(editable).forEach { match ->
            val url = emoteUrls[match.value]?.takeIf { it.isNotBlank() } ?: return@forEach
            if (composingStart < match.range.last + 1 && composingEnd > match.range.first) return@forEach
            val drawable = images[url]
            if (drawable != null) {
                visibleImageUrls.add(url)
                editable.setSpan(
                    CommentEmoteSpan(drawable, emotePaddingPx),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else if (!requests.containsKey(url)) {
                requests[url] = context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(
                            when {
                                url.startsWith("//") -> "https:$url"
                                url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
                                else -> url
                            }
                        )
                        .size(emoteSizePx)
                        .target(onSuccess = success@{ image ->
                            if (!initialized) return@success
                            images[url] = image.asDrawable(resources).apply {
                                setBounds(0, 0, emoteSizePx, emoteSizePx)
                                callback = this@CommentEmoteEditText
                            }
                            renderEmotes()
                        })
                        .build()
                )
            }
        }
        updateAnimationState()
        val start = selectionStart
        val end = selectionEnd
        if (start >= 0 && end >= 0) {
            val adjustedStart = emoteBoundary(start, if (start == end) start >= previousCaret else start > end)
            val adjustedEnd = if (start == end) adjustedStart else emoteBoundary(end, end > start)
            if (start != adjustedStart || end != adjustedEnd) setSelection(adjustedStart, adjustedEnd)
        }
    }

    private fun emoteBoundary(offset: Int, towardEnd: Boolean): Int {
        val editable = editableText
        val span = editable.getSpans(offset, offset, CommentEmoteSpan::class.java).firstOrNull {
            offset > editable.getSpanStart(it) && offset < editable.getSpanEnd(it)
        } ?: return offset
        return if (towardEnd) editable.getSpanEnd(span) else editable.getSpanStart(span)
    }

    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        if (!initialized || syncing || changingText || start < 0 || end < 0) return
        val adjustedStart = emoteBoundary(start, if (start == end) start >= previousCaret else start > end)
        val adjustedEnd = if (start == end) adjustedStart else emoteBoundary(end, end > start)
        if (start != adjustedStart || end != adjustedEnd) {
            setSelection(adjustedStart, adjustedEnd)
            return
        }
        previousCaret = end
        publishValue()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isEnabled && panelReadOnly) {
            panelReadOnly = false
            showSoftInputOnFocus = true
            isCursorVisible = true
            onEditingRequested?.invoke()
        }
        return super.onTouchEvent(event)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        when (id) {
            android.R.id.copy, android.R.id.cut -> {
                val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
                val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
                clipboard.setPrimaryClip(ClipData.newPlainText("评论", editableText.subSequence(start, end).toString()))
                if (id == android.R.id.cut && !panelReadOnly) editableText.delete(start, end)
                return true
            }
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                if (panelReadOnly) return false
                val clip = clipboard.primaryClip ?: return false
                if (clip.itemCount == 0) return false
                replaceSelection(clip.getItemAt(0).coerceToText(context).toString())
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }

    private fun deleteEmote(backward: Boolean): Boolean {
        if (panelReadOnly || selectionStart != selectionEnd || selectionStart < 0) return false
        val caret = selectionStart
        val editable = editableText
        val span = editable.getSpans(caret, caret, CommentEmoteSpan::class.java).firstOrNull {
            if (backward) editable.getSpanEnd(it) == caret else editable.getSpanStart(it) == caret
        } ?: return false
        editable.delete(editable.getSpanStart(span), editable.getSpanEnd(span))
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (panelReadOnly && keyCode != KeyEvent.KEYCODE_BACK) return false
        if (keyCode == KeyEvent.KEYCODE_DEL && deleteEmote(backward = true)) return true
        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL && deleteEmote(backward = false)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (panelReadOnly) return null
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (panelReadOnly) return false
                val result = super.commitText(text, newCursorPosition)
                renderEmotes()
                publishValue()
                return result
            }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (panelReadOnly) return false
                val result = super.setComposingText(text, newCursorPosition)
                renderEmotes()
                publishValue()
                return result
            }
            override fun finishComposingText(): Boolean {
                val result = super.finishComposingText()
                renderEmotes()
                publishValue()
                return result
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (panelReadOnly) return false
                if (beforeLength == 1 && afterLength == 0 && deleteEmote(true)) return true
                if (beforeLength == 0 && afterLength == 1 && deleteEmote(false)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (panelReadOnly) return false
                if (beforeLength == 1 && afterLength == 0 && deleteEmote(true)) return true
                if (beforeLength == 0 && afterLength == 1 && deleteEmote(false)) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        (initialized && images.values.any { it === who }) || super.verifyDrawable(who)

    private fun updateAnimationState() {
        images.forEach { (url, image) ->
            val animation = image as? Animatable ?: return@forEach
            if (isAttachedToWindow && url in visibleImageUrls) {
                if (!animation.isRunning) animation.start()
            } else if (animation.isRunning) {
                animation.stop()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
        scheduleKeyboardShow()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(showKeyboard)
        images.values.forEach { (it as? Animatable)?.stop() }
        super.onDetachedFromWindow()
    }

    fun release() {
        keyboardShowPending = false
        removeCallbacks(showKeyboard)
        initialized = false
        requests.values.forEach { it.dispose() }
        images.values.forEach {
            (it as? Animatable)?.stop()
            it.callback = null
        }
        requests.clear()
        images.clear()
        visibleImageUrls.clear()
        onValueChange = null
        onEditingRequested = null
        onFocusChangeListener = null
    }
}

private class CommentEmoteSpan(drawable: Drawable, private val padding: Int) : ImageSpan(drawable) {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            val center = (metrics.ascent + metrics.descent) / 2
            fm.ascent = minOf(metrics.ascent, center - drawable.bounds.height() / 2)
            fm.descent = maxOf(metrics.descent, center + drawable.bounds.height() / 2)
            fm.top = minOf(metrics.top, fm.ascent)
            fm.bottom = maxOf(metrics.bottom, fm.descent)
        }
        return drawable.bounds.width() + padding * 2
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val metrics = paint.fontMetricsInt
        val center = y + (metrics.ascent + metrics.descent) / 2
        val saved = canvas.save()
        canvas.translate(x + padding, (center - drawable.bounds.height() / 2).toFloat())
        drawable.draw(canvas)
        canvas.restoreToCount(saved)
    }
}
