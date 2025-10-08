package com.example.chess.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.chess.audio.rememberChessSoundManager
import com.example.chess.model.*
import com.example.chess.network.SocketService
import com.example.chess.utils.NotificationHelper

/** Kiểm tra nước đi có phải tốt lên hàng cuối không */
private fun needsPromotionForMove(state: GameState, move: Move): Boolean {
    val who = pieceAt(state.boards, move.from) ?: return false
    val (side, type) = who
    if (type != 'P') return false
    val (rTo, _) = rowColFromIndex(move.to)
    return (side == Side.WHITE && rTo == 0) || (side == Side.BLACK && rTo == 7)
}

/* ============================================================================
 *  API #1: Nhận Bitboards ban đầu
 * ==========================================================================*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessBoardBitboard(
    initial: Bitboards,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerColor: Side? = null, // null = both sides can move (offline)
    isOnlineMode: Boolean = false,
    roomCode: String? = null,
    isAiMode: Boolean = false,
    aiLevel: Int = 10,
    aiThinkTimeMs: Int = 500,
    isNearbyMode: Boolean = false,
    nearbyManager: com.example.chess.nearby.NearbyConnectionsManager? = null,
    timeControl: TimeControl? = null
) {
    var gameState by remember {
        mutableStateOf(GameState(boards = initial, sideToMove = Side.WHITE))
    }
    ChessBoardBitboardImpl(
        gameState = gameState,
        onBack = onBack,
        modifier = modifier,
        playerColor = playerColor,
        isOnlineMode = isOnlineMode,
        roomCode = roomCode,
        isAiMode = isAiMode,
        aiLevel = aiLevel,
        aiThinkTimeMs = aiThinkTimeMs,
        isNearbyMode = isNearbyMode,
        nearbyManager = nearbyManager,
        timeControl = timeControl
    ) { gameState = it }
}

/* ============================================================================
 *  API #2: Nhận GameState ban đầu (testing/khôi phục)
 * ==========================================================================*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessBoardBitboard(
    initialState: GameState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerColor: Side? = null,
    isOnlineMode: Boolean = false,
    roomCode: String? = null,
    isAiMode: Boolean = false,
    aiLevel: Int = 10,
    aiThinkTimeMs: Int = 500,
    isNearbyMode: Boolean = false,
    nearbyManager: com.example.chess.nearby.NearbyConnectionsManager? = null,
    timeControl: TimeControl? = null
) {
    var gameState by remember { mutableStateOf(initialState) }
    ChessBoardBitboardImpl(
        gameState = gameState,
        onBack = onBack,
        modifier = modifier,
        playerColor = playerColor,
        isOnlineMode = isOnlineMode,
        roomCode = roomCode,
        isAiMode = isAiMode,
        aiLevel = aiLevel,
        aiThinkTimeMs = aiThinkTimeMs,
        isNearbyMode = isNearbyMode,
        nearbyManager = nearbyManager,
        timeControl = timeControl
    ) { gameState = it }
}

/* ============================================================================
 *  Impl
 * ==========================================================================*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChessBoardBitboardImpl(
    gameState: GameState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerColor: Side? = null,
    isOnlineMode: Boolean = false,
    roomCode: String? = null,
    isAiMode: Boolean = false,
    aiLevel: Int = 10,
    aiThinkTimeMs: Int = 500,
    isNearbyMode: Boolean = false,
    nearbyManager: com.example.chess.nearby.NearbyConnectionsManager? = null,
    timeControl: TimeControl? = null,
    onGameStateChange: (GameState) -> Unit
) {
    val context = LocalContext.current
    val soundManager = rememberChessSoundManager(context)
    var selected by remember { mutableStateOf<Int?>(null) }
    var availableMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    val latestGameState = rememberUpdatedState(gameState)
    val latestOnGameStateChange = rememberUpdatedState(onGameStateChange)
    
    // Timer state
    val timerEnabled = timeControl?.enabled == true
    var playerTimers by remember(timeControl) {
        mutableStateOf(
            if (timerEnabled && timeControl != null) {
                PlayerTimers(
                    whiteRemainingMs = timeControl.totalTimeMs,
                    blackRemainingMs = timeControl.totalTimeMs
                )
            } else null
        )
    }
    var gameEndedOnTime by remember { mutableStateOf<Side?>(null) }

    // Socket service (online)
    val socketService = remember { if (isOnlineMode) SocketService.getInstance() else null }
    val isAiGame = isOnlineMode && isAiMode
    var aiFirstMoveRequested by remember(roomCode) { mutableStateOf(false) }

    // Debug socket init
    LaunchedEffect(isOnlineMode, roomCode, isAiGame) {
        Log.d(
            "ChessBoard",
            "ChessBoard init: isOnlineMode=$isOnlineMode, isAiGame=$isAiGame, roomCode=$roomCode, socketService=${socketService != null}"
        )
        if (socketService != null) {
            Log.d("ChessBoard", "Socket connected: ${socketService.isConnected()}")
        }
    }

    // Socket listeners
    LaunchedEffect(socketService, isOnlineMode, isAiGame) {
        if (isOnlineMode && socketService != null) {
            if (!socketService.isConnected()) {
                Log.d("ChessBoard", "Socket not connected, connecting...")
                socketService.connect()
                kotlinx.coroutines.delay(2000)
            }

            if (isAiGame) {
                socketService.setOnMoveReceivedCallback(null)
                socketService.setOnAiMoveCallback { result ->
                    result.aiMove?.let { aiMove ->
                        Log.d("ChessBoard", "AI move applied: ${aiMove.from} -> ${aiMove.to}")
                        val currentState = latestGameState.value
                        val status = getGameStatus(currentState)
                        val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                        val updatedState = applyMove(currentState, aiMove, soundManager, isGameEnd)
                        latestOnGameStateChange.value(updatedState)
                    }
                    result.gameOverResult?.let { outcome ->
                        Log.d("ChessBoard", "Game over vs AI: $outcome")
                    }
                }
            } else {
                socketService.setOnAiMoveCallback(null)
                socketService.setOnMoveReceivedCallback { receivedMove ->
                    Log.d("ChessBoard", "Received move from opponent: ${receivedMove.from} -> ${receivedMove.to}")
                    val currentState = latestGameState.value
                    val status = getGameStatus(currentState)
                    val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                    val updatedState = applyMove(currentState, receivedMove, soundManager, isGameEnd)
                    latestOnGameStateChange.value(updatedState)
                }
            }
            
            // Timer sync callback
            socketService.setOnTimerUpdateCallback { whiteMs, blackMs ->
                if (timerEnabled && whiteMs != null && blackMs != null) {
                    playerTimers = PlayerTimers(
                        whiteRemainingMs = whiteMs,
                        blackRemainingMs = blackMs,
                        lastUpdateTimestamp = System.currentTimeMillis()
                    )
                    Log.d("ChessBoard", "Timer synced: white=$whiteMs, black=$blackMs")
                }
            }
        }
    }
    DisposableEffect(socketService, isOnlineMode) {
        onDispose {
            if (isOnlineMode && socketService != null) {
                socketService.setOnMoveReceivedCallback(null)
                socketService.setOnAiMoveCallback(null)
                socketService.setOnTimerUpdateCallback(null)
            }
        }
    }

    // Nearby P2P
    LaunchedEffect(isNearbyMode, nearbyManager) {
        if (isNearbyMode && nearbyManager != null) {
            Log.d("ChessBoard", "Setting up P2P callbacks")

            nearbyManager.setOnMoveReceivedCallback { move ->
                Log.d("ChessBoard", "P2P move received: ${move.from} -> ${move.to}")
                val currentState = latestGameState.value
                val status = getGameStatus(currentState)
                val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                val updatedState = applyMove(currentState, move, soundManager, isGameEnd)
                latestOnGameStateChange.value(updatedState)
                // Optional notification:
                // NotificationHelper.showMoveReceived(context, enable = false)
            }

            nearbyManager.setOnConnectionStatusChangedCallback { isConnected, message ->
                if (!isConnected) {
                    Log.d("ChessBoard", "P2P connection lost: $message")
                    NotificationHelper.showConnectionLost(context, message)
                }
            }
            
            nearbyManager.setOnTimerUpdateCallback { whiteMs, blackMs ->
                if (timerEnabled) {
                    playerTimers = PlayerTimers(
                        whiteRemainingMs = whiteMs,
                        blackRemainingMs = blackMs,
                        lastUpdateTimestamp = System.currentTimeMillis()
                    )
                    Log.d("ChessBoard", "P2P timer synced: white=$whiteMs, black=$blackMs")
                }
            }
        }
    }
    DisposableEffect(nearbyManager, isNearbyMode) {
        onDispose {
            if (isNearbyMode && nearbyManager != null) {
                nearbyManager.setOnMoveReceivedCallback(null)
                nearbyManager.setOnConnectionStatusChangedCallback(null)
                nearbyManager.setOnTimerUpdateCallback(null)
            }
        }
    }

    // Timer countdown
    LaunchedEffect(timerEnabled, gameState.sideToMove, gameEndedOnTime) {
        if (timerEnabled && playerTimers != null && gameEndedOnTime == null) {
            val status = getGameStatus(gameState)
            if (status != GameStatus.CHECKMATE && status != GameStatus.STALEMATE) {
                while (playerTimers != null && gameEndedOnTime == null) {
                    kotlinx.coroutines.delay(100) // Update every 100ms
                    
                    val currentTimers = playerTimers ?: break
                    val currentTime = System.currentTimeMillis()
                    val elapsed = currentTime - currentTimers.lastUpdateTimestamp

                    playerTimers = if (gameState.sideToMove == Side.WHITE) {
                        val newWhiteTime = (currentTimers.whiteRemainingMs - elapsed).coerceAtLeast(0)
                        if (newWhiteTime <= 0 && gameEndedOnTime == null) {
                            gameEndedOnTime = Side.BLACK // Black wins
                        }
                        currentTimers.copy(
                            whiteRemainingMs = newWhiteTime,
                            lastUpdateTimestamp = currentTime
                        )
                    } else {
                        val newBlackTime = (currentTimers.blackRemainingMs - elapsed).coerceAtLeast(0)
                        if (newBlackTime <= 0 && gameEndedOnTime == null) {
                            gameEndedOnTime = Side.WHITE // White wins
                        }
                        currentTimers.copy(
                            blackRemainingMs = newBlackTime,
                            lastUpdateTimestamp = currentTime
                        )
                    }
                }
            }
        }
    }

    // AI opening move (nếu AI chơi trắng)
    LaunchedEffect(isAiGame, playerColor, roomCode, socketService, aiLevel, aiThinkTimeMs) {
        if (isAiGame && playerColor == Side.BLACK && roomCode != null && socketService != null && !aiFirstMoveRequested) {
            if (!socketService.isConnected()) {
                socketService.connect()
                kotlinx.coroutines.delay(500)
            }
            var attempts = 0
            while (!socketService.isConnected() && attempts < 30) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            if (socketService.isConnected()) {
                Log.d("ChessBoard", "Requesting AI opening move for room=$roomCode")
                socketService.requestAiMove(roomCode, aiLevel, aiThinkTimeMs)
                aiFirstMoveRequested = true
            }
        }
    }

    // Xoay bàn khi online hoặc nearby & bạn là đen
    val shouldRotateBoard = (isOnlineMode || isNearbyMode) && playerColor == Side.BLACK

    fun rotateRowCol(r: Int, c: Int): Pair<Int, Int> =
        if (shouldRotateBoard) Pair(7 - r, 7 - c) else Pair(r, c)

    fun rotateIndex(idx: Int): Int {
        val (r, c) = rowColFromIndex(idx)
        val (newR, newC) = rotateRowCol(r, c)
        return indexFromRowCol(newR, newC)
    }

    // Animation state
    var animatingPiece by remember { mutableStateOf<Triple<String, Int, Int>?>(null) } // (pieceCode, fromIdx, toIdx)
    var pendingMove by remember { mutableStateOf<Move?>(null) }

    // Promotion state
    var pendingPromotionMove by remember { mutableStateOf<Move?>(null) }
    var showPromotionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val animationProgress = animateFloatAsState(
        targetValue = if (animatingPiece != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        finishedListener = { animatingPiece = null }
    )

    // Áp dụng pendingMove sau khi anim xong
    LaunchedEffect(pendingMove) {
        pendingMove?.let { move ->
            kotlinx.coroutines.delay(300) // chờ animation
            val current = latestGameState.value

            if (needsPromotionForMove(current, move)) {
                pendingPromotionMove = move
                showPromotionSheet = true
                pendingMove = null
            } else {
                val status = getGameStatus(current)
                val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                val updated = applyMove(current, move, soundManager, isGameEnd)
                latestOnGameStateChange.value(updated)

                // Online: gửi move
                Log.d(
                    "ChessBoard",
                    "Move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}"
                )
                if (isOnlineMode && socketService != null && roomCode != null) {
                    if (socketService.isConnected()) {
                        val promo = move.promo?.toString()
                        if (isAiGame) {
                            socketService.sendMoveVsAi(roomCode, move.from, move.to, promo, aiLevel, aiThinkTimeMs)
                            Log.d("ChessBoard", "Sent move_vs_ai: ${move.from} -> ${move.to}, promo=$promo")
                        } else {
                            socketService.sendMove(roomCode, move.from, move.to, promo)
                            Log.d("ChessBoard", "Sent move to server: ${move.from} -> ${move.to}")
                        }
                    } else {
                        Log.w("ChessBoard", "Socket not connected, cannot send move")
                    }
                }

                // P2P Nearby: gửi move
                if (isNearbyMode && nearbyManager != null) {
                    nearbyManager.sendMove(move)
                    Log.d("ChessBoard", "Sent P2P move: ${move.from} -> ${move.to}")
                    
                    // Send timer update after move
                    playerTimers?.let { timers ->
                        nearbyManager.sendTimerUpdate(
                            timers.whiteRemainingMs,
                            timers.blackRemainingMs
                        )
                    }
                }

                pendingMove = null
            }
        }
    }

    // Áp dụng move phong cấp
    val applyPromotionMove: (Char) -> Unit = { promotionPiece ->
        pendingPromotionMove?.let { move ->
            val promotionMove = move.copy(promo = promotionPiece)
            val current = latestGameState.value
            val status = getGameStatus(current)
            val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
            val updated = applyMove(current, promotionMove, soundManager, isGameEnd)
            latestOnGameStateChange.value(updated)

            // Online
            Log.d(
                "ChessBoard",
                "Promotion move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}"
            )
            if (isOnlineMode && socketService != null && roomCode != null) {
                if (socketService.isConnected()) {
                    val promo = promotionMove.promo?.toString()
                    if (isAiGame) {
                        socketService.sendMoveVsAi(roomCode, promotionMove.from, promotionMove.to, promo, aiLevel, aiThinkTimeMs)
                        Log.d("ChessBoard", "Sent AI promotion move: ${promotionMove.from} -> ${promotionMove.to}, promo: ${promotionMove.promo}")
                    } else {
                        socketService.sendMove(roomCode, promotionMove.from, promotionMove.to, promo)
                        Log.d("ChessBoard", "Sent promotion move to server: ${promotionMove.from} -> ${promotionMove.to}, promo: ${promotionMove.promo}")
                    }
                } else {
                    Log.w("ChessBoard", "Socket not connected for promotion move")
                }
            }

            // P2P Nearby
            if (isNearbyMode && nearbyManager != null) {
                nearbyManager.sendMove(promotionMove)
                Log.d("ChessBoard", "Sent P2P promotion move: ${promotionMove.from} -> ${promotionMove.to}, promo: ${promotionMove.promo}")
                
                // Send timer update after promotion
                playerTimers?.let { timers ->
                    nearbyManager.sendTimerUpdate(
                        timers.whiteRemainingMs,
                        timers.blackRemainingMs
                    )
                }
            }

            pendingPromotionMove = null
            showPromotionSheet = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
    ) {
        // Back
        IconButton(
            onClick = onBack,
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

        // Main column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Opponent Timer (top)
            if (timerEnabled && playerTimers != null) {
                val timers = playerTimers!!
                val opponentSide = if (playerColor == Side.WHITE) Side.BLACK else Side.WHITE
                val opponentTime = if (opponentSide == Side.WHITE) timers.whiteRemainingMs else timers.blackRemainingMs
                ChessTimerDisplay(
                    timeRemainingMs = opponentTime,
                    isActive = gameState.sideToMove == opponentSide,
                    isPlayerTimer = false,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .background(Color(0xFFEEEED2))
            ) {
                val size = minOf(maxWidth, maxHeight)
                val sq: Dp = size / 8

                // 1) Board
                BoardBackground(square = sq)

                // 2) Highlights
                MovesOverlay(moves = availableMoves, square = sq, shouldRotateBoard = shouldRotateBoard)

                // 3) Check indicator
                CheckIndicator(gameState = gameState, square = sq, shouldRotateBoard = shouldRotateBoard)

                // 4) Pieces
                PiecesLayer(
                    board = gameState.boards,
                    square = sq,
                    animatingPiece = animatingPiece,
                    animationProgress = animationProgress.value,
                    shouldRotateBoard = shouldRotateBoard
                )

                // 5) Click grid
                ClickGrid(
                    square = sq,
                    onSquareClick = { r, c ->
                        val status = getGameStatus(gameState)
                        if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) {
                            return@ClickGrid
                        }

                        val (realR, realC) = rotateRowCol(r, c)
                        val idx = indexFromRowCol(realR, realC)
                        val here = pieceAt(gameState.boards, idx)

                        if (selected == null) {
                            val canSelectPiece = if (playerColor != null) {
                                here?.first == playerColor && here.first == gameState.sideToMove
                            } else {
                                here?.first == gameState.sideToMove
                            }

                            if (canSelectPiece) {
                                selected = idx
                                availableMoves = generateMoves(gameState, idx)
                            } else {
                                selected = null
                                availableMoves = emptyList()
                            }
                        } else {
                            val sel = selected!!
                            val targetMove = availableMoves.find { it.from == sel && it.to == idx }

                            if (targetMove != null) {
                                val movingPiece = pieceAt(gameState.boards, sel)
                                if (movingPiece != null) {
                                    val sideChar = if (movingPiece.first == Side.WHITE) "w" else "b"
                                    val pieceChar = movingPiece.second.lowercase()
                                    val pieceCode = "$sideChar$pieceChar"
                                    animatingPiece = Triple(pieceCode, sel, idx)
                                    pendingMove = targetMove
                                } else {
                                    // Fallback rất hiếm khi xảy ra
                                    val cur = latestGameState.value
                                    val st = getGameStatus(cur)
                                    val end = st == GameStatus.CHECKMATE || st == GameStatus.STALEMATE
                                    latestOnGameStateChange.value(applyMove(cur, targetMove, soundManager, end))

                                    Log.d(
                                        "ChessBoard",
                                        "Fallback move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}"
                                    )
                                    if (isOnlineMode && socketService != null && roomCode != null) {
                                        if (socketService.isConnected()) {
                                            val promo = targetMove.promo?.toString()
                                            if (isAiGame) {
                                                socketService.sendMoveVsAi(roomCode, targetMove.from, targetMove.to, promo, aiLevel, aiThinkTimeMs)
                                                Log.d("ChessBoard", "Sent AI fallback move: ${targetMove.from} -> ${targetMove.to}")
                                            } else {
                                                socketService.sendMove(roomCode, targetMove.from, targetMove.to, promo)
                                                Log.d("ChessBoard", "Sent fallback move to server: ${targetMove.from} -> ${targetMove.to}")
                                            }
                                        } else {
                                            Log.w("ChessBoard", "Socket not connected for fallback move")
                                        }
                                    }
                                }
                                selected = null
                                availableMoves = emptyList()
                            } else {
                                val canSelectPiece = if (playerColor != null) {
                                    here?.first == playerColor && here.first == gameState.sideToMove
                                } else {
                                    here?.first == gameState.sideToMove
                                }

                                if (canSelectPiece) {
                                    selected = idx
                                    availableMoves = generateMoves(gameState, idx)
                                } else {
                                    selected = null
                                    availableMoves = emptyList()
                                }
                            }
                        }
                    }
                )

                // 6) Highlight origin
                selected?.let { HighlightOrigin(index = it, square = sq, shouldRotateBoard = shouldRotateBoard) }
            }

            // Player Timer (bottom)
            if (timerEnabled && playerTimers != null) {
                val timers = playerTimers!!
                val playerTime = if (playerColor == Side.WHITE) timers.whiteRemainingMs else timers.blackRemainingMs
                ChessTimerDisplay(
                    timeRemainingMs = playerTime,
                    isActive = gameState.sideToMove == playerColor,
                    isPlayerTimer = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 7) Promotion Sheet
            if (showPromotionSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showPromotionSheet = false
                        pendingPromotionMove = null
                    },
                    sheetState = sheetState
                ) {
                    PromotionPicker(
                        side = latestGameState.value.sideToMove,
                        onPick = applyPromotionMove
                    )
                }
            }

            // 8) Game End Dialog
            GameEndDialog(
                gameState = gameState,
                gameEndedOnTime = gameEndedOnTime,
                onReturnToMenu = onBack
            )
        }
    }
}

/* ------------------------------ Drawing layers ----------------------------- */
@Composable
private fun BoardBackground(square: Dp) {
    val light = Color(0xFFF0D9B5)
    val dark = Color(0xFFB58863)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val sqPx = square.toPx()
            for (r in 0..7) for (c in 0..7) {
                val isDark = (r + c) % 2 == 1
                drawRect(
                    color = if (isDark) dark else light,
                    topLeft = Offset(x = c * sqPx, y = r * sqPx),
                    size = androidx.compose.ui.geometry.Size(sqPx, sqPx)
                )
            }
        }
    }
}

