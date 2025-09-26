package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chess.R
import com.example.chess.model.Side

data class AiDifficultyRange(
    val minLevel: Int = 0,
    val maxLevel: Int = 20,
    val defaultLevel: Int = 10,
    val minThinkTimeMs: Int = 200,
    val maxThinkTimeMs: Int = 2000,
    val defaultThinkTimeMs: Int = 500
)

@Composable
fun AiPlayModal(
    onDismiss: () -> Unit,
    onStartGame: (Side, Int, Int) -> Unit,
    isProcessing: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    difficultyRange: AiDifficultyRange = AiDifficultyRange()
) {
    var selectedSide by rememberSaveable { mutableStateOf(Side.WHITE) }
    var level by rememberSaveable { mutableStateOf(difficultyRange.defaultLevel.toFloat()) }
    var thinkTime by rememberSaveable { mutableStateOf(difficultyRange.defaultThinkTimeMs.toFloat()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HeaderSection(onDismiss = onDismiss, isProcessing = isProcessing)
                ColorSelectionSection(
                    selectedSide = selectedSide,
                    onSelectSide = { if (!isProcessing) selectedSide = it }
                )
                DifficultySection(
                    level = level,
                    onLevelChange = { if (!isProcessing) level = it },
                    thinkTime = thinkTime,
                    onThinkTimeChange = { if (!isProcessing) thinkTime = it },
                    difficultyRange = difficultyRange,
                    enabled = !isProcessing
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        if (!isProcessing) {
                            onStartGame(selectedSide, level.toInt(), thinkTime.toInt())
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color.Black
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Start game",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(onDismiss: () -> Unit, isProcessing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Play vs AI",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Pick your side and tune Stockfish",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        IconButton(
            onClick = onDismiss,
            enabled = !isProcessing
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ColorSelectionSection(
    selectedSide: Side,
    onSelectSide: (Side) -> Unit
) {
    Text(
        text = "Choose your pieces",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AiColorOption(
            label = "White",
            description = "You move first",
            iconRes = R.drawable.wk,
            selected = selectedSide == Side.WHITE,
            onClick = { onSelectSide(Side.WHITE) }
        )

        AiColorOption(
            label = "Black",
            description = "AI moves first",
            iconRes = R.drawable.bk,
            selected = selectedSide == Side.BLACK,
            onClick = { onSelectSide(Side.BLACK) }
        )
    }
}

@Composable
private fun DifficultySection(
    level: Float,
    onLevelChange: (Float) -> Unit,
    thinkTime: Float,
    onThinkTimeChange: (Float) -> Unit,
    difficultyRange: AiDifficultyRange,
    enabled: Boolean
) {
    Text(
        text = "AI level",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    Slider(
        value = level,
        onValueChange = onLevelChange,
        valueRange = difficultyRange.minLevel.toFloat()..difficultyRange.maxLevel.toFloat(),
        steps = (difficultyRange.maxLevel - difficultyRange.minLevel) - 1,
        colors = SliderDefaults.colors(
            activeTrackColor = Color(0xFF9B59B6),
            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            thumbColor = Color(0xFF9B59B6)
        ),
        enabled = enabled
    )

    Text(
        text = "Skill ${level.toInt()} (0 easiest — ${difficultyRange.maxLevel} hardest)",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.8f)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Thinking time",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    Slider(
        value = thinkTime,
        onValueChange = onThinkTimeChange,
        valueRange = difficultyRange.minThinkTimeMs.toFloat()..difficultyRange.maxThinkTimeMs.toFloat(),
        steps = 9,
        colors = SliderDefaults.colors(
            activeTrackColor = Color(0xFF3498DB),
            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            thumbColor = Color(0xFF3498DB)
        ),
        enabled = enabled
    )

    Text(
        text = "${thinkTime.toInt()} ms per move",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.8f)
    )
}

@Composable
private fun RowScope.AiColorOption(
    label: String,
    description: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.2f)
    val backgroundColor = if (selected) Color(0x332ECC71) else Color(0xFF2A2A2A)

    Card(
        modifier = Modifier
            .weight(1f)
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
