package com.android.purebilibili.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.android.purebilibili.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val density = LocalDensity.current

    // Background color - Clean White/Surface for that iOS feel
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() 
        ) {
            
            // --- Top Content (Pager) ---
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                // Basic Parallax/Scale Effect
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Subtle scale effect
                            val scale = lerp(1f, 0.9f, pageOffset.absoluteValue)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.3f, pageOffset.absoluteValue)
                        }
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> DesignPage() // Placeholder for now
                        2 -> FeaturesPage() // Placeholder for now
                        3 -> GetStartedPage(onFinish = onFinish) // Placeholder for now
                    }
                }
            }

            // --- Bottom Control Area ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                
                // iOS-style Page Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        // Animate width for the selected indicator
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            label = "indicatorWidth"
                        )
                        val color = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Action Button Area (Keeps layout stable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pagerState.currentPage == 3,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Button(
                            onClick = onFinish,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(28.dp), // Squircle-ish
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp
                            )
                        ) {
                            Text(
                                text = "开始体验",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Page 1: Welcome (Minimalist & Bold) ---
@Composable
fun WelcomePage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Observe the current app icon key
    val appIconKey by com.android.purebilibili.core.store.SettingsManager.getAppIcon(context).collectAsState(initial = "icon_3d")
    
    // Find the corresponding icon resource
    // We reuse the mapping from IconSettingsScreen to ensure consistency
    val iconRes = remember(appIconKey) {
        val groups = com.android.purebilibili.feature.settings.getIconGroups()
        val allIcons = groups.flatMap { it.icons }
        val selectedOption = allIcons.find { it.key == appIconKey }
        // Fallback to 3D foreground if not found or if the resource causes issues
        // Note: IconOptions use _round versions which are usually suitable for display
        selectedOption?.iconRes ?: R.mipmap.ic_launcher_3d_foreground
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minimalist Icon / Brand
        // Use AsyncImage to handle potentially different image types smoothly, similar to IconSettingsScreen
        coil.compose.AsyncImage(
            model = iconRes,
            contentDescription = "App Icon",
            modifier = Modifier
                .size(120.dp) // Larger for better visual impact
                .clip(RoundedCornerShape(24.dp)) // Apply a soft clip to match the "Mask" feel
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome to\nBiliPai",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 42.sp,
                lineHeight = 48.sp
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "纯净 · 流畅 · 沉浸",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp
        )
    }
}

// --- Placeholders for other pages (To be implemented next) ---

// --- Page 2: Design (Immersive & Glassmorphism) ---
@Composable
fun DesignPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
        ) {
            // Abstract "Cards" representing depth
            // Card 3 (Back)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -40f
                        scaleX = 0.8f
                        rotationZ = -5f
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
            
            // Card 2 (Middle)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -20f
                        scaleX = 0.9f
                        rotationZ = 3f
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            )
            
            // Card 1 (Front - Main)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Mock UI Content
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                         Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFCC80).copy(alpha = 0.8f)))
                         Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF81C784).copy(alpha = 0.8f)))
                         Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF64B5F6).copy(alpha = 0.8f)))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "沉浸式设计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "原生质感 · 丝滑流畅 · 细节打磨",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Page 3: Features (Clean List) ---
@Composable
fun FeaturesPage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "强大且纯净",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        val features = listOf(
            Triple(Icons.Filled.Lock, "无广告打扰", "专注于内容本身"),
            Triple(Icons.Filled.Star, "4K 超清画质", "细节毕现的视觉盛宴"),
            Triple(Icons.Filled.Refresh, "智能会话去重", "每次刷新都是新内容")
        )

        features.forEachIndexed { index, feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.first,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column {
                    Text(
                        text = feature.second,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = feature.third,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Page 4: Get Started (Ready?) ---
@Composable
fun GetStartedPage(onFinish: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "准备好了吗？",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "开启您的纯净 BiliPai 之旅",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        // Spacer to push content up slightly to leave room for the button
        Spacer(modifier = Modifier.height(80.dp))
    }
}
