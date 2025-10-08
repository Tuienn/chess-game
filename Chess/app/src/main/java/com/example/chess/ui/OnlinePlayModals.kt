package com.example.chess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chess.R
import com.example.chess.model.TimeControl
import com.example.chess.network.ChessApiService
import com.example.chess.network.SocketService
import kotlinx.coroutines.launch

@Composable
fun OnlinePlayModal(
    onDismiss: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onRoomCreated: (String, TimeControl) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { ChessApiService.create() }
    val socketService = remember { SocketService.getInstance() }
    var isCreatingRoom by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTimeControl by remember { mutableStateOf(TimeControl.FIVE_MINUTES) }
    
    // Connect socket when modal opens
    LaunchedEffect(Unit) {
        if (!socketService.isConnected()) {
            socketService.connect()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Play Online",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Choose how you want to play online",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time Selection
                TimeSelectionSection(
                    selectedTimeControl = selectedTimeControl,
                    onTimeControlSelected = { selectedTimeControl = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isCreatingRoom = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val response = apiService.createRoom()
                                if (response.isSuccessful) {
                                    response.body()?.let { roomResponse ->
                                        onRoomCreated(roomResponse.code, selectedTimeControl)
                                        onCreateRoom()
                                    }
                                } else {
                                    errorMessage = "Failed to create room: ${response.code()}"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network error: ${e.message}"
                            } finally {
                                isCreatingRoom = false
                            }
                        }
                    },
                    enabled = !isCreatingRoom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color.White
                    )
                ) {
                    if (isCreatingRoom) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Create new room",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onJoinRoom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Input room code",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Error message display
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateRoomModal(
    onDismiss: () -> Unit,
    roomCode: String,
    selectedColor: String = "white"
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedMessage by remember { mutableStateOf(false) }
    val socketService = remember { SocketService.getInstance() }
    var opponentJoined by remember { mutableStateOf(false) }
    
    // Setup socket listeners
    LaunchedEffect(roomCode) {
        socketService.setOnOpponentJoinedCallback {
            opponentJoined = true
        }
        
        // Join the room as the creator
        val uid = "creator_${System.currentTimeMillis()}"
        socketService.joinRoom(roomCode, uid)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Room Created!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Share this code with your friend to join the game",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                // Display selected color
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2A2A2A),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You are playing as: ",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Image(
                        painter = painterResource(
                            if (selectedColor == "white") R.drawable.wk else R.drawable.bk
                        ),
                        contentDescription = if (selectedColor == "white") "White King" else "Black King",
                        modifier = Modifier.size(20.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedColor == "white") "White" else "Black",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedColor == "white") Color(0xFF2ECC71) else Color(0xFF3498DB)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2A2A2A),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionContainer {
                        Text(
                            text = roomCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2ECC71),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(roomCode))
                        showCopiedMessage = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3498DB),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Copy",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showCopiedMessage) "Copied!" else "Copy code",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showCopiedMessage) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        showCopiedMessage = false
                    }
                }

                Text(
                    text = if (opponentJoined) "Opponent joined! Starting game..." else "Waiting for opponent to join...",
                    fontSize = 14.sp,
                    color = if (opponentJoined) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun JoinRoomModal(
    onDismiss: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
    val isValidCode = roomCode.length == 6 && roomCode.all { it.isLetterOrDigit() }
    val socketService = remember { SocketService.getInstance() }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Connect socket when modal opens
    LaunchedEffect(Unit) {
        if (!socketService.isConnected()) {
            socketService.connect()
        }
        
        // Setup socket callbacks
        socketService.setOnRoomJoinedCallback { joinedRoomCode, playerColor ->
            isJoining = false
            onJoinRoom(joinedRoomCode)
        }
        
        socketService.setOnErrorCallback { error ->
            isJoining = false
            errorMessage = error
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Join Room",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Enter the room code to join a game",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { newValue ->
                        if (newValue.length <= 6) {
                            roomCode = newValue.uppercase()
                        }
                    },
                    label = { Text("Room Code", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("Enter 6-digit code", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3498DB),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (isValidCode && !isJoining) {
                            isJoining = true
                            errorMessage = null
                            // Generate a simple UID for now - in production, this should be more robust
                            val uid = "player_${System.currentTimeMillis()}"
                            socketService.joinRoom(roomCode, uid)
                        }
                    },
                    enabled = isValidCode && !isJoining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Join Room",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Room codes are 6 characters long",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                
                // Error message display
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorSelectionModal(
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Your Color",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Select which color you want to play as",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // White pieces option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable { onColorSelected("white") }
                            .border(
                                2.dp,
                                Color(0xFF2ECC71).copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Color.White,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.wk),
                                    contentDescription = "White King",
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "White",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "You go first",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Black pieces option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable { onColorSelected("black") }
                            .border(
                                2.dp,
                                Color(0xFF2ECC71).copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Color(0xFF1A1A1A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.bk),
                                    contentDescription = "Black King",
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Black",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Opponent goes first",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Text(
                    text = "Tap to select your preferred color",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6)
        .map { chars.random() }
        .joinToString("")
}

@Composable
private fun TimeSelectionSection(
    selectedTimeControl: TimeControl,
    onTimeControlSelected: (TimeControl) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Time Control",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeControlButton(
                label = "1m",
                timeControl = TimeControl.ONE_MINUTE,
                selected = selectedTimeControl == TimeControl.ONE_MINUTE,
                onClick = { onTimeControlSelected(TimeControl.ONE_MINUTE) },
                modifier = Modifier.weight(1f)
            )
            TimeControlButton(
                label = "3m",
                timeControl = TimeControl.THREE_MINUTES,
                selected = selectedTimeControl == TimeControl.THREE_MINUTES,
                onClick = { onTimeControlSelected(TimeControl.THREE_MINUTES) },
                modifier = Modifier.weight(1f)
            )
            TimeControlButton(
                label = "5m",
                timeControl = TimeControl.FIVE_MINUTES,
                selected = selectedTimeControl == TimeControl.FIVE_MINUTES,
                onClick = { onTimeControlSelected(TimeControl.FIVE_MINUTES) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeControlButton(
                label = "10m",
                timeControl = TimeControl.TEN_MINUTES,
                selected = selectedTimeControl == TimeControl.TEN_MINUTES,
                onClick = { onTimeControlSelected(TimeControl.TEN_MINUTES) },
                modifier = Modifier.weight(1f)
            )
            TimeControlButton(
                label = "30m",
                timeControl = TimeControl.THIRTY_MINUTES,
                selected = selectedTimeControl == TimeControl.THIRTY_MINUTES,
                onClick = { onTimeControlSelected(TimeControl.THIRTY_MINUTES) },
                modifier = Modifier.weight(1f)
            )
            TimeControlButton(
                label = "∞",
                timeControl = TimeControl.NO_LIMIT,
                selected = selectedTimeControl == TimeControl.NO_LIMIT,
                onClick = { onTimeControlSelected(TimeControl.NO_LIMIT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeControlButton(
    label: String,
    timeControl: TimeControl,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF3498DB) else Color(0xFF2A2A2A),
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