@Composable
private fun PiecesLayer(
    board: Bitboards,
    square: Dp,
    animatingPiece: Triple<String, Int, Int>?,
    animationProgress: Float,
    shouldRotateBoard: Boolean = false
) {
    @Composable
    fun emit(bb: ULong, code: String) {
        val squares = squaresFrom(bb)
        for (idx in squares) {
            if (animatingPiece != null && idx == animatingPiece.second && animatingPiece.first == code) {
                continue
            }
            val (r, c) = rowColFromIndex(idx)
            val (displayR, displayC) = if (shouldRotateBoard) Pair(7 - r, 7 - c) else Pair(r, c)
            Piece(code, displayR, displayC, square)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // White
        emit(board.WK, "wk"); emit(board.WQ, "wq"); emit(board.WR, "wr")
        emit(board.WB, "wb"); emit(board.WN, "wn"); emit(board.WP, "wp")
        // Black
        emit(board.BK, "bk"); emit(board.BQ, "bq"); emit(board.BR, "br")
        emit(board.BB, "bb"); emit(board.BN, "bn"); emit(board.BP, "bp")

        // Animated piece
        animatingPiece?.let { (pieceCode, fromIdx, toIdx) ->
            val (fromR, fromC) = rowColFromIndex(fromIdx)
            val (toR, toC) = rowColFromIndex(toIdx)

            val (displayFromR, displayFromC) = if (shouldRotateBoard) Pair(7 - fromR, 7 - fromC) else Pair(fromR, fromC)
            val (displayToR, displayToC) = if (shouldRotateBoard) Pair(7 - toR, 7 - toC) else Pair(toR, toC)

            val currentR = displayFromR + (displayToR - displayFromR) * animationProgress
            val currentC = displayFromC + (displayToC - displayFromC) * animationProgress

            AnimatedPiece(
                code = pieceCode,
                row = currentR,
                col = currentC,
                square = square
            )
        }
    }
}

@Composable
private fun MovesOverlay(moves: List<Move>, square: Dp, shouldRotateBoard: Boolean = false) {
    if (moves.isEmpty()) return
    val moveColor = Color(0xAA2ECC71)
    val castleColor = Color(0xAA3498DB)
    val enPassantColor = Color(0xAAF39C12)

    Canvas(Modifier.fillMaxSize()) {
        val sqPx = square.toPx()
        for (move in moves) {
            val (r, c) = rowColFromIndex(move.to)
            val (displayR, displayC) = if (shouldRotateBoard) Pair(7 - r, 7 - c) else Pair(r, c)
            val center = Offset(displayC * sqPx + sqPx / 2f, displayR * sqPx + sqPx / 2f)
            val radius = sqPx * 0.18f

            val color = when {
                move.isCastle -> castleColor
                move.isEnPassant -> enPassantColor
                else -> moveColor
            }
            drawCircle(color = color, radius = radius, center = center)
        }
    }
}

/** Highlight vua khi bị chiếu */
@Composable
private fun CheckIndicator(gameState: GameState, square: Dp, shouldRotateBoard: Boolean = false) {
    if (isKingInCheck(gameState, gameState.sideToMove)) {
        val kingBB = if (gameState.sideToMove == Side.WHITE) gameState.boards.WK else gameState.boards.BK
        val kingSq = squaresFrom(kingBB).firstOrNull()
        if (kingSq != null) {
            val checkColor = Color(0xAAE74C3C)
            Canvas(Modifier.fillMaxSize()) {
                val sqPx = square.toPx()
                val (r, c) = rowColFromIndex(kingSq)
                val (displayR, displayC) = if (shouldRotateBoard) Pair(7 - r, 7 - c) else Pair(r, c)
                val pad = sqPx * 0.05f
                drawRect(
                    color = checkColor,
                    topLeft = Offset(displayC * sqPx + pad, displayR * sqPx + pad),
                    size = androidx.compose.ui.geometry.Size(sqPx - 2 * pad, sqPx - 2 * pad)
                )
            }
        }
    }
}

/** Highlight ô nguồn */
@Composable
private fun HighlightOrigin(index: Int, square: Dp, shouldRotateBoard: Boolean = false) {
    val col = Color(0x88498AF3)
    Canvas(Modifier.fillMaxSize()) {
        val sqPx = square.toPx()
        val (r, c) = rowColFromIndex(index)
        val (displayR, displayC) = if (shouldRotateBoard) Pair(7 - r, 7 - c) else Pair(r, c)
        val pad = sqPx * 0.05f
        drawRect(
            color = col,
            topLeft = Offset(displayC * sqPx + pad, displayR * sqPx + pad),
            size = androidx.compose.ui.geometry.Size(sqPx - 2 * pad, sqPx - 2 * pad)
        )
    }
}

/* ------------------------------ Input layer ------------------------------ */
@Composable
private fun ClickGrid(
    square: Dp,
    onSquareClick: (row: Int, col: Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        for (r in 0..7) {
            Row(Modifier.weight(1f)) {
                for (c in 0..7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSquareClick(r, c) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedPiece(
    code: String,
    row: Float,
    col: Float,
    square: Dp
) {
    Piece(
        code = code,
        row = row.toInt(),
        col = col.toInt(),
        square = square,
        modifier = Modifier.offset(
            x = square * (col - col.toInt()),
            y = square * (row - row.toInt())
        )
    )
}

/** Dialog shows when the game ends */
@Composable
private fun GameEndDialog(
    gameState: GameState,
    gameEndedOnTime: Side? = null,
    onReturnToMenu: () -> Unit
) {
    val status = getGameStatus(gameState)

    if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE || gameEndedOnTime != null) {
        AlertDialog(
            onDismissRequest = { /* block outside dismiss */ },
            title = {
                Text(
                    text = when {
                        gameEndedOnTime != null -> "TIME'S UP!"
                        status == GameStatus.CHECKMATE -> "GAME OVER"
                        else -> "DRAW"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                val message = when {
                    gameEndedOnTime != null -> {
                        val winner = if (gameEndedOnTime == Side.WHITE) "WHITE" else "BLACK"
                        "$winner WINS ON TIME!"
                    }
                    status == GameStatus.CHECKMATE -> {
                        val winner = if (gameState.sideToMove == Side.WHITE) "BLACK" else "WHITE"
                        "$winner WINS!"
                    }
                    status == GameStatus.STALEMATE -> "STALEMATE - DRAW"
                    else -> ""
                }
                Text(
                    text = message,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onReturnToMenu,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2ECC71),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Back to Main Menu")
                    }
                }
            },
            containerColor = Color(0xFF1A1A1F),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
