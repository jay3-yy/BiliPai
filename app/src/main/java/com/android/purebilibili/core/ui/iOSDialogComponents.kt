package com.android.purebilibili.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.theme.iOSBlue

/**
 * iOS-style Alert Dialog.
 * Mimics the look of standard iOS UIAlertController (Alert style).
 */
@Composable
fun IOSAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            modifier = Modifier
                .width(270.dp) // Standard iOS Alert width
                .clip(RoundedCornerShape(14.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), // Slightly transparent mimic
            tonalElevation = 0.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title and Text Content
                Column(
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (title != null) {
                        ProvideTextStyle(
                            value = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                title()
                            }
                        }
                    }
                    
                    if (title != null && text != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    if (text != null) {
                        ProvideTextStyle(
                            value = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                text()
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                
                // Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min) // Ensure equal height
                ) {
                    if (dismissButton != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal, // Dismiss is usually normal
                                    color = iOSBlue
                                )
                            ) {
                                dismissButton()
                            }
                        }
                    }
                    
                    if (dismissButton != null && confirmButton != null) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), 
                            thickness = 0.5.dp,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    
                    if (confirmButton != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, // Confirm is usually bold
                                    color = iOSBlue
                                )
                            ) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A helper for buttons inside IOSAlertDialog if you need absolute control,
 * basically a wrapper that removes TextButton padding issues if standard TextButton is used.
 * But actually providing TextStyle above might be enough for simple Text() children.
 * If user passes TextButton, we might need to conform it.
 */
@Composable
fun IOSDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
