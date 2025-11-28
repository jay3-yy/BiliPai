package com.android.purebilibili.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.UserState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onSettingsClick: () -> Unit,
    // 🔥 1. 新增回调参数
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    Scaffold(
        containerColor = Color(0xFFF1F2F3),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is ProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BiliPink)
                    }
                }
                is ProfileUiState.LoggedOut -> {
                    GuestProfileContent(onGoToLogin = onGoToLogin)
                }
                is ProfileUiState.Success -> {
                    UserProfileContent(
                        user = s.user,
                        onLogout = {
                            viewModel.logout()
                            onLogoutSuccess()
                        },
                        // 🔥 2. 传递回调
                        onHistoryClick = onHistoryClick,
                        onFavoriteClick = onFavoriteClick
                    )
                }
            }
        }
    }
}

// ... GuestProfileContent 保持不变 ...
@Composable
fun GuestProfileContent(onGoToLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFE0E0E0)).clickable { onGoToLogin() },
            contentAlignment = Alignment.Center
        ) {
            Text("登录", color = Color.Gray, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGoToLogin, colors = ButtonDefaults.buttonColors(containerColor = BiliPink)) {
            Text("点击登录 Bilibili", color = Color.White)
        }
    }
}

@Composable
fun UserProfileContent(
    user: UserState,
    onLogout: () -> Unit,
    // 🔥 3. 接收回调
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { UserInfoSection(user) }
        item { UserStatsSection(user) }
        item { VipBannerSection(user) }
        // 🔥 4. 传递给 ServicesSection
        item { ServicesSection(onHistoryClick, onFavoriteClick) }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) {
                    Text("退出登录")
                }
            }
        }
    }
}

// ... UserInfoSection, LevelTag, UserStatsSection, StatItem, VipBannerSection 保持不变 ...
// (请保留原来的代码，这里省略以节省篇幅，重点是下面的 ServicesSection)
@Composable
fun UserInfoSection(user: UserState) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(user.face)).crossfade(true).placeholder(android.R.color.darker_gray).build(), contentDescription = null, modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFE0E0E0)), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (user.isVip) BiliPink else Color.Black)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelTag(level = user.level)
                Spacer(modifier = Modifier.width(8.dp))
                if (user.isVip) {
                    Surface(color = BiliPink, shape = RoundedCornerShape(4.dp)) { Text(user.vipLabel.ifEmpty { "大会员" }, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                } else {
                    Surface(color = Color(0xFFF1F2F3), shape = RoundedCornerShape(4.dp)) { Text("正式会员", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                }
            }
        }
    }
}

@Composable
fun LevelTag(level: Int) {
    Surface(color = if (level >= 5) Color(0xFFFF9800) else Color(0xFF9E9E9E), shape = RoundedCornerShape(2.dp)) {
        Text("LV$level", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

@Composable
fun UserStatsSection(user: UserState) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
        StatItem(count = FormatUtils.formatStat(user.dynamic.toLong()), label = "动态")
        StatItem(count = FormatUtils.formatStat(user.following.toLong()), label = "关注")
        StatItem(count = FormatUtils.formatStat(user.follower.toLong()), label = "粉丝")
    }
}

@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun VipBannerSection(user: UserState) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(60.dp).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(colors = listOf(Color(0xFFFFEECC), Color(0xFFFFCC99))))) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (user.isVip) "尊贵的大会员" else "成为大会员", color = Color(0xFF8B5A2B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("硬币: ${user.coin}   B币: ${user.bcoin}", color = Color(0xFF8B5A2B).copy(alpha = 0.8f), fontSize = 11.sp)
            }
            Text(if (user.isVip) "续费 >" else "开通 >", color = Color(0xFF8B5A2B), fontSize = 12.sp)
        }
    }
}

@Composable
fun ServicesSection(
    // 🔥 5. 接收回调
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        Text(
            "更多服务",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        ServiceItem(Icons.Default.Download, "离线缓存", BiliPink) { /* TODO */ }
        Divider(thickness = 0.5.dp, color = Color(0xFFF1F2F3), modifier = Modifier.padding(start = 56.dp))

        // 🔥 6. 绑定点击事件
        ServiceItem(Icons.Default.History, "历史记录", Color(0xFF2196F3), onClick = onHistoryClick)
        Divider(thickness = 0.5.dp, color = Color(0xFFF1F2F3), modifier = Modifier.padding(start = 56.dp))

        ServiceItem(Icons.Default.FavoriteBorder, "我的收藏", Color(0xFFFFC107), onClick = onFavoriteClick)
        Divider(thickness = 0.5.dp, color = Color(0xFFF1F2F3), modifier = Modifier.padding(start = 56.dp))

        ServiceItem(Icons.Default.Schedule, "稍后再看", Color(0xFF4CAF50)) { /* TODO */ }
    }
}

@Composable
fun ServiceItem(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    onClick: () -> Unit // 🔥 7. 增加 onClick 参数，默认为空在调用处处理
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() } // 🔥 8. 触发点击
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, fontSize = 14.sp, color = Color.Black, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
    }
}