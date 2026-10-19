package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.InputStream
import java.io.OutputStream
import okio.buffer
import okio.sink
import okio.source

/** One instance per file in each process; all readers of these files use this factory. */
object MultiProcessPreferences {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    // DataStore 1.2 exposes an Okio PreferencesSerializer; the multi-process file factory
    // accepts a stream Serializer and installs the Android cross-process coordinator.
    private val serializer = object : Serializer<Preferences> {
        override val defaultValue: Preferences = PreferencesSerializer.defaultValue
        override suspend fun readFrom(input: InputStream): Preferences =
            PreferencesSerializer.readFrom(input.source().buffer())
        override suspend fun writeTo(t: Preferences, output: OutputStream) {
            val sink = output.sink().buffer()
            PreferencesSerializer.writeTo(t, sink)
            sink.flush()
        }
    }

    @Synchronized
    fun get(
        context: Context,
        name: String,
        migrations: List<DataMigration<Preferences>> = emptyList()
    ): DataStore<Preferences> {
        val file = context.applicationContext.preferencesDataStoreFile(name)
        return stores.getOrPut(file.absolutePath) {
            val delegate = MultiProcessDataStoreFactory.create(
                serializer = serializer,
                migrations = migrations,
                produceFile = { file }
            )
            // Preferences.edit returns MutablePreferences, so freeze before caching or sharing.
            object : DataStore<Preferences> {
                override val data = delegate.data
                override suspend fun updateData(
                    transform: suspend (t: Preferences) -> Preferences
                ): Preferences = delegate.updateData { transform(it).toPreferences() }
            }
        }
    }
}
