package com.example.chess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.chess.audio.ChessSoundManager
import com.example.chess.audio.rememberChessSoundManager
import com.example.chess.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessBoardCanvas(
    modifier: Modifier = Modifier,
    playerColor: Side? = null, // null = both sides can move (offline), WHITE/BLACK = only that side can move (online)
    isOnlineMode: Boolean = false // để xác định có xoay bàn cờ không
) {
    val context = LocalContext.current
    val soundManager = rememberChessSoundManager(context)
    
    // 1) Valid chess game state (provided in your model package)
    var gameState by remember { mutableStateOf(GameState(boards = initialBitboards(), sideToMove = Side.WHITE)) }

    // 2) Quản lý sheet chọn phong cấp
    var pendingPromotionMove by remember { mutableStateOf<Move?>(null) }
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPromotionSheet by remember { mutableStateOf(false) }

    // 3) UI bàn cờ (vẽ như bạn đã có)
    Box(modifier.fillMaxSize()) {
        // ... phần vẽ lưới, quân cờ, highlight, xử lý chọn from/to ... (giữ code cũ)

        // Ví dụ bạn có callback khi người chơi chọn xong 1 nước đi:
        val onUserPickMove: (Move) -> Unit = { chosen ->
            // Nếu là nước cần phong cấp mà promo hiện chưa set -> mở sheet
            if (needsPromotion(gameState, chosen) && chosen.promo == null) {
                pendingPromotionMove = chosen
                showPromotionSheet = true
            } else {
                // Nước bình thường
                val status = getGameStatus(gameState)
                val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                gameState = applyMove(gameState, chosen, soundManager, isGameEnd)
            }
        }

        // ... ở nơi bạn generate nước đi từ ô đang chọn:
        // val moves: List<Move> = generateMoves(gameState, fromIndex)
        // Khi user chạm ô đích, bạn build Move { from, to, ... } rồi gọi onUserPickMove(move)

        // 4) Bottom sheet chọn quân
        if (showPromotionSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showPromotionSheet = false
                    pendingPromotionMove = null
                },
                sheetState = sheetState
            ) {
                val sideToPromote = gameState.sideToMove // đang là bên sắp đi trước khi apply
                // Lưu ý: Nếu bạn đã đổi lượt trước khi mở sheet, cần suy luận side từ "m.from"
                PromotionPicker(side = sideToPromote, onPick = { picked ->
                    val pending = pendingPromotionMove
                    if (pending != null) {
                        // Gọi apply với quân được chọn
                        val status = getGameStatus(gameState)
                        val isGameEnd = status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                        gameState = applyMove(gameState, pending.copy(promo = picked), soundManager, isGameEnd)
                    }
                    showPromotionSheet = false
                    pendingPromotionMove = null
                })
            }
        }
    }
}

/** Kiểm tra nước đi có phải tốt lên hàng cuối không (khi promo còn null) */
private fun needsPromotion(state: GameState, move: Move): Boolean {
    val who = pieceAt(state.boards, move.from) ?: return false
    val (side, type) = who
    if (type != 'P') return false
    val (rTo, _) = rowColFromIndex(move.to)
    return (side == Side.WHITE && rTo == 0) || (side == Side.BLACK && rTo == 7)
}
