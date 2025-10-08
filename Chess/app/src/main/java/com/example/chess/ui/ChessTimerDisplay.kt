package com.example.chess.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chess timer display component showing remaining time for both players
 */
@Composable
fun ChessTimerDisplay(
    timeRemainingMs: Long,
    isActive: Boolean,
    isPlayerTimer: Boolean,
    modifier: Modifier = Modifier
) {
    val isWarning = timeRemainingMs <= 30_000 && timeRemainingMs > 10_000
    val isCritical = timeRemainingMs <= 10_000
    
    // Blinking animation for critical time
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCritical && isActive) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val backgroundColor = when {
        isCritical && isActive -> Color(0xFFE74C3C).copy(alpha = alpha)
        isWarning && isActive -> Color(0xFFE67E22)
        isActive -> Color(0xFF2ECC71)
        else -> Color(0xFF2A2A2A)
    }
    
    val textColor = when {
        isCritical || isWarning -> Color.White
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPlayerTimer) "Your Time" else "Opponent",
                fontSize = 14.sp,
                color = textColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = formatTime(timeRemainingMs),
                fontSize = 24.sp,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Format milliseconds to MM:SS or M:SS
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

