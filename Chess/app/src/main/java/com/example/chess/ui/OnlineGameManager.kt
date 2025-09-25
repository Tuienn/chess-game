package com.example.chess.ui

import androidx.compose.runtime.*
import com.example.chess.model.Side
import com.example.chess.network.SocketService

/**
 * Data class để lưu trạng thái online game
 */
data class OnlineGameState(
    val roomCode: String? = null,
    val playerColor: Side? = null,
    val isConnected: Boolean = false,
    val opponentJoined: Boolean = false,
    val isMyTurn: Boolean = false
)

/**
 * Composable helper để quản lý trạng thái online game
 */
@Composable
fun rememberOnlineGameManager(): OnlineGameManager {
    return remember { OnlineGameManager() }
}

class OnlineGameManager {
    private val socketService = SocketService.getInstance()
    
    var gameState by mutableStateOf(OnlineGameState())
        private set
    
    fun connectToRoom(roomCode: String, playerColor: String) {
        gameState = gameState.copy(
            roomCode = roomCode,
            playerColor = if (playerColor == "white") Side.WHITE else Side.BLACK,
            isMyTurn = playerColor == "white" // White goes first
        )
        
        // Setup socket listeners
        socketService.setOnRoomJoinedCallback { joinedRoomCode, joinedPlayerColor ->
            gameState = gameState.copy(
                isConnected = true,
                playerColor = if (joinedPlayerColor == "white") Side.WHITE else Side.BLACK,
                isMyTurn = joinedPlayerColor == "white"
            )
        }
        
        socketService.setOnOpponentJoinedCallback {
            gameState = gameState.copy(opponentJoined = true)
        }
        
        socketService.setOnMoveReceivedCallback { _ ->
            // Toggle turn when receiving a move
            gameState = gameState.copy(isMyTurn = !gameState.isMyTurn)
        }
    }
    
    fun onMoveSent() {
        // Toggle turn when sending a move
        gameState = gameState.copy(isMyTurn = !gameState.isMyTurn)
    }
    
    fun disconnect() {
        socketService.disconnect()
        gameState = OnlineGameState()
    }
}
