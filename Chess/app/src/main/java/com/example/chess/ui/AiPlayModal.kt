package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.chess.model.TimeControl
import androidx.compose.runtime.saveable.rememberSaveable

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
    onStartGame: (Side, Int, Int, TimeControl) -> Unit,
    isProcessing: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    difficultyRange: AiDifficultyRange = AiDifficultyRange()
) {
    var selectedSide by rememberSaveable { mutableStateOf(Side.WHITE) }
    var level by rememberSaveable { mutableStateOf(difficultyRange.defaultLevel.toFloat()) }
    var thinkTime by rememberSaveable { mutableStateOf(difficultyRange.defaultThinkTimeMs.toFloat()) }
    var selectedTimeControl by remember { mutableStateOf(TimeControl.FIVE_MINUTES) }

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
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderSection(onDismiss = onDismiss, isProcessing = isProcessing)
                ColorSelectionSection(
                    selectedSide = selectedSide,
                    onSelectSide = { if (!isProcessing) selectedSide = it }
                )
                TimeControlSection(
                    selectedTimeControl = selectedTimeControl,
                    onTimeControlSelected = { if (!isProcessing) selectedTimeControl = it }
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
                            onStartGame(selectedSide, level.toInt(), thinkTime.toInt(), selectedTimeControl)
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color.Black
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Start game",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeControlSection(
    selectedTimeControl: TimeControl,
    onTimeControlSelected: (TimeControl) -> Unit
) {
    Text(
        text = "Time Control",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AiTimeControlChip("1m", TimeControl.ONE_MINUTE, selectedTimeControl, onTimeControlSelected)
        AiTimeControlChip("3m", TimeControl.THREE_MINUTES, selectedTimeControl, onTimeControlSelected)
        AiTimeControlChip("5m", TimeControl.FIVE_MINUTES, selectedTimeControl, onTimeControlSelected)
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AiTimeControlChip("10m", TimeControl.TEN_MINUTES, selectedTimeControl, onTimeControlSelected)
        AiTimeControlChip("30m", TimeControl.THIRTY_MINUTES, selectedTimeControl, onTimeControlSelected)
        AiTimeControlChip("∞", TimeControl.NO_LIMIT, selectedTimeControl, onTimeControlSelected)
    }
}

@Composable
private fun RowScope.AiTimeControlChip(
    label: String,
    timeControl: TimeControl,
    selected: TimeControl,
    onSelect: (TimeControl) -> Unit
) {
    Button(
        onClick = { onSelect(timeControl) },
        modifier = Modifier
            .weight(1f)
            .height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == timeControl) Color(0xFF9B59B6) else Color(0xFF2A2A2A),
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected == timeControl) FontWeight.Bold else FontWeight.Normal
        )
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
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Pick your side and tune Stockfish",
                fontSize = 13.sp,
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
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
        fontSize = 15.sp,
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
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.8f)
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Thinking time",
        fontSize = 15.sp,
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
        fontSize = 13.sp,
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
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
