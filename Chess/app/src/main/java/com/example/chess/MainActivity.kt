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
import com.example.chess.network.ChessApiService
import com.example.chess.network.SocketService
import com.example.chess.ui.AiPlayModal
import com.example.chess.ui.ChessBoardBitboard
import com.example.chess.ui.MenuScreen
import com.example.chess.ui.WatchGameScreen
import com.example.chess.ui.OnlinePlayModal
import com.example.chess.ui.CreateRoomModal
import com.example.chess.ui.JoinRoomModal
import com.example.chess.ui.ColorSelectionModal
import com.example.chess.ui.NearbyModeSelectionScreen
import com.example.chess.ui.NearbyCreateGameScreen
import com.example.chess.ui.NearbyJoinGameScreen
import com.example.chess.nearby.NearbyConnectionsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private const val DEFAULT_AI_LEVEL = 10
private const val DEFAULT_AI_THINK_TIME_MS = 500

sealed class Screen {
    data object Menu : Screen()
    data class Game(
        val playerColor: Side? = null,
        val isOnlineMode: Boolean = false,
        val roomCode: String? = null,
        val isAiMode: Boolean = false,
        val aiLevel: Int = DEFAULT_AI_LEVEL,
        val aiThinkTimeMs: Int = DEFAULT_AI_THINK_TIME_MS,
        val isNearbyMode: Boolean = false
    ) : Screen()
    data object Watch : Screen()
    data object NearbyModeSelection : Screen()
    data object NearbyCreateGame : Screen()
    data object NearbyJoinGame : Screen()
}

sealed class ModalState {
    data object None : ModalState()
    data object OnlinePlay : ModalState()
    data object ColorSelection : ModalState()
    data class CreateRoom(val selectedColor: String, val roomCode: String) : ModalState()
    data object JoinRoom : ModalState()
    data class AiSetup(
        val isProcessing: Boolean = false,
        val errorMessage: String? = null
    ) : ModalState()
}

data class AiGameConfig(
    val color: Side,
    val level: Int,
    val thinkTimeMs: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val apiService = remember { ChessApiService.create() }
                    val socketService = remember { SocketService.getInstance() }
                    val nearbyManager = remember { NearbyConnectionsManager(this@MainActivity) }
                    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                    
                    // Cleanup nearby manager when activity is destroyed
                    DisposableEffect(nearbyManager) {
                        onDispose {
                            nearbyManager.cleanup()
                        }
                    }
                    var modalState by remember { mutableStateOf<ModalState>(ModalState.None) }
                    var currentRoomCode by remember { mutableStateOf<String?>(null) }
                    var pendingAiConfig by remember { mutableStateOf<AiGameConfig?>(null) }

                    LaunchedEffect(pendingAiConfig) {
                        val config = pendingAiConfig ?: return@LaunchedEffect
                        try {
                            modalState = ModalState.AiSetup(isProcessing = true)

                            if (!socketService.isConnected()) {
                                socketService.connect()
                            }

                            val connected = withTimeoutOrNull(5_000) {
                                while (!socketService.isConnected()) {
                                    delay(100)
                                }
                                true
                            }

                            if (connected != true) {
                                throw IllegalStateException("Unable to connect to chess server")
                            }

                            val response = apiService.createRoom()
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Failed to create room (${response.code()})")
                            }
                            val roomCode = response.body()?.code ?: throw IllegalStateException("Server returned empty room code")
                            currentRoomCode = roomCode

                            val joinAck = CompletableDeferred<Unit>()
                            socketService.setOnRoomJoinedCallback { code, _ ->
                                if (code.equals(roomCode, ignoreCase = true) && !joinAck.isCompleted) {
                                    joinAck.complete(Unit)
                                }
                            }

                            val uid = "ai_${System.currentTimeMillis()}"
                            socketService.joinRoom(roomCode, uid)

                            withTimeout(5_000) {
                                joinAck.await()
                            }

                            socketService.setOnRoomJoinedCallback(null)

                            modalState = ModalState.None
                            pendingAiConfig = null
                            screen = Screen.Game(
                                playerColor = config.color,
                                isOnlineMode = true,
                                roomCode = roomCode,
                                isAiMode = true,
                                aiLevel = config.level,
                                aiThinkTimeMs = config.thinkTimeMs
                            )
                        } catch (e: Exception) {
                            socketService.setOnRoomJoinedCallback(null)
                            pendingAiConfig = null
                            modalState = ModalState.AiSetup(
                                isProcessing = false,
                                errorMessage = e.message ?: "Unable to start AI game"
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val animationDuration = 400
                            fun screenOrder(screen: Screen): Int = when (screen) {
                                Screen.Menu -> 0
                                Screen.NearbyModeSelection -> 1
                                Screen.NearbyCreateGame, Screen.NearbyJoinGame -> 2
                                Screen.Watch -> 2
                                is Screen.Game -> 3
                            }

                            val initialOrder = screenOrder(initialState)
                            val targetOrder = screenOrder(targetState)
                            val slideDirection = if (targetOrder >= initialOrder) 1 else -1
                            
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
                                onPlayOnline = { modalState = ModalState.OnlinePlay },
                                onPlayVsAi = { modalState = ModalState.AiSetup() },
                                onPlayNearby = { screen = Screen.NearbyModeSelection }
                            )
                            is Screen.Game -> ChessBoardBitboard(
                                initial = initialBitboards(),
                                onBack = { screen = Screen.Menu },
                                playerColor = currentScreen.playerColor,
                                isOnlineMode = currentScreen.isOnlineMode,
                                roomCode = currentScreen.roomCode,
                                isAiMode = currentScreen.isAiMode,
                                aiLevel = currentScreen.aiLevel,
                                aiThinkTimeMs = currentScreen.aiThinkTimeMs,
                                isNearbyMode = currentScreen.isNearbyMode,
                                nearbyManager = if (currentScreen.isNearbyMode) nearbyManager else null,
                                modifier = Modifier.fillMaxSize()
                            )
                            Screen.Watch -> WatchGameScreen(
                                onBack = { screen = Screen.Menu }
                            )
                            Screen.NearbyModeSelection -> NearbyModeSelectionScreen(
                                onBack = { screen = Screen.Menu },
                                onCreateGame = { screen = Screen.NearbyCreateGame },
                                onJoinGame = { screen = Screen.NearbyJoinGame }
                            )
                            Screen.NearbyCreateGame -> NearbyCreateGameScreen(
                                nearbyManager = nearbyManager,
                                onBack = { screen = Screen.NearbyModeSelection },
                                onGameStart = { screen = Screen.Game(isNearbyMode = true) }
                            )
                            Screen.NearbyJoinGame -> NearbyJoinGameScreen(
                                nearbyManager = nearbyManager,
                                onBack = { screen = Screen.NearbyModeSelection },
                                onGameStart = { screen = Screen.Game(isNearbyMode = true) }
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
                        is ModalState.AiSetup -> {
                            val state = modalState as ModalState.AiSetup
                            AiPlayModal(
                                isProcessing = state.isProcessing,
                                errorMessage = state.errorMessage,
                                onDismiss = { if (!state.isProcessing) modalState = ModalState.None },
                                onStartGame = { selectedSide, aiLevel, thinkTimeMs ->
                                    if (!state.isProcessing) {
                                        modalState = ModalState.AiSetup(isProcessing = true)
                                        pendingAiConfig = AiGameConfig(
                                            color = selectedSide,
                                            level = aiLevel,
                                            thinkTimeMs = thinkTimeMs
                                        )
                                    }
                                }
                            )
                        }
                        ModalState.None -> {}
                    }
                }
            }
        }
    }
}
