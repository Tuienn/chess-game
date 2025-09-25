package com.example.chess.model

/* ------------- Hàm tính “ô bị khống chế” để kiểm tra nhập thành/chiếu ------------- */

private fun pawnAttacksFrom(idx: Int, side: Side): ULong {
    val (r, c) = rowColFromIndex(idx)
    var m = 0UL
    when (side) {
        Side.WHITE -> {
            val a = arrayOf(r - 1 to c - 1, r - 1 to c + 1)
            for ((rr, cc) in a) if (rr in 0..7 && cc in 0..7) m = m or bitAt(indexFromRowCol(rr, cc))
        }
        Side.BLACK -> {
            val a = arrayOf(r + 1 to c - 1, r + 1 to c + 1)
            for ((rr, cc) in a) if (rr in 0..7 && cc in 0..7) m = m or bitAt(indexFromRowCol(rr, cc))
        }
    }
    return m
}

/** Tập hợp tất cả ô bị tấn công bởi 1 bên (dùng cho nhập thành, tự-chiếu, v.v.) */
fun attackMask(b: Bitboards, bySide: Side): ULong {
    val occ = occupancy(b)
    val own = if (bySide == Side.WHITE) occ.white else occ.black

    var mask = 0UL
    // Pawns
    val pawns = if (bySide == Side.WHITE) b.WP else b.BP
    for (sq in squaresFrom(pawns)) mask = mask or pawnAttacksFrom(sq, bySide)

    // Knights, Bishops, Rooks, Queens, Kings: tái sử dụng các hàm di chuyển sẵn có
    val knights = if (bySide == Side.WHITE) b.WN else b.BN
    for (sq in squaresFrom(knights)) mask = mask or knightMoves(sq, own)

    val bishops = if (bySide == Side.WHITE) b.WB else b.BB
    for (sq in squaresFrom(bishops)) mask = mask or bishopMoves(sq, own, occ.all)

    val rooks = if (bySide == Side.WHITE) b.WR else b.BR
    for (sq in squaresFrom(rooks)) mask = mask or rookMoves(sq, own, occ.all)

    val queens = if (bySide == Side.WHITE) b.WQ else b.BQ
    for (sq in squaresFrom(queens)) mask = mask or queenMoves(sq, own, occ.all)

    val king = if (bySide == Side.WHITE) b.WK else b.BK
    for (sq in squaresFrom(king)) mask = mask or kingMoves(sq, own)

    return mask
}

/** Kiểm tra xem vua của một bên có đang bị chiếu không */
fun isKingInCheck(state: GameState, side: Side): Boolean {
    val b = state.boards
    val kingBB = if (side == Side.WHITE) b.WK else b.BK
    val kingSq = squaresFrom(kingBB).firstOrNull() ?: return false
    val oppSide = if (side == Side.WHITE) Side.BLACK else Side.WHITE
    val attackMask = attackMask(b, oppSide)
    return isSet(attackMask, kingSq)
}

/** Kiểm tra xem một nước đi có để vua của mình bị chiếu không (self-check) */
fun wouldLeaveKingInCheck(state: GameState, move: Move): Boolean {
    val newState = applyMove(state, move)
    return isKingInCheck(newState, state.sideToMove)
}

/** Trạng thái game */
enum class GameStatus {
    PLAYING,    // Game đang diễn ra
    CHECK,      // Vua bị chiếu nhưng có nước đi
    CHECKMATE,  // Chiếu bí (thua)
    STALEMATE   // Hòa cờ (không có nước đi hợp lệ nhưng vua không bị chiếu)
}

/** Kiểm tra trạng thái hiện tại của game */
fun getGameStatus(state: GameState): GameStatus {
    val inCheck = isKingInCheck(state, state.sideToMove)
    val legalMoves = generateAllLegalMoves(state)
    
    return when {
        legalMoves.isEmpty() && inCheck -> GameStatus.CHECKMATE
        legalMoves.isEmpty() && !inCheck -> GameStatus.STALEMATE
        inCheck -> GameStatus.CHECK
        else -> GameStatus.PLAYING
    }
}