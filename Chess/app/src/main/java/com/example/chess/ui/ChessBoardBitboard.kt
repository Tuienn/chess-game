
package com.example.chess.ui

import androidx.compose.animation.core.*
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.example.chess.audio.ChessSoundManager
import com.example.chess.audio.rememberChessSoundManager
import com.example.chess.model.*
import com.example.chess.network.SocketService

/** Kiểm tra nước đi có phải tốt lên hàng cuối không */
private fun needsPromotionForMove(state: GameState, move: Move): Boolean {
    val who = pieceAt(state.boards, move.from) ?: return false
    val (side, type) = who
    if (type != 'P') return false
    val (rTo, _) = rowColFromIndex(move.to)
    return (side == Side.WHITE && rTo == 0) || (side == Side.BLACK && rTo == 7)
}


/**
 * Bàn cờ tương tác:
 * - Nhấn 1 ô có quân thuộc lượt hiện tại -> highlight nước đi từ movesForSquare
 * - Nhấn vào một ô đích được highlight -> thực hiện đi quân (có ăn quân), đổi lượt
 * - Trắng đi trước
 * - Hỗ trợ phong cấp tốt với PromotionPicker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessBoardBitboard(
    initial: Bitboards,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerColor: Side? = null, // null = both sides can move (offline), WHITE/BLACK = only that side can move (online)
    isOnlineMode: Boolean = false, // để xác định có xoay bàn cờ không
    roomCode: String? = null, // room code for online play
    isAiMode: Boolean = false,
    aiLevel: Int = 10,
    aiThinkTimeMs: Int = 500
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
        aiThinkTimeMs = aiThinkTimeMs
    ) { gameState = it }
}

/**
 * Overload để nhận GameState trực tiếp (cho testing)
 */
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
    aiThinkTimeMs: Int = 500
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
        aiThinkTimeMs = aiThinkTimeMs
    ) { gameState = it }
}

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
    onGameStateChange: (GameState) -> Unit
) {
    val context = LocalContext.current
    val soundManager = rememberChessSoundManager(context)
    var selected by remember { mutableStateOf<Int?>(null) }
    var availableMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    val latestGameState = rememberUpdatedState(gameState)
    val latestOnGameStateChange = rememberUpdatedState(onGameStateChange)
    
    // Socket service for online play
    val socketService = remember { if (isOnlineMode) SocketService.getInstance() else null }
    val isAiGame = isOnlineMode && isAiMode
    var aiFirstMoveRequested by remember(roomCode) { mutableStateOf(false) }
    
    // Debug log for socket service initialization
    LaunchedEffect(isOnlineMode, roomCode, isAiGame) {
        Log.d(
            "ChessBoard",
            "ChessBoard init: isOnlineMode=$isOnlineMode, isAiGame=$isAiGame, roomCode=$roomCode, socketService=${socketService != null}"
        )
        if (socketService != null) {
            Log.d("ChessBoard", "Socket connected: ${socketService.isConnected()}")
        }
    }
    
    // Setup socket listeners for online play
    LaunchedEffect(socketService, isOnlineMode, isAiGame) {
        if (isOnlineMode && socketService != null) {
            // Ensure socket is connected
            if (!socketService.isConnected()) {
                Log.d("ChessBoard", "Socket not connected, connecting...")
                socketService.connect()
                kotlinx.coroutines.delay(2000) // Wait for connection
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
                    // Apply received move from opponent
                    Log.d("ChessBoard", "Received move from opponent: ${receivedMove.from} -> ${receivedMove.to}")
                    val currentState = latestGameState.value
                    val status = getGameStatus(currentState)
                    val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                    val updatedState = applyMove(currentState, receivedMove, soundManager, isGameEnd)
                    latestOnGameStateChange.value(updatedState)
                }
            }
        }
    }
    DisposableEffect(socketService, isOnlineMode) {
        onDispose {
            if (isOnlineMode && socketService != null) {
                socketService.setOnMoveReceivedCallback(null)
                socketService.setOnAiMoveCallback(null)
            }
        }
    }

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
    
    // Xác định có nên xoay bàn cờ không (chỉ xoay khi chơi online và là quân đen)
    val shouldRotateBoard = isOnlineMode && playerColor == Side.BLACK
    
    // Helper functions để xử lý xoay bàn cờ
    fun rotateRowCol(r: Int, c: Int): Pair<Int, Int> {
        return if (shouldRotateBoard) {
            Pair(7 - r, 7 - c)
        } else {
            Pair(r, c)
        }
    }
    
    fun rotateIndex(idx: Int): Int {
        val (r, c) = rowColFromIndex(idx)
        val (newR, newC) = rotateRowCol(r, c)
        return indexFromRowCol(newR, newC)
    }
    
    // Animation state cho quân cờ đang di chuyển
    var animatingPiece by remember { mutableStateOf<Triple<String, Int, Int>?>(null) } // (pieceCode, fromIdx, toIdx)
    var pendingMove by remember { mutableStateOf<Move?>(null) } // Move chờ thực hiện
    
    // Promotion state
    var pendingPromotionMove by remember { mutableStateOf<Move?>(null) } // Move cần phong cấp
    var showPromotionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val animationProgress = animateFloatAsState(
        targetValue = if (animatingPiece != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        finishedListener = { 
            if (animatingPiece != null) {
                animatingPiece = null
            }
        }
    )
    
    // Handle pending move after animation
    LaunchedEffect(pendingMove) {
        pendingMove?.let { move ->
            kotlinx.coroutines.delay(300) // Đợi animation hoàn thành
            
            // Kiểm tra nếu cần phong cấp
            if (needsPromotionForMove(gameState, move)) {
                pendingPromotionMove = move
                showPromotionSheet = true
                pendingMove = null
            } else {
                // Thực hiện đi quân với GameState
                val status = getGameStatus(gameState)
                val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                onGameStateChange(applyMove(gameState, move, soundManager, isGameEnd))
                
                // Send move to server if playing online
                Log.d("ChessBoard", "Move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}")
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
                } else {
                    Log.w("ChessBoard", "Cannot send move - missing requirements")
                }
                
                pendingMove = null
            }
        }
    }
    
    // Function to apply promotion move
    val applyPromotionMove: (Char) -> Unit = { promotionPiece ->
        pendingPromotionMove?.let { move ->
            // Apply move with promotion
            val promotionMove = move.copy(promo = promotionPiece)
            val status = getGameStatus(gameState)
            val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
            onGameStateChange(applyMove(gameState, promotionMove, soundManager, isGameEnd))
            
            // Send promotion move to server if playing online
            Log.d("ChessBoard", "Promotion move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}")
            if (isOnlineMode && socketService != null && roomCode != null) {
                if (socketService.isConnected()) {
                    val promo = promotionMove.promo?.toString()
                    if (isAiGame) {
                        socketService.sendMoveVsAi(roomCode, promotionMove.from, promotionMove.to, promo, aiLevel, aiThinkTimeMs)
                        Log.d(
                            "ChessBoard",
                            "Sent AI promotion move: ${promotionMove.from} -> ${promotionMove.to}, promo: ${promotionMove.promo}"
                        )
                    } else {
                        socketService.sendMove(roomCode, promotionMove.from, promotionMove.to, promo)
                        Log.d(
                            "ChessBoard",
                            "Sent promotion move to server: ${promotionMove.from} -> ${promotionMove.to}, promo: ${promotionMove.promo}"
                        )
                    }
                } else {
                    Log.w("ChessBoard", "Socket not connected for promotion move")
                }
            } else {
                Log.w("ChessBoard", "Cannot send promotion move - missing requirements")
            }
            
            // Clear promotion state
            pendingPromotionMove = null
            showPromotionSheet = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
    ) {
        // Back arrow button - top left corner
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
        
        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

        // Bàn cờ
        Spacer(modifier = Modifier.weight(1f))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
                .background(Color(0xFFEEEED2))
        ) {
            val size = minOf(maxWidth, maxHeight)
            val sq: Dp = size / 8

            // 1) Draw 8x8 board background
            BoardBackground(square = sq)

            // 2) Overlay move highlights
            MovesOverlay(moves = availableMoves, square = sq, shouldRotateBoard = shouldRotateBoard)

            // 3) Check indicator (highlight king when in check)
            CheckIndicator(gameState = gameState, square = sq, shouldRotateBoard = shouldRotateBoard)

            // 4) Draw pieces
            PiecesLayer(
                board = gameState.boards, 
                square = sq,
                animatingPiece = animatingPiece,
                animationProgress = animationProgress.value,
                shouldRotateBoard = shouldRotateBoard
            )

            // 5) 8x8 click grid
            ClickGrid(
                square = sq,
                onSquareClick = { r, c ->
                    // Check game status - block moves if game has ended
                    val status = getGameStatus(gameState)
                    if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) {
                        return@ClickGrid
                    }
                    
                    // Chuyển đổi tọa độ click về tọa độ thật của bàn cờ
                    val (realR, realC) = rotateRowCol(r, c)
                    val idx = indexFromRowCol(realR, realC)
                    val here = pieceAt(gameState.boards, idx)

                    if (selected == null) {
                        // No selection -> only allow selecting current side's piece
                        val canSelectPiece = if (playerColor != null) {
                            // Online mode: chỉ cho phép chọn quân của màu được chỉ định
                            here?.first == playerColor && here.first == gameState.sideToMove
                        } else {
                            // Offline mode: cho phép chọn quân của lượt hiện tại
                            here?.first == gameState.sideToMove
                        }
                        
                        if (canSelectPiece) {
                            selected = idx
                            availableMoves = generateMoves(gameState, idx)
                        } else {
                            // tapping empty, opponent piece, or not player's turn -> ignore
                            selected = null
                            availableMoves = emptyList()
                        }
                    } else {
                        val sel = selected!!
                        // Find corresponding move
                        val targetMove = availableMoves.find { it.from == sel && it.to == idx }
                        
                        if (targetMove != null) {
                            // Capture piece info before moving
                            val movingPiece = pieceAt(gameState.boards, sel)
                            if (movingPiece != null) {
                                val sideChar = if (movingPiece.first == Side.WHITE) "w" else "b"
                                val pieceChar = movingPiece.second.lowercase()
                                val pieceCode = "$sideChar$pieceChar"
                                // Start animation
                                animatingPiece = Triple(pieceCode, sel, idx)
                                // Set pending move to apply after animation completes
                                pendingMove = targetMove
                            } else {
                                // Fallback if piece not found
                                val status = getGameStatus(gameState)
                                val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                                onGameStateChange(applyMove(gameState, targetMove, soundManager, isGameEnd))
                                
                                // Send move to server if playing online
                                Log.d("ChessBoard", "Fallback move check: isOnlineMode=$isOnlineMode, socketService=${socketService != null}, roomCode=$roomCode, connected=${socketService?.isConnected()}")
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
                                } else {
                                    Log.w("ChessBoard", "Cannot send fallback move - missing requirements")
                                }
                            }
                            selected = null
                            availableMoves = emptyList()
                        } else {
                            // Tap another friendly piece -> change selection
                            val canSelectPiece = if (playerColor != null) {
                                // Online mode: chỉ cho phép chọn quân của màu được chỉ định
                                here?.first == playerColor && here.first == gameState.sideToMove
                            } else {
                                // Offline mode: cho phép chọn quân của lượt hiện tại
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

            // 6) Highlight selected origin (after grid to not block clicks)
            selected?.let { HighlightOrigin(index = it, square = sq, shouldRotateBoard = shouldRotateBoard) }
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
                    side = gameState.sideToMove,
                    onPick = applyPromotionMove
                )
            }
        }
        
        // 8) Game End Dialog
        GameEndDialog(
            gameState = gameState,
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
            // Không hiển thị quân cờ đang di chuyển ở vị trí cũ
            if (animatingPiece != null && idx == animatingPiece.second && animatingPiece.first == code) {
                continue
            }
            val (r, c) = rowColFromIndex(idx)
            // Xoay tọa độ nếu cần
            val (displayR, displayC) = if (shouldRotateBoard) {
                Pair(7 - r, 7 - c)
            } else {
                Pair(r, c)
            }
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
        
        // Render quân cờ đang di chuyển với animation
        animatingPiece?.let { (pieceCode, fromIdx, toIdx) ->
            val (fromR, fromC) = rowColFromIndex(fromIdx)
            val (toR, toC) = rowColFromIndex(toIdx)
            
            // Xoay tọa độ nếu cần
            val (displayFromR, displayFromC) = if (shouldRotateBoard) {
                Pair(7 - fromR, 7 - fromC)
            } else {
                Pair(fromR, fromC)
            }
            val (displayToR, displayToC) = if (shouldRotateBoard) {
                Pair(7 - toR, 7 - toC)
            } else {
                Pair(toR, toC)
            }
            
            // Tính toán vị trí interpolated
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
    val captureColor = Color(0xAAE74C3C) // red-ish
    val moveColor = Color(0xAA2ECC71)    // green-ish
    val castleColor = Color(0xAA3498DB)  // blue-ish for castling
    val enPassantColor = Color(0xAAF39C12) // orange-ish for en passant
    
    Canvas(Modifier.fillMaxSize()) {
        val sqPx = square.toPx()
        for (move in moves) {
            val (r, c) = rowColFromIndex(move.to)
            // Xoay tọa độ nếu cần
            val (displayR, displayC) = if (shouldRotateBoard) {
                Pair(7 - r, 7 - c)
            } else {
                Pair(r, c)
            }
            val center = Offset(displayC * sqPx + sqPx / 2f, displayR * sqPx + sqPx / 2f)
            val radius = sqPx * 0.18f
            
            // Chọn màu dựa trên loại nước đi
            val color = when {
                move.isCastle -> castleColor
                move.isEnPassant -> enPassantColor
                else -> moveColor // Bao gồm cả capture thông thường
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
            val checkColor = Color(0xAAE74C3C) // Đỏ cho check
            Canvas(Modifier.fillMaxSize()) {
                val sqPx = square.toPx()
                val (r, c) = rowColFromIndex(kingSq)
                // Xoay tọa độ nếu cần
                val (displayR, displayC) = if (shouldRotateBoard) {
                    Pair(7 - r, 7 - c)
                } else {
                    Pair(r, c)
                }
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
        // Xoay tọa độ nếu cần
        val (displayR, displayC) = if (shouldRotateBoard) {
            Pair(7 - r, 7 - c)
        } else {
            Pair(r, c)
        }
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
        row = row.toInt(), // Piece hiện tại chỉ nhận Int, nhưng offset sẽ được điều chỉnh
        col = col.toInt(),
        square = square,
        modifier = Modifier.offset(
            x = square * (col - col.toInt()), // Offset fractional part
            y = square * (row - row.toInt())
        )
    )
}

/** Dialog shows when the game ends */
@Composable
private fun GameEndDialog(
    gameState: GameState,
    onReturnToMenu: () -> Unit
) {
    val status = getGameStatus(gameState)
    
    if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) {
        AlertDialog(
            onDismissRequest = { /* block outside dismiss */ },
            title = {
                Text(
                    text = if (status == GameStatus.CHECKMATE) "GAME OVER" else "DRAW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                val message = when (status) {
                    GameStatus.CHECKMATE -> {
                        val winner = if (gameState.sideToMove == Side.WHITE) "BLACK" else "WHITE"
                        "$winner WINS!"
                    }
                    GameStatus.STALEMATE -> "STALEMATE - DRAW"
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