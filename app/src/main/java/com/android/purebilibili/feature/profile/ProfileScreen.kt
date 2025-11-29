// 文件路径: feature/profile/ProfileScreen.kt
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
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    Scaffold(
        // 🔥 修复：背景色
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 🔥 修复：图标颜色
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                // 🔥 修复：TopBar 背景
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        onHistoryClick = onHistoryClick,
                        onFavoriteClick = onFavoriteClick
                    )
                }
            }
        }
    }
}

@Composable
fun GuestProfileContent(onGoToLogin: () -> Unit) {
    Column(
        // 🔥 修复：背景色
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                // 🔥 修复：占位圆颜色
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onGoToLogin() },
            contentAlignment = Alignment.Center
        ) {
            Text("登录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
        item { ServicesSection(onHistoryClick, onFavoriteClick) }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text("退出登录")
                }
            }
        }
    }
}

@Composable
fun UserInfoSection(user: UserState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 🔥 修复：背景色
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(user.face)).crossfade(true).placeholder(android.R.color.darker_gray).build(),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            // 🔥 修复：用户名颜色
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (user.isVip) BiliPink else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelTag(level = user.level)
                Spacer(modifier = Modifier.width(8.dp))
                if (user.isVip) {
                    Surface(color = BiliPink, shape = RoundedCornerShape(4.dp)) {
                        Text(user.vipLabel.ifEmpty { "大会员" }, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                        Text("正式会员", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 🔥 修复：背景色
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatItem(count = FormatUtils.formatStat(user.dynamic.toLong()), label = "动态")
        StatItem(count = FormatUtils.formatStat(user.following.toLong()), label = "关注")
        StatItem(count = FormatUtils.formatStat(user.follower.toLong()), label = "粉丝")
    }
}

@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 🔥 修复：数字和标签颜色
        Text(text = count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VipBannerSection(user: UserState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            // 🔥 保持 VIP 金色，因为这是品牌色，不需要随深色模式变黑
            .background(Brush.horizontalGradient(colors = listOf(Color(0xFFFFEECC), Color(0xFFFFCC99))))
    ) {
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
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            // 🔥 修复：卡片背景
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 🔥 修复：标题颜色
        Text(
            "更多服务",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        )

        ServiceItem(Icons.Default.Download, "离线缓存", BiliPink) { /* TODO */ }
        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))

        ServiceItem(Icons.Default.History, "历史记录", Color(0xFF2196F3), onClick = onHistoryClick)
        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))

        ServiceItem(Icons.Default.FavoriteBorder, "我的收藏", Color(0xFFFFC107), onClick = onFavoriteClick)
        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))

        ServiceItem(Icons.Default.Schedule, "稍后再看", Color(0xFF4CAF50)) { /* TODO */ }
    }
}

@Composable
fun ServiceItem(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        // 🔥 修复：文字颜色
        Text(text = title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        // 🔥 修复：箭头颜色
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}