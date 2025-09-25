package com.example.chess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.chess.model.*

/**
 * Demo composable để test online chess functionality
 * Sử dụng để kiểm tra socket connection và game synchronization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineChessDemo(
    onBack: () -> Unit
) {
    var showOnlineModal by remember { mutableStateOf(false) }
    var showCreateRoomModal by remember { mutableStateOf(false) }
    var showJoinRoomModal by remember { mutableStateOf(false) }
    var showColorSelectionModal by remember { mutableStateOf(false) }
    var createdRoomCode by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableStateOf("white") }
    var currentRoomCode by remember { mutableStateOf<String?>(null) }
    var playerColor by remember { mutableStateOf<Side?>(null) }
    var gameStarted by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!gameStarted) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Online Chess Demo",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Button(
                    onClick = { showOnlineModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color.White
                    )
                ) {
                    Text("Start Online Game", fontSize = 18.sp)
                }
                
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF95A5A6),
                        contentColor = Color.White
                    )
                ) {
                    Text("Back", fontSize = 18.sp)
                }
            }
        } else {
            // Show chess board when game starts
            currentRoomCode?.let { roomCode ->
                ChessBoardBitboard(
                    initial = initialBitboards(),
                    onBack = {
                        gameStarted = false
                        currentRoomCode = null
                        playerColor = null
                    },
                    playerColor = playerColor,
                    isOnlineMode = true,
                    roomCode = roomCode
                )
            }
        }
    }
    
    // Online Play Modal
    if (showOnlineModal) {
        OnlinePlayModal(
            onDismiss = { showOnlineModal = false },
            onCreateRoom = {
                showOnlineModal = false
                showColorSelectionModal = true
            },
            onJoinRoom = {
                showOnlineModal = false
                showJoinRoomModal = true
            },
            onRoomCreated = { roomCode ->
                Log.d("OnlineDemo", "Room created callback: roomCode=$roomCode")
                createdRoomCode = roomCode
            }
        )
    }
    
    // Color Selection Modal
    if (showColorSelectionModal) {
        ColorSelectionModal(
            onDismiss = { showColorSelectionModal = false },
            onColorSelected = { color ->
                Log.d("OnlineDemo", "Color selected: $color, createdRoomCode=$createdRoomCode")
                selectedColor = color
                showColorSelectionModal = false
                createdRoomCode?.let { roomCode ->
                    Log.d("OnlineDemo", "Opening CreateRoomModal with roomCode=$roomCode")
                    showCreateRoomModal = true
                }
            }
        )
    }
    
    // Create Room Modal
    if (showCreateRoomModal && createdRoomCode != null) {
        CreateRoomModal(
            onDismiss = {
                showCreateRoomModal = false
                createdRoomCode = null
            },
            roomCode = createdRoomCode!!,
            selectedColor = selectedColor
        )
        
        // Auto start game when opponent joins (simplified for demo)
        LaunchedEffect(createdRoomCode) {
            if (createdRoomCode != null) {
                Log.d("OnlineDemo", "Setting currentRoomCode immediately: $createdRoomCode")
                currentRoomCode = createdRoomCode
                playerColor = if (selectedColor == "white") Side.WHITE else Side.BLACK
                
                kotlinx.coroutines.delay(5000) // Give more time for socket setup
                Log.d("OnlineDemo", "Starting game with roomCode: $createdRoomCode, color: $selectedColor")
                gameStarted = true
                showCreateRoomModal = false
            }
        }
    }
    
    // Join Room Modal
    if (showJoinRoomModal) {
        JoinRoomModal(
            onDismiss = { showJoinRoomModal = false },
            onJoinRoom = { roomCode ->
                Log.d("OnlineDemo", "Joined room: $roomCode")
                showJoinRoomModal = false
                currentRoomCode = roomCode
                // Joiner gets opposite color of room creator
                playerColor = Side.BLACK // Assume creator is white for demo
                gameStarted = true
            }
        )
    }
}
