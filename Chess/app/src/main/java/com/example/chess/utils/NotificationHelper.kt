package com.example.chess.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope

/**
 * Helper class for managing notifications in P2P chess game
 */
object NotificationHelper {
    
    /**
     * Show toast message
     */
    fun showToast(context: Context, message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, length).show()
    }
    
    /**
     * Show connection success toast
     */
    fun showConnectionSuccess(context: Context, deviceName: String = "opponent") {
        showToast(context, "Connected to $deviceName successfully!", Toast.LENGTH_SHORT)
    }
    
    /**
     * Show connection lost toast
     */
    fun showConnectionLost(context: Context, reason: String? = null) {
        val message = if (reason != null) {
            "Connection lost: $reason"
        } else {
            "Connection lost"
        }
        showToast(context, message, Toast.LENGTH_LONG)
    }
    
    /**
     * Show connection timeout toast
     */
    fun showConnectionTimeout(context: Context) {
        showToast(context, "Connection timed out. Please try again.", Toast.LENGTH_LONG)
    }
    
    /**
     * Show advertising started toast
     */
    fun showAdvertisingStarted(context: Context) {
        showToast(context, "Waiting for players to join...", Toast.LENGTH_SHORT)
    }
    
    /**
     * Show discovery started toast
     */
    fun showDiscoveryStarted(context: Context) {
        showToast(context, "Searching for nearby games...", Toast.LENGTH_SHORT)
    }
    
    /**
     * Show error toast
     */
    fun showError(context: Context, error: String) {
        showToast(context, "Error: $error", Toast.LENGTH_LONG)
    }
    
    /**
     * Show game starting toast
     */
    fun showGameStarting(context: Context) {
        showToast(context, "Game starting...", Toast.LENGTH_SHORT)
    }
    
    /**
     * Show move received toast (optional, for debugging)
     */
    fun showMoveReceived(context: Context, enable: Boolean = false) {
        if (enable) {
            showToast(context, "Opponent made a move", Toast.LENGTH_SHORT)
        }
    }
}

/**
 * Composable function to handle P2P notifications
 */
@Composable
fun P2PNotificationHandler(
    connectionState: com.example.chess.nearby.NearbyConnectionsManager.ConnectionState,
    context: Context,
    prevConnectionState: MutableState<com.example.chess.nearby.NearbyConnectionsManager.ConnectionState?> = remember { mutableStateOf(null) }
) {
    LaunchedEffect(connectionState) {
        val prevState = prevConnectionState.value
        
        when (connectionState) {
            is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Advertising -> {
                if (prevState != connectionState) {
                    NotificationHelper.showAdvertisingStarted(context)
                }
            }
            
            is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Discovering -> {
                if (prevState != connectionState) {
                    NotificationHelper.showDiscoveryStarted(context)
                }
            }
            
            is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Connected -> {
                if (prevState !is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Connected) {
                    NotificationHelper.showConnectionSuccess(context, connectionState.deviceName)
                }
            }
            
            is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Error -> {
                if (prevState != connectionState) {
                    NotificationHelper.showError(context, connectionState.message)
                }
            }
            
            com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Disconnected -> {
                if (prevState is com.example.chess.nearby.NearbyConnectionsManager.ConnectionState.Connected) {
                    NotificationHelper.showConnectionLost(context)
                }
            }
            
            else -> {}
        }
        
        prevConnectionState.value = connectionState
    }
}