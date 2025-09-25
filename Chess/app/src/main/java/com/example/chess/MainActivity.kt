package com.example.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.chess.model.initialBitboards
import com.example.chess.model.Side
import com.example.chess.ui.ChessBoardBitboard
import com.example.chess.ui.MenuScreen
import com.example.chess.ui.WatchGameScreen
import com.example.chess.ui.OnlinePlayModal
import com.example.chess.ui.CreateRoomModal
import com.example.chess.ui.JoinRoomModal
import com.example.chess.ui.ColorSelectionModal

sealed class Screen {
    data object Menu : Screen()
    data class Game(val playerColor: Side? = null, val isOnlineMode: Boolean = false, val roomCode: String? = null) : Screen()
    data object Watch : Screen()
}

sealed class ModalState {
    data object None : ModalState()
    data object OnlinePlay : ModalState()
    data object ColorSelection : ModalState()
    data class CreateRoom(val selectedColor: String, val roomCode: String) : ModalState()
    data object JoinRoom : ModalState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                    var modalState by remember { mutableStateOf<ModalState>(ModalState.None) }
                    var currentRoomCode by remember { mutableStateOf<String?>(null) }

                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val animationDuration = 400
                            val slideDirection = when (targetState) {
                                Screen.Menu -> -1 // Slide in from left when going back to menu
                                is Screen.Game, Screen.Watch -> 1 // Slide in from right when going forward
                            }
                            
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> slideDirection * fullWidth },
                                animationSpec = tween(animationDuration)
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -slideDirection * fullWidth },
                                animationSpec = tween(animationDuration)
                            )
                        },
                        label = "ScreenTransition"
                    ) { currentScreen ->
                        when (currentScreen) {
                            Screen.Menu -> MenuScreen(
                                onGetStarted = { screen = Screen.Game() }, // Offline mode
                                onWatchGame = { screen = Screen.Watch },
                                onPlayOnline = { modalState = ModalState.OnlinePlay }
                            )
                            is Screen.Game -> ChessBoardBitboard(
                                initial = initialBitboards(),
                                onBack = { screen = Screen.Menu },
                                playerColor = currentScreen.playerColor,
                                isOnlineMode = currentScreen.isOnlineMode,
                                roomCode = currentScreen.roomCode,
                                modifier = Modifier.fillMaxSize()
                            )
                            Screen.Watch -> WatchGameScreen(
                                onBack = { screen = Screen.Menu }
                            )
                        }
                    }

                    // Handle modals
                    when (modalState) {
                        ModalState.OnlinePlay -> OnlinePlayModal(
                            onDismiss = { modalState = ModalState.None },
                            onCreateRoom = { modalState = ModalState.ColorSelection },
                            onJoinRoom = { modalState = ModalState.JoinRoom },
                            onRoomCreated = { roomCode -> 
                                currentRoomCode = roomCode
                            }
                        )
                        ModalState.ColorSelection -> ColorSelectionModal(
                            onDismiss = { modalState = ModalState.None },
                            onColorSelected = { selectedColor ->
                                currentRoomCode?.let { roomCode ->
                                    modalState = ModalState.CreateRoom(selectedColor, roomCode)
                                }
                            }
                        )
                        is ModalState.CreateRoom -> {
                            val currentModalState = modalState as ModalState.CreateRoom
                            CreateRoomModal(
                                roomCode = currentModalState.roomCode,
                                selectedColor = currentModalState.selectedColor,
                                onDismiss = { 
                                    // Save selected color before changing modalState
                                    val selectedColor = currentModalState.selectedColor
                                    modalState = ModalState.None
                                    // Start online game with selected color
                                    val playerSide = if (selectedColor == "white") Side.WHITE else Side.BLACK
                                    screen = Screen.Game(playerColor = playerSide, isOnlineMode = true, roomCode = currentModalState.roomCode)
                                }
                            )
                        }
                        ModalState.JoinRoom -> JoinRoomModal(
                            onDismiss = { modalState = ModalState.None },
                            onJoinRoom = { roomCode ->
                                // Persist joined room code and start online game
                                currentRoomCode = roomCode
                                modalState = ModalState.None
                                // Assume joining player gets the opposite color (black by default for now)
                                screen = Screen.Game(playerColor = Side.BLACK, isOnlineMode = true, roomCode = roomCode)
                            }
                        )
                        ModalState.None -> {}
                    }
                }
            }
        }
    }
}
