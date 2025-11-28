package com.android.purebilibili.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // 第一次进入加载二维码
    LaunchedEffect(Unit) {
        viewModel.loadQrCode()
    }

    // 退出页面时停止轮询
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPolling() }
    }

    // 监听成功
    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 关闭按钮
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("扫码登录 Bilibili", style = MaterialTheme.typography.headlineMedium, color = BiliPink)
                Spacer(modifier = Modifier.height(32.dp))

                when (val s = state) {
                    is LoginState.Loading -> CircularProgressIndicator(color = BiliPink)
                    is LoginState.Error -> {
                        Text("错误: ${s.msg}", color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadQrCode() }) { Text("刷新二维码") }
                    }
                    is LoginState.QrCode -> {
                        Image(
                            bitmap = s.bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(240.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("请打开 Bilibili 手机端", style = MaterialTheme.typography.bodyLarge)
                        Text("点击首页右上角扫一扫", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    is LoginState.Success -> {
                        CircularProgressIndicator(color = BiliPink)
                        Text("登录成功，正在跳转...", modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}