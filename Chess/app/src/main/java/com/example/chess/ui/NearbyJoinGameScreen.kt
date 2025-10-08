package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhoneAndroid
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
import com.example.chess.model.TimeControl
import com.example.chess.nearby.NearbyConnectionsManager
import com.example.chess.utils.NotificationHelper
import com.example.chess.utils.P2PNotificationHandler

/**
 * Màn hình Join Game - discovery mode
 */
@Composable
fun NearbyJoinGameScreen(
    nearbyManager: NearbyConnectionsManager,
    onBack: () -> Unit,
    onGameStart: (TimeControl) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by nearbyManager.connectionState.collectAsState()
    val discoveredDevices by nearbyManager.discoveredDevices.collectAsState()
    
    var playerName by remember { mutableStateOf("Player 2") }
    var isDiscovering by remember { mutableStateOf(false) }
    var connectingToDevice by remember { mutableStateOf<String?>(null) }
    var selectedTimeControl by remember { mutableStateOf(TimeControl.FIVE_MINUTES) }
    
    // Start discovery when screen opens
    LaunchedEffect(Unit) {
        nearbyManager.startDiscovery().collect { success ->
            if (success) {
                isDiscovering = true
            } else {
                NotificationHelper.showError(context, "Failed to start discovery")
            }
        }
    }
    
    // Handle connection state changes
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is NearbyConnectionsManager.ConnectionState.Connected -> {
                NotificationHelper.showGameStarting(context)
                kotlinx.coroutines.delay(1500) // Brief delay to show the message
                onGameStart(selectedTimeControl)
            }
            is NearbyConnectionsManager.ConnectionState.Error -> {
                connectingToDevice = null
            }
            NearbyConnectionsManager.ConnectionState.Disconnected -> {
                connectingToDevice = null
            }
            else -> {}
        }
    }
    
    // Handle P2P notifications
    P2PNotificationHandler(connectionState, context)
    
    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            nearbyManager.stopDiscovery()
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
                nearbyManager.stopDiscovery()
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
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(56.dp)) // Space for back button
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Join Game",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = {
                        nearbyManager.stopDiscovery()
                        nearbyManager.startDiscovery()
                    },
                    enabled = connectionState != NearbyConnectionsManager.ConnectionState.Connecting
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF3498DB),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status indicator
            when (connectionState) {
                is NearbyConnectionsManager.ConnectionState.Discovering -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3498DB),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Searching for nearby games...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
                
                is NearbyConnectionsManager.ConnectionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2ECC71),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Connecting to ${connectingToDevice}...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
                
                is NearbyConnectionsManager.ConnectionState.Connected -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Connected! Starting game...",
                            color = Color(0xFF2ECC71),
                            fontSize = 14.sp
                        )
                    }
                }
                
                is NearbyConnectionsManager.ConnectionState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFE74C3C),
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = (connectionState as NearbyConnectionsManager.ConnectionState.Error).message,
                            color = Color(0xFFE74C3C),
                            fontSize = 14.sp
                        )
                    }
                }
                
                else -> {}
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Time Control Selection (similar to Create screen)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1F)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Time Control",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimeControlChip("1m", TimeControl.ONE_MINUTE, selectedTimeControl) { selectedTimeControl = it }
                        TimeControlChip("3m", TimeControl.THREE_MINUTES, selectedTimeControl) { selectedTimeControl = it }
                        TimeControlChip("5m", TimeControl.FIVE_MINUTES, selectedTimeControl) { selectedTimeControl = it }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimeControlChip("10m", TimeControl.TEN_MINUTES, selectedTimeControl) { selectedTimeControl = it }
                        TimeControlChip("30m", TimeControl.THIRTY_MINUTES, selectedTimeControl) { selectedTimeControl = it }
                        TimeControlChip("∞", TimeControl.NO_LIMIT, selectedTimeControl) { selectedTimeControl = it }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Devices list
            if (discoveredDevices.isEmpty() && connectionState is NearbyConnectionsManager.ConnectionState.Discovering) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1F)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3498DB),
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Looking for games...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "Make sure the other device has created a game",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDevices) { device ->
                        DeviceItem(
                            device = device,
                            isConnecting = connectingToDevice == device.name,
                            isEnabled = connectionState != NearbyConnectionsManager.ConnectionState.Connecting,
                            onClick = {
                                connectingToDevice = device.name
                                nearbyManager.connectToDevice(device, playerName)
                            }
                        )
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
                        text = "Tip:",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "If you don't see any games, make sure:\n" +
                              "• Both devices have Bluetooth and Wi-Fi enabled\n" +
                              "• You're within range of the other device\n" +
                              "• The other player has created a game",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: NearbyConnectionsManager.NearbyDevice,
    isConnecting: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1F)
        ),
        onClick = onClick,
        enabled = isEnabled && !isConnecting
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = "Device",
                tint = Color(0xFF3498DB),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Chess Game Available",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
            
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color(0xFF2ECC71),
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun RowScope.TimeControlChip(
    label: String,
    timeControl: TimeControl,
    selected: TimeControl,
    onSelect: (TimeControl) -> Unit
) {
    Button(
        onClick = { onSelect(timeControl) },
        modifier = Modifier
            .weight(1f)
            .height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == timeControl) Color(0xFF2ECC71) else Color(0xFF2A2A2A),
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected == timeControl) FontWeight.Bold else FontWeight.Normal
        )
    }
}