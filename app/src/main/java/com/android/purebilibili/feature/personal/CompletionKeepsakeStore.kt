package com.android.purebilibili.feature.personal

import android.content.Context
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.widget.KeepsakeMoodWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 本地完结纪念短评存储，严格按账号 mid 隔离，不上传云端。
 */
object CompletionKeepsakeStore {
    private const val PREFS_NAME = "completion_keepsakes"
    private const val MAX_RECORDS_PER_ACCOUNT = 120
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun accountKey(accountMid: Long? = TokenManager.midCache): String =
        "records_${accountMid ?: 0L}"

    private fun dismissedKey(accountMid: Long? = TokenManager.midCache): String =
        "dismissed_${accountMid ?: 0L}"

    suspend fun loadRecords(
        context: Context,
        accountMid: Long? = TokenManager.midCache,
    ): List<CompletionKeepsakeRecord> = withContext(Dispatchers.IO) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(accountKey(accountMid), null)
            ?: return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(CompletionKeepsakeRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun shouldPrompt(
        context: Context,
        keepsakeId: String,
        accountMid: Long? = TokenManager.midCache,
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getStringSet(dismissedKey(accountMid), emptySet()).orEmpty()
        if (keepsakeId in dismissed) return@withContext false
        val records = loadRecords(context, accountMid)
        records.none { it.id == keepsakeId }
    }

    suspend fun markDismissed(
        context: Context,
        keepsakeId: String,
        accountMid: Long? = TokenManager.midCache,
    ) = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(dismissedKey(accountMid), emptySet()).orEmpty().toMutableSet()
        updated += keepsakeId
        prefs.edit().putStringSet(dismissedKey(accountMid), updated).apply()
    }

    suspend fun saveRecord(
        context: Context,
        record: CompletionKeepsakeRecord,
        accountMid: Long? = TokenManager.midCache,
    ) = withContext(Dispatchers.IO) {
        val existing = loadRecords(context, accountMid)
            .filterNot { it.id == record.id }
        val updated = (listOf(record) + existing).take(MAX_RECORDS_PER_ACCOUNT)
        val encoded = json.encodeToString(ListSerializer(CompletionKeepsakeRecord.serializer()), updated)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getStringSet(dismissedKey(accountMid), emptySet()).orEmpty().toMutableSet()
        dismissed += record.id
        prefs.edit()
            .putString(accountKey(accountMid), encoded)
            .putStringSet(dismissedKey(accountMid), dismissed)
            .apply()
        KeepsakeMoodWidgetProvider.requestUpdate(context)
    }

    suspend fun deleteRecord(
        context: Context,
        keepsakeId: String,
        accountMid: Long? = TokenManager.midCache,
    ) = withContext(Dispatchers.IO) {
        val updated = loadRecords(context, accountMid).filterNot { it.id == keepsakeId }
        val encoded = json.encodeToString(ListSerializer(CompletionKeepsakeRecord.serializer()), updated)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(accountKey(accountMid), encoded)
            .apply()
        KeepsakeMoodWidgetProvider.requestUpdate(context)
    }
}
