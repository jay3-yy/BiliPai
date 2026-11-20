package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val routeListSerializer = ListSerializer(BiliPaiNavKey.serializer())
private val routeStateJson = Json { ignoreUnknownKeys = true }

// Keep saved Activity state compatible with the existing route list during the host migration.
internal val BiliPaiNavBackStackSaver = Saver<NavBackStack<BiliPaiNavKey>, String>(
    save = { stack -> routeStateJson.encodeToString(routeListSerializer, stack) },
    restore = { encoded ->
        NavBackStack(routeStateJson.decodeFromString(routeListSerializer, encoded).toMutableStateList())
    },
)

@Composable
internal fun rememberBiliPaiNavBackStack(initialRoutes: List<BiliPaiNavKey>): NavBackStack<BiliPaiNavKey> =
    rememberSaveable(saver = BiliPaiNavBackStackSaver) {
        NavBackStack(initialRoutes.toMutableStateList())
    }
