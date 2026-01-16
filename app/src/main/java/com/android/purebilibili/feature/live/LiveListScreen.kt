// 文件路径: feature/live/LiveListScreen.kt
package com.android.purebilibili.feature.live

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.responsiveContentWidth

// 辅助函数：格式化数字
private fun formatNumber(num: Int): String {
    return when {
        num >= 10000 -> String.format("%.1f万", num / 10000f)
        else -> num.toString()
    }
}

/**
 * 直播项目数据类
 */
data class LiveRoomItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val uname: String,
    val face: String,
    val online: Int,
    val areaName: String,
    val liveStatus: Int = 1  // 1=直播中
)

/**
 * 直播列表 UI 状态
 */
data class LiveListUiState(
    val recommendItems: List<LiveRoomItem> = emptyList(),
    val followItems: List<LiveRoomItem> = emptyList(),
    val areaList: List<LiveAreaParent> = emptyList(),
    val selectedAreaId: Int = 0,  // 选中的分区 ID (0=全部)
    val areaItems: List<LiveRoomItem> = emptyList(),
    val isLoading: Boolean = false,
    val isAreaLoading: Boolean = false,
    val error: String? = null,
    val currentTab: Int = 0  // 0=推荐, 1=分区, 2=关注
)

/**
 * 直播列表 ViewModel
 */
class LiveListViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LiveListUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val api = NetworkModule.api
                
                // 并行加载推荐直播和分区列表
                val recommendJob = launch { loadRecommendLive() }
                val areaJob = launch { loadAreaList() }
                
                recommendJob.join()
                areaJob.join()
                
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载失败")
            }
        }
    }
    
    private suspend fun loadRecommendLive() {
        try {
            val api = NetworkModule.api
            val response = api.getLiveList(parentAreaId = 0, page = 1, pageSize = 30)
            if (response.code == 0 && response.data != null) {
                val items = response.data.getAllRooms().map { room ->
                    LiveRoomItem(
                        roomId = room.roomid,
                        title = room.title,
                        cover = room.cover.ifEmpty { room.userCover.ifEmpty { room.keyframe } },
                        uname = room.uname,
                        face = room.face,
                        online = room.online,
                        areaName = room.areaName
                    )
                }
                _uiState.value = _uiState.value.copy(recommendItems = items)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun loadAreaList() {
        try {
            val api = NetworkModule.api
            val response = api.getLiveAreaList()
            if (response.code == 0 && response.data != null) {
                _uiState.value = _uiState.value.copy(areaList = response.data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadFollowLive() {
        viewModelScope.launch {
            try {
                val api = NetworkModule.api
                val response = api.getFollowedLive(page = 1, pageSize = 50)
                if (response.code == 0 && response.data != null) {
                    val items = response.data.list?.filter { it.liveStatus == 1 }?.map { room ->
                        room.toLiveRoom().let {
                            LiveRoomItem(
                                roomId = it.roomid,
                                title = it.title,
                                cover = it.cover.ifEmpty { it.userCover.ifEmpty { it.keyframe } },
                                uname = it.uname,
                                face = it.face,
                                online = it.online,
                                areaName = it.areaName,
                                liveStatus = 1
                            )
                        }
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(followItems = items)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun loadAreaLive(parentAreaId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAreaLoading = true, selectedAreaId = parentAreaId)
            try {
                val api = NetworkModule.api
                val response = api.getLiveSecondAreaList(parentAreaId = parentAreaId, page = 1)
                if (response.code == 0 && response.data?.list != null) {
                    val items = response.data.list.map { room ->
                        LiveRoomItem(
                            roomId = room.roomid,
                            title = room.title,
                            cover = room.cover.ifEmpty { room.userCover.ifEmpty { room.keyframe } },
                            uname = room.uname,
                            face = room.face,
                            online = room.online,
                            areaName = room.areaName
                        )
                    }
                    _uiState.value = _uiState.value.copy(areaItems = items, isAreaLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isAreaLoading = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isAreaLoading = false)
            }
        }
    }
    
    fun setTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tabIndex)
        // 切换到关注 Tab 时加载数据
        if (tabIndex == 2 && _uiState.value.followItems.isEmpty()) {
            loadFollowLive()
        }
        // 切换到分区 Tab 时，如果分区列表有数据但未选择分区，自动选择第一个
        if (tabIndex == 1 && _uiState.value.areaList.isNotEmpty() && _uiState.value.selectedAreaId == 0) {
            loadAreaLive(_uiState.value.areaList.first().id)
        }
    }
    
    fun refresh() {
        loadInitialData()
        if (_uiState.value.currentTab == 2) {
            loadFollowLive()
        }
    }
}

/**
 *  直播列表页面 - iOS 风格三 Tab 布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveListScreen(
    onBack: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,  // roomId, title, uname
    viewModel: LiveListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    //  设置导航栏透明
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val originalNavBarColor = window?.navigationBarColor ?: android.graphics.Color.TRANSPARENT
        if (window != null) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        onDispose {
            if (window != null) {
                window.navigationBarColor = originalNavBarColor
            }
        }
    }

    
    // 📐 [Tablet Adaptation] Calculate adaptive columns
    val windowSizeClass = LocalWindowSizeClass.current
    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(windowSizeClass.widthDp, 1000.dp)
    } else {
        windowSizeClass.widthDp
    }
    
    val gridColumns = remember(contentWidth) {
        if (windowSizeClass.isExpandedScreen) {
            val minColumnWidth = 200.dp
            val columns = (contentWidth / minColumnWidth).toInt()
            columns.coerceIn(2, 6)
        } else {
            2
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Text(
                            "直播", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                
                //  iOS 风格分段控件
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CupertinoSegmentedControl(
                        selectedTabIndex = state.currentTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CupertinoSegmentedControlTab(
                            isSelected = state.currentTab == 0,
                            onClick = { viewModel.setTab(0) }
                        ) {
                            Text("推荐", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        CupertinoSegmentedControlTab(
                            isSelected = state.currentTab == 1,
                            onClick = { viewModel.setTab(1) }
                        ) {
                            Text("分区", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        CupertinoSegmentedControlTab(
                            isSelected = state.currentTab == 2,
                            onClick = { viewModel.setTab(2) }
                        ) {
                            Text("关注", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .responsiveContentWidth(maxWidth = 1000.dp) // 📐 [Tablet Adaptation] Limit content width
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CupertinoActivityIndicator()
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: "未知错误",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    // 内容区域
                    AnimatedContent(
                        targetState = state.currentTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "tab_content"
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> RecommendTab(
                                items = state.recommendItems,
                                gridColumns = gridColumns,
                                onLiveClick = onLiveClick
                            )
                            1 -> AreaTab(
                                areaList = state.areaList,
                                selectedAreaId = state.selectedAreaId,
                                areaItems = state.areaItems,
                                isLoading = state.isAreaLoading,
                                gridColumns = gridColumns,
                                onAreaSelected = { viewModel.loadAreaLive(it) },
                                onLiveClick = onLiveClick
                            )
                            2 -> FollowTab(
                                items = state.followItems,
                                gridColumns = gridColumns,
                                onLiveClick = onLiveClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 *  推荐直播 Tab
 */
@Composable
private fun RecommendTab(
    items: List<LiveRoomItem>,
    gridColumns: Int,
    onLiveClick: (Long, String, String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState("暂无推荐直播")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                LiveRoomCard(
                    item = item,
                    onClick = { onLiveClick(item.roomId, item.title, item.uname) }
                )
            }
        }
    }
}

/**
 *  分区 Tab - iOS 风格
 */
@Composable
private fun AreaTab(
    areaList: List<LiveAreaParent>,
    selectedAreaId: Int,
    areaItems: List<LiveRoomItem>,
    isLoading: Boolean,
    gridColumns: Int,
    onAreaSelected: (Int) -> Unit,
    onLiveClick: (Long, String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        //  分区横向滚动选择器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            areaList.forEach { area ->
                val isSelected = area.id == selectedAreaId
                Surface(
                    modifier = Modifier.clickable { onAreaSelected(area.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = area.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // 分区直播列表
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CupertinoActivityIndicator()
                }
            }
            areaItems.isEmpty() -> {
                EmptyState("该分区暂无直播")
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(areaItems) { item ->
                        LiveRoomCard(
                            item = item,
                            onClick = { onLiveClick(item.roomId, item.title, item.uname) }
                        )
                    }
                }
            }
        }
    }
}

/**
 *  关注直播 Tab
 */
@Composable
private fun FollowTab(
    items: List<LiveRoomItem>,
    gridColumns: Int,
    onLiveClick: (Long, String, String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState("暂无关注的直播\n关注的主播开播后将显示在这里")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                LiveRoomCard(
                    item = item,
                    onClick = { onLiveClick(item.roomId, item.title, item.uname) }
                )
            }
        }
    }
}

/**
 *  空状态组件
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                CupertinoIcons.Default.Video,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 *  直播间卡片 - iOS 风格
 */
@Composable
private fun LiveRoomCard(
    item: LiveRoomItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.cover,
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
                                Color.Black.copy(alpha = 0.6f)
                            ),
                            startY = 100f
                        )
                    )
            )
            
            // 在线人数
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    CupertinoIcons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = formatNumber(item.online),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            
            // LIVE 标签
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        Color(0xFFFF5252).copy(alpha = 0.95f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
            
            // 分区标签
            if (item.areaName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.areaName,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
        
        // 标题和主播信息
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsyncImage(
                    model = item.face,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = item.uname,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
