package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.nearby.NearbyConnectionsManager
import com.example.chess.utils.NotificationHelper
import com.example.chess.utils.P2PNotificationHandler

/**
 * Màn hình Create Game - advertising mode
 */
@Composable
fun NearbyCreateGameScreen(
    nearbyManager: NearbyConnectionsManager,
    onBack: () -> Unit,
    onGameStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by nearbyManager.connectionState.collectAsState()
    
    var playerName by remember { mutableStateOf("Player 1") }
    var isAdvertising by remember { mutableStateOf(false) }
    
    // Start advertising when screen opens
    LaunchedEffect(Unit) {
        nearbyManager.startAdvertising(playerName).collect { success ->
            if (success) {
                isAdvertising = true
            } else {
                NotificationHelper.showError(context, "Failed to start advertising")
            }
        }
    }
    
    // Handle connection state changes
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is NearbyConnectionsManager.ConnectionState.Connected -> {
                NotificationHelper.showGameStarting(context)
                kotlinx.coroutines.delay(1500) // Brief delay to show the message
                onGameStart()
            }
            else -> {}
        }
    }
    
    // Handle P2P notifications
    P2PNotificationHandler(connectionState, context)
    
    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            nearbyManager.stopAdvertising()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
    ) {
        // Back button
        IconButton(
            onClick = {
                nearbyManager.stopAdvertising()
                onBack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Game",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1F)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (connectionState) {
                        is NearbyConnectionsManager.ConnectionState.Advertising -> {
                            CircularProgressIndicator(
                                color = Color(0xFF2ECC71),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Waiting for players...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Your device is discoverable as \"$playerName\"",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        is NearbyConnectionsManager.ConnectionState.Connecting -> {
                            CircularProgressIndicator(
                                color = Color(0xFF3498DB),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Connecting...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text(
                                text = "A player is trying to join your game",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        is NearbyConnectionsManager.ConnectionState.Connected -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Connected",
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Connected!",
                                color = Color(0xFF2ECC71),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text(
                                text = "Starting game...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        is NearbyConnectionsManager.ConnectionState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFE74C3C),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Connection Error",
                                color = Color(0xFFE74C3C),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text(
                                text = (connectionState as NearbyConnectionsManager.ConnectionState.Error).message,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    nearbyManager.startAdvertising(playerName)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2ECC71)
                                )
                            ) {
                                Text("Try Again")
                            }
                        }
                        
                        else -> {
                            CircularProgressIndicator(
                                color = Color(0xFF95A5A6),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Starting...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1F).copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instructions:",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• Make sure both devices have Bluetooth and Wi-Fi enabled\n" +
                              "• Other players can find your game by selecting \"Join Game\"\n" +
                              "• The game will start automatically when someone connects",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}