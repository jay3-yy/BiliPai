package com.android.purebilibili.core.lifecycle

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun backgroundStateFlow(): Flow<Boolean> = callbackFlow {
    val listener = object : BackgroundManager.BackgroundStateListener {
        override fun onEnterBackground() { trySend(true) }
        override fun onEnterForeground() { trySend(false) }
    }
    BackgroundManager.addListener(listener)
    trySend(BackgroundManager.isInBackground)
    awaitClose { BackgroundManager.removeListener(listener) }
}.distinctUntilChanged()
