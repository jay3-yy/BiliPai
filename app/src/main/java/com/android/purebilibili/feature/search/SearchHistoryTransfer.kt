package com.android.purebilibili.feature.search

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.common.copyPlainTextToClipboard
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

internal fun encodeSearchHistory(keywords: List<String>): String =
    Json.encodeToString(keywords)

internal fun decodeSearchHistory(text: String): List<String> {
    val array = Json.parseToJsonElement(text) as? JsonArray
        ?: throw IllegalArgumentException("Expected a JSON array")
    return array.map { value ->
        require(value is JsonPrimitive && value.isString && value.content.isNotBlank())
        value.content
    }.distinct()
}

@Composable
internal fun rememberSearchHistoryTransfer(viewModel: SearchViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf<String?>(null) }
    var inputInvalid by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun notify(message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun runTransfer(failureMessage: Int, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("SearchHistoryTransfer", "Search history operation failed", e)
                notify(failureMessage)
            } finally {
                busy = false
            }
        }
    }

    suspend fun prepareImport(text: String): Boolean {
        val keywords = withContext(Dispatchers.Default) {
            try {
                decodeSearchHistory(text.removePrefix("\uFEFF"))
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return false
        pendingImport = keywords
        return true
    }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runTransfer(R.string.search_history_export_failed) {
            val json = viewModel.exportHistory()
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to open destination")
                output.bufferedWriter().use { it.write(json) }
            }
            notify(R.string.search_history_exported)
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTransfer(R.string.search_history_import_failed) {
            val text = withContext(Dispatchers.IO) {
                val source = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open source")
                source.bufferedReader().use { it.readText() }
            }
            if (!prepareImport(text)) notify(R.string.search_history_invalid)
        }
    }

    if (showMenu) {
        AppAlertDialog(
            onDismissRequest = { showMenu = false },
            title = { AppText(stringResource(R.string.search_history_transfer)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SearchHistoryTransferOption(R.string.search_history_copy) {
                        showMenu = false
                        runTransfer(R.string.search_history_export_failed) {
                            copyPlainTextToClipboard(context, viewModel.exportHistory())
                            notify(R.string.search_history_copied)
                        }
                    }
                    SearchHistoryTransferOption(R.string.search_history_save) {
                        showMenu = false
                        createFile.launch("bilipai_search_${System.currentTimeMillis()}.json")
                    }
                    AppHorizontalDivider()
                    SearchHistoryTransferOption(R.string.search_history_input) {
                        showMenu = false
                        inputInvalid = false
                        input = ""
                    }
                    SearchHistoryTransferOption(R.string.search_history_paste) {
                        showMenu = false
                        runTransfer(R.string.search_history_import_failed) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            when {
                                text.isNullOrBlank() -> notify(R.string.search_history_clipboard_empty)
                                !prepareImport(text) -> notify(R.string.search_history_invalid)
                                else -> Unit
                            }
                        }
                    }
                    SearchHistoryTransferOption(R.string.search_history_open) {
                        showMenu = false
                        openFile.launch(arrayOf("application/json", "text/plain"))
                    }
                }
            },
        )
    }

    input?.let { draft ->
        AppAlertDialog(
            onDismissRequest = { if (!busy) input = null },
            title = { AppText(stringResource(R.string.search_history_input_title)) },
            text = {
                AppOutlinedTextField(
                    value = draft,
                    onValueChange = {
                        input = it
                        inputInvalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    labelText = stringResource(R.string.search_history_json),
                    minLines = 3,
                    maxLines = 6,
                    isError = inputInvalid,
                    supportingText = if (inputInvalid) {
                        { AppText(stringResource(R.string.search_history_invalid)) }
                    } else null,
                )
            },
            confirmButton = {
                AppDialogAction(onClick = {
                    runTransfer(R.string.search_history_import_failed) {
                        if (prepareImport(draft)) input = null else inputInvalid = true
                    }
                }) { AppText(stringResource(R.string.search_history_preview)) }
            },
            dismissButton = {
                AppDialogAction(onClick = { if (!busy) input = null }) {
                    AppText(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    pendingImport?.let { keywords ->
        AppAlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { AppText(stringResource(R.string.search_history_import_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppText(stringResource(R.string.search_history_replace, keywords.size))
                    keywords.take(8).forEach { keyword ->
                        AppText(
                            text = keyword,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (keywords.size > 8) AppText("…")
                }
            },
            confirmButton = {
                AppDialogAction(onClick = {
                    runTransfer(R.string.search_history_import_failed) {
                        viewModel.replaceHistory(keywords)
                        pendingImport = null
                        notify(R.string.search_history_imported)
                    }
                }) { AppText(stringResource(R.string.search_history_import_confirm)) }
            },
            dismissButton = {
                AppDialogAction(onClick = { if (!busy) pendingImport = null }) {
                    AppText(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    return { if (!busy) showMenu = true }
}

@Composable
private fun SearchHistoryTransferOption(
    label: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppText(
        text = stringResource(label),
        modifier = modifier
            .fillMaxWidth()
            .appActionClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}
