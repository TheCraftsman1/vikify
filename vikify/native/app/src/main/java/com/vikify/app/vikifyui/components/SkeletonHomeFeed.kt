package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer

@Composable
fun SkeletonHomeFeed(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .shimmer() // Apply shimmer effect to everything
            .padding(horizontal = 20.dp)
    ) {
        // 1. Header Skeleton
        Spacer(modifier = Modifier.height(60.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 2. Quick Resume Grid Skeleton (2 rows)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCard(modifier = Modifier.weight(1f).height(60.dp))
            SkeletonCard(modifier = Modifier.weight(1f).height(60.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCard(modifier = Modifier.weight(1f).height(60.dp))
            SkeletonCard(modifier = Modifier.weight(1f).height(60.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 3. Horizontal Rail Skeleton
        Box(
             modifier = Modifier
                 .width(140.dp)
                 .height(20.dp)
                 .clip(RoundedCornerShape(4.dp))
                 .background(Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Column {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.width(100.dp).height(12.dp).background(Color.Gray.copy(alpha=0.3f))
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.3f))
    )
}
