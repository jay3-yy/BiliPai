package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.core.theme.iosCard
import com.android.purebilibili.core.theme.iOSSystemGray3
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSSystemGray

/**
 * Related Video Components
 * 
 * Contains components for displaying related videos:
 * - RelatedVideosHeader: Section header
 * - RelatedVideoItem: Individual video card
 * 
 * Requirement Reference: AC3.3 - Related video components in dedicated file
 */

/**
 * Related Videos Header
 */
@Composable
fun RelatedVideosHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background // Automated iOS System Gray 6 via Theme
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u66f4\u591a\u63a8\u8350",
                style = MaterialTheme.typography.titleMedium, // Should be ~17sp SemiBold "Body/Headline"
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Related Video Item (iOS style optimized)
 */
@Composable
fun RelatedVideoItem(
    video: RelatedVideo, 
    isFollowed: Boolean = false,
    onClick: () -> Unit
) {
    // Top-level container acts as the button/card
    // Using simple Row structure but with IOS touch physics manually or via wrapper
    // Since we want the whole row to be clickable and scale:
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp) // Spacing between items
            .iosCard(
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colorScheme.surface,
                elevation = 0.dp, // Flat list style often doesn't need elevation for cells, or very subtle
                pressEffect = true,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp) // Internal padding
        ) {
            // Video cover
            Box(
                modifier = Modifier
                    .width(130.dp)  // Slightly smaller to give text breathing room
                    .height(82.dp)  // maintain 16:9ish ratio
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
                
                // Duration label - Plain text with shadow, no background (Apple style)
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            blurRadius = 4f,
                            offset = Offset(0f, 1f)
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Video info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy( // 15sp regular/medium
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column {
                    // UP owner info row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.owner.name,
                            style = MaterialTheme.typography.labelMedium, // 12sp
                            color = MaterialTheme.colorScheme.onSurfaceVariant, // System Gray
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        //  已关注标签
                        if (isFollowed) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "已关注",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Stats row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Views
                        StatItem(icon = CupertinoIcons.Filled.Play, text = FormatUtils.formatStat(video.stat.view.toLong()))
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Danmaku
                        StatItem(icon = CupertinoIcons.Filled.BubbleLeft, text = FormatUtils.formatStat(video.stat.danmaku.toLong()))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline, // System Gray 3 or similar
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
