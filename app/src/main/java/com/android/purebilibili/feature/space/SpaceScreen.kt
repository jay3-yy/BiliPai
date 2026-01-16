package com.android.purebilibili.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
//  已改用 MaterialTheme.colorScheme.primary
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.*
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import com.android.purebilibili.core.util.iOSTapEffect
import com.android.purebilibili.core.util.responsiveContentWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    mid: Long,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: SpaceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(mid) {
        viewModel.loadSpaceInfo(mid)
        //  [埋点] 页面浏览追踪
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("SpaceScreen")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("空间", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())  //  只应用顶部 padding，底部沉浸
        ) {
            when (val state = uiState) {
                is SpaceUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CupertinoActivityIndicator()
                    }
                }
                
                is SpaceUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😢", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadSpaceInfo(mid) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                
                is SpaceUiState.Success -> {
                    SpaceContent(
                        state = state,
                        onVideoClick = onVideoClick,
                        onLoadMore = { viewModel.loadMoreVideos() },
                        onCategoryClick = { viewModel.selectCategory(it) },
                        onSortOrderClick = { viewModel.selectSortOrder(it) }  //  排序点击
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceContent(
    state: SpaceUiState.Success,
    onVideoClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onCategoryClick: (Int) -> Unit,  //  分类点击回调
    onSortOrderClick: (VideoSortOrder) -> Unit  //  排序点击回调
) {
    //  当前选中的 Tab（目前只实现投稿页）
    var selectedTab by remember { mutableIntStateOf(2) }  // 默认投稿
    
    val listState = rememberLazyGridState()
    
    //  自动加载更多：当滚动接近底部时触发
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 6 && !state.isLoadingMore && state.hasMoreVideos && selectedTab == 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = listState,
        modifier = Modifier.fillMaxSize().responsiveContentWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 用户头部信息 (跨满列)
        item(span = { GridItemSpan(maxLineSpan) }) {
            SpaceHeader(
                userInfo = state.userInfo,
                relationStat = state.relationStat,
                upStat = state.upStat
            )
        }
        
        //  Tab 导航栏 (跨满列)
        item(span = { GridItemSpan(maxLineSpan) }) {
            SpaceTabRow(
                selectedTab = selectedTab,
                videoCount = state.totalVideos,
                collectionsCount = state.seasons.size + state.series.size,
                onTabSelected = { selectedTab = it }
            )
        }
        
        //  根据 Tab 显示不同内容
        when (selectedTab) {
            2 -> {  // 投稿
                // 投稿视频标题和排序按钮 (跨满列)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "视频",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val displayCount = if (state.totalVideos > 0) state.totalVideos else state.videos.size
                        Text(
                            text = " · $displayCount",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        Spacer(Modifier.weight(1f))
                        
                        SortButtonRow(
                            currentOrder = state.sortOrder,
                            onOrderClick = onSortOrderClick
                        )
                    }
                }
                
                // 视频列表 (网格)
                items(state.videos, key = { it.bvid }) { video ->
                    // Map SpaceVideoItem to VideoItem
                    val videoItem = remember(video) {
                        VideoItem(
                            bvid = video.bvid,
                            title = video.title,
                            pic = video.pic,
                            owner = Owner(mid = state.userInfo.mid, name = state.userInfo.name, face = state.userInfo.face),
                            stat = Stat(view = video.play, danmaku = 0, reply = video.comment, favorite = 0, coin = 0, share = 0, like = 0),
                            pubdate = video.created,
                            duration = try {
                                val parts = video.length.split(":")
                                if (parts.size == 2) {
                                    parts[0].toInt() * 60 + parts[1].toInt()
                                } else {
                                    0
                                }
                            } catch (e: Exception) { 0 }
                        )
                    }
                    
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                         ElegantVideoCard(
                             video = videoItem,
                             index = 0, // Not important for static card
                             animationEnabled = false,
                             transitionEnabled = false,
                             showPublishTime = true,
                             onClick = { bvid, _ -> onVideoClick(bvid) }
                         )
                    }
                }
                
                // 加载中指示器 (跨满列)
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CupertinoActivityIndicator()
                        }
                    }
                } else if (!state.hasMoreVideos && state.videos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("—— 没有更多了 ——", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
            
            3 -> {  // 合集和系列 (跨满列)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "合集和系列",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                
                // 显示合集
                state.seasons.forEach { season ->
                    item(key = "season_${season.meta.season_id}", span = { GridItemSpan(maxLineSpan) }) {
                        SeasonSection(
                            season = season,
                            archives = state.seasonArchives[season.meta.season_id] ?: emptyList(),
                            onVideoClick = onVideoClick,
                            mid = state.userInfo.mid
                        )
                    }
                }
                
                // 显示系列
                state.series.forEach { series ->
                    item(key = "series_${series.meta.series_id}", span = { GridItemSpan(maxLineSpan) }) {
                        SeriesSection(
                            series = series,
                            archives = state.seriesArchives[series.meta.series_id] ?: emptyList(),
                            onVideoClick = onVideoClick
                        )
                    }
                }
                
                if (state.seasons.isEmpty() && state.series.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "该用户暂无合集和系列",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            else -> {  // 主页 或 动态 (跨满列)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该功能暂未开放",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceHeader(
    userInfo: SpaceUserInfo,
    relationStat: RelationStatData?,
    upStat: UpStatData?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        //  头图 Banner - 更紧凑的高度
        if (userInfo.topPhoto.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)  //  减少高度
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(userInfo.topPhoto))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
        }
        
        //  头像和基本信息区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = if (userInfo.topPhoto.isNotEmpty()) (-20).dp else 4.dp),  //  减少 offset
            verticalAlignment = Alignment.Bottom
        ) {
            // 头像（带边框）
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(userInfo.face))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                //  直播状态标识（如果正在直播）
                if (userInfo.liveRoom?.liveStatus == 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .background(Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "播",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            // 用户名和信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userInfo.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(Modifier.width(6.dp))
                    
                    //  等级徽章
                    Surface(
                        color = when {
                            userInfo.level >= 6 -> Color(0xFFFF6699)  // 粉色高等级
                            userInfo.level >= 4 -> Color(0xFF00AEEC)  // 蓝色中等级
                            else -> Color(0xFF9E9E9E)  // 灰色低等级
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "LV${userInfo.level}",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    
                    //  性别图标
                    if (userInfo.sex == "男") {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "♂",
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00AEEC),  // 蓝色
                            fontSize = 14.sp
                        )
                    } else if (userInfo.sex == "女") {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "♀",
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFF6699),  // 粉色
                            fontSize = 14.sp
                        )
                    }
                    
                    // VIP 标签
                    if (userInfo.vip.status == 1 && userInfo.vip.label.text.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFF6699),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = userInfo.vip.label.text,
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 签名
        if (userInfo.sign.isNotEmpty()) {
            Text(
                text = userInfo.sign,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = if (userInfo.topPhoto.isNotEmpty()) 0.dp else 8.dp)
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // 数据统计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 关注
            StatItem(label = "关注", value = relationStat?.following ?: 0)
            // 粉丝
            StatItem(label = "粉丝", value = relationStat?.follower ?: 0)
            // 获赞
            StatItem(label = "获赞", value = (upStat?.likes ?: 0).toInt())
            // 播放
            StatItem(label = "播放", value = (upStat?.archive?.view ?: 0).toInt())
        }
        
        Spacer(Modifier.height(12.dp))
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = FormatUtils.formatStat(value.toLong()),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SpaceVideoItem(video: SpaceVideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iOSTapEffect(scale = 0.98f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(video.pic))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 时长标签
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = video.length,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        // 信息
        Column(
            modifier = Modifier
                .weight(1f)
                .height(94.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = video.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    CupertinoIcons.Default.Play,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = FormatUtils.formatStat(video.play.toLong()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                Spacer(Modifier.width(12.dp))
                
                Icon(
                    CupertinoIcons.Default.Message,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = FormatUtils.formatStat(video.comment.toLong()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 *  分类标签行组件
 */
@Composable
private fun CategoryTabRow(
    categories: List<SpaceVideoCategory>,
    selectedTid: Int,
    onCategoryClick: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // 全部按钮
        item {
            CategoryChip(
                text = "全部",
                isSelected = selectedTid == 0,
                onClick = { onCategoryClick(0) }
            )
        }
        
        // 分类按钮
        items(categories, key = { it.tid }) { category ->
            CategoryChip(
                text = "${category.name} (${category.count})",
                isSelected = selectedTid == category.tid,
                onClick = { onCategoryClick(category.tid) }
            )
        }
    }
}

/**
 *  分类标签芯片
 */
@Composable
private fun CategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) 
                Color.White 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 *  排序按钮行
 */
@Composable
private fun SortButtonRow(
    currentOrder: VideoSortOrder,
    onOrderClick: (VideoSortOrder) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoSortOrder.entries.forEach { order ->
            SortChip(
                text = order.displayName,
                isSelected = currentOrder == order,
                onClick = { onOrderClick(order) }
            )
        }
    }
}

/**
 *  排序芯片
 */
@Composable
private fun SortChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary 
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) 
                Color.White 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 *  Space Tab 导航栏
 */
@Composable
private fun SpaceTabRow(
    selectedTab: Int,
    videoCount: Int,
    collectionsCount: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        TabItem(0, "主页", CupertinoIcons.Default.House),
        TabItem(1, "动态", CupertinoIcons.Default.Bell),
        TabItem(2, "投稿", CupertinoIcons.Default.PlayCircle, if (videoCount > 999) "999+" else if (videoCount > 0) videoCount.toString() else null),
        TabItem(3, "合集和系列", CupertinoIcons.Default.Folder, if (collectionsCount > 0) collectionsCount.toString() else null)
    )
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                SpaceTab(
                    tab = tab,
                    isSelected = selectedTab == tab.index,
                    onClick = { onTabSelected(tab.index) }
                )
            }
        }
        
        // 下划线指示器
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

private data class TabItem(
    val index: Int,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: String? = null
)

@Composable
private fun SpaceTab(
    tab: TabItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = tab.title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 数量徽章
            if (tab.badge != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = tab.badge,
                    fontSize = 11.sp,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        // 选中指示条
        if (isSelected) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 *  合集区块 - 横向滚动
 */
@Composable
private fun SeasonSection(
    season: SeasonItem,
    archives: List<SeasonArchiveItem>,
    onVideoClick: (String) -> Unit,
    mid: Long = 0L  // UP主的mid，用于构建分享链接
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "合集 · ${season.meta.name}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = " · ${season.meta.total}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            // 分享按钮
            IconButton(
                onClick = {
                    // 使用 space.bilibili.com 域名（www 域名会 404）
                    val shareUrl = "https://space.bilibili.com/$mid/lists/${season.meta.season_id}?type=season"
                    val shareText = "${season.meta.name}\n$shareUrl"
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "【合集】${season.meta.name}")
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "分享合集"))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    CupertinoIcons.Default.SquareAndArrowUp,
                    contentDescription = "分享合集",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 横向视频列表
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(archives, key = { it.bvid }) { archive ->
                SeasonVideoCard(
                    archive = archive,
                    onClick = { onVideoClick(archive.bvid) }
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
    }
}


/**
 *  系列区块
 */
@Composable
private fun SeriesSection(
    series: SeriesItem,
    archives: List<SeriesArchiveItem>,
    onVideoClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "系列 · ${series.meta.name}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = " · ${series.meta.total}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        // 横向视频列表
        if (archives.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(archives, key = { it.bvid }) { archive ->
                    SeriesVideoCard(
                        archive = archive,
                        onClick = { onVideoClick(archive.bvid) }
                    )
                }
            }
        } else {
            Text(
                text = "暂无视频",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        Spacer(Modifier.height(12.dp))
    }
}

/**
 *  合集视频卡片 - 紧凑横向布局
 */
@Composable
private fun SeasonVideoCard(
    archive: SeasonArchiveItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(archive.pic))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 时长标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = FormatUtils.formatDuration(archive.duration),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        // 标题
        Text(
            text = archive.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 播放量
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                CupertinoIcons.Default.Play,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = FormatUtils.formatStat(archive.stat.view),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 *  系列视频卡片 - 紧凑横向布局
 */
@Composable
private fun SeriesVideoCard(
    archive: SeriesArchiveItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(archive.pic))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 时长标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = FormatUtils.formatDuration(archive.duration),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        // 标题
        Text(
            text = archive.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 播放量
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                CupertinoIcons.Default.Play,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = FormatUtils.formatStat(archive.stat.view),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
