package com.android.bilipai.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

private const val WEAR_PATH_COMMAND = "/bilipai/wear/command"
private const val WEAR_PATH_STATE = "/bilipai/wear/state"

private data class WearUiState(
    val title: String = "等待连接手机 BiliPai",
    val author: String = "",
    val playing: Boolean = false,
    val speed: Float = 1.0f,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val locked: Boolean = false,
)

class WearRemoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearRemoteScreen(
                    sendCommand = ::sendCommandToPhone,
                    registerListener = { listener ->
                        Wearable.getMessageClient(this).addListener(listener)
                    },
                    unregisterListener = { listener ->
                        Wearable.getMessageClient(this).removeListener(listener)
                    },
                )
            }
        }
    }

    private fun sendCommandToPhone(command: String) {
        val payload = command.toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val client = Wearable.getMessageClient(this)
            nodes.forEach { node ->
                client.sendMessage(node.id, WEAR_PATH_COMMAND, payload)
            }
        }
    }
}

@Composable
private fun WearRemoteScreen(
    sendCommand: (String) -> Unit,
    registerListener: (MessageClient.OnMessageReceivedListener) -> Unit,
    unregisterListener: (MessageClient.OnMessageReceivedListener) -> Unit,
) {
    var uiState by remember { mutableStateOf(WearUiState()) }

    DisposableEffect(Unit) {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != WEAR_PATH_STATE) return@OnMessageReceivedListener
            runCatching {
                val obj = JSONObject(event.data.toString(Charsets.UTF_8))
                uiState = WearUiState(
                    title = obj.optString("title", "").ifBlank {
                        if (obj.optBoolean("locked", false)) "手机已开启隐私锁" else "暂无播放内容"
                    },
                    author = obj.optString("author", ""),
                    playing = obj.optBoolean("playing", false),
                    speed = obj.optDouble("speed", 1.0).toFloat(),
                    canPrevious = obj.optBoolean("canPrevious", false),
                    canNext = obj.optBoolean("canNext", false),
                    locked = obj.optBoolean("locked", false),
                )
            }
        }
        registerListener(listener)
        onDispose { unregisterListener(listener) }
    }

    LaunchedEffect(Unit) {
        sendCommand("request_state")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.author.isNotBlank()) {
            Text(
                text = uiState.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { sendCommand("seek_backward_15s") }) {
                Text("-15s")
            }
            Button(onClick = { sendCommand("toggle_play") }) {
                Text(if (uiState.playing) "暂停" else "播放")
            }
            FilledTonalButton(onClick = { sendCommand("seek_forward_15s") }) {
                Text("+15s")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { sendCommand("previous_item") },
                enabled = uiState.canPrevious,
            ) { Text("上一集") }
            FilledTonalButton(onClick = { sendCommand("cycle_speed") }) {
                Text("${uiState.speed}x")
            }
            FilledTonalButton(
                onClick = { sendCommand("next_item") },
                enabled = uiState.canNext,
            ) { Text("下一集") }
        }
    }
}
