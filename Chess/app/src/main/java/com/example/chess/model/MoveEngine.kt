package com.example.chess.model

import com.example.chess.audio.ChessSoundManager
import com.example.chess.audio.playMoveSound

/* ------------------------ Bộ sinh nước đi "đúng luật" ------------------------ */

private fun pawnMovesWithSpecial(from: Int, side: Side, state: GameState): List<Move> {
    val b = state.boards
    val occ = occupancy(b)
    val all = occ.all
    val (r0, c0) = rowColFromIndex(from)
    val dir = if (side == Side.WHITE) -1 else +1
    val startRank = if (side == Side.WHITE) 6 else 1
    val lastRank = if (side == Side.WHITE) 0 else 7

    val res = ArrayList<Move>()

    // 1) Đi thẳng 1 ô
    val r1 = r0 + dir
    if (r1 in 0..7) {
        val i1 = indexFromRowCol(r1, c0)
        if (!isSet(all, i1)) {
            if (r1 == lastRank) {
                for (pr in charArrayOf('Q','R','B','N')) res += Move(from, i1, promo = pr)
            } else {
                res += Move(from, i1)
                // 2) Đi thẳng 2 ô (từ hàng xuất phát)
                if (r0 == startRank) {
                    val r2 = r0 + 2 * dir
                    val i2 = indexFromRowCol(r2, c0)
                    if (!isSet(all, i2)) res += Move(from, i2, isDoublePawnPush = true)
                }
            }
        }
    }

    // 3) Ăn chéo thường
    for (dc in intArrayOf(-1, +1)) {
        val rr = r0 + dir
        val cc = c0 + dc
        if (rr !in 0..7 || cc !in 0..7) continue
        val i = indexFromRowCol(rr, cc)
        val targetHasOpp = if (side == Side.WHITE) isSet(occ.black, i) else isSet(occ.white, i)
        if (targetHasOpp) {
            if (rr == lastRank) for (pr in charArrayOf('Q','R','B','N')) res += Move(from, i, promo = pr)
            else res += Move(from, i)
        }
    }

    // 4) Bắt tốt qua đường (en passant)
    state.enPassantSquare?.let { ep ->
        val (er, ec) = rowColFromIndex(ep)
        if (er == r0 + dir && kotlin.math.abs(ec - c0) == 1) {
            res += Move(from, ep, isEnPassant = true)
        }
    }

    return res
}

private fun castlingMoves(side: Side, state: GameState): List<Move> {
    val b = state.boards
    val occ = occupancy(b)
    val atkOpp = attackMask(b, if (side == Side.WHITE) Side.BLACK else Side.WHITE)

    val kingSq: Int
    val emptyK: IntArray; val emptyQ: IntArray
    val passK: IntArray;  val passQ: IntArray
    val rights = state.castling

    if (side == Side.WHITE) {
        kingSq = squaresFrom(b.WK).firstOrNull() ?: return emptyList()
        emptyK = intArrayOf(indexFromRowCol(7,5), indexFromRowCol(7,6))
        emptyQ = intArrayOf(indexFromRowCol(7,1), indexFromRowCol(7,2), indexFromRowCol(7,3))
        passK  = intArrayOf(indexFromRowCol(7,5), indexFromRowCol(7,6))
        passQ  = intArrayOf(indexFromRowCol(7,3), indexFromRowCol(7,2))
    } else {
        kingSq = squaresFrom(b.BK).firstOrNull() ?: return emptyList()
        emptyK = intArrayOf(indexFromRowCol(0,5), indexFromRowCol(0,6))
        emptyQ = intArrayOf(indexFromRowCol(0,1), indexFromRowCol(0,2), indexFromRowCol(0,3))
        passK  = intArrayOf(indexFromRowCol(0,5), indexFromRowCol(0,6))
        passQ  = intArrayOf(indexFromRowCol(0,3), indexFromRowCol(0,2))
    }

    fun pathEmpty(keys: IntArray) = keys.all { !isSet(occ.all, it) }
    fun pathSafe(kFrom: Int, keys: IntArray): Boolean {
        if (isSet(atkOpp, kFrom)) return false // vua đang bị chiếu
        return keys.all { !isSet(atkOpp, it) }
    }

    val res = ArrayList<Move>()
    if (side == Side.WHITE) {
        if (rights.whiteKingSide && pathEmpty(emptyK) && pathSafe(kingSq, passK))
            res += Move(kingSq, indexFromRowCol(7,6), isCastle = true)
        if (rights.whiteQueenSide && pathEmpty(emptyQ) && pathSafe(kingSq, passQ))
            res += Move(kingSq, indexFromRowCol(7,2), isCastle = true)
    } else {
        if (rights.blackKingSide && pathEmpty(emptyK) && pathSafe(kingSq, passK))
            res += Move(kingSq, indexFromRowCol(0,6), isCastle = true)
        if (rights.blackQueenSide && pathEmpty(emptyQ) && pathSafe(kingSq, passQ))
            res += Move(kingSq, indexFromRowCol(0,2), isCastle = true)
    }
    return res
}

/** Sinh danh sách Move (pseudo-legal + kiểm tra an toàn đường nhập thành). */
fun generateMoves(state: GameState, fromIndex: Int): List<Move> {
    val b = state.boards
    val occ = occupancy(b)
    val who = pieceAt(b, fromIndex) ?: return emptyList()
    val (side, type) = who
    if (side != state.sideToMove) return emptyList()

    val own = if (side == Side.WHITE) occ.white else occ.black
    val oppAll = occ.all

    val pseudoLegalMoves = when (type) {
        'K' -> {
            val km = kingMoves(fromIndex, own)
            val normal = squaresFrom(km).map { Move(fromIndex, it) }
            normal + castlingMoves(side, state).filter { it.from == fromIndex }
        }
        'Q' -> squaresFrom(queenMoves(fromIndex, own, oppAll)).map { Move(fromIndex, it) }
        'R' -> squaresFrom(rookMoves(fromIndex, own, oppAll)).map { Move(fromIndex, it) }
        'B' -> squaresFrom(bishopMoves(fromIndex, own, oppAll)).map { Move(fromIndex, it) }
        'N' -> squaresFrom(knightMoves(fromIndex, own)).map { Move(fromIndex, it) }
        'P' -> pawnMovesWithSpecial(fromIndex, side, state)
        else -> emptyList()
    }

    // Lọc chỉ những nước đi không khiến vua bị chiếu (legal moves)
    return pseudoLegalMoves.filter { move ->
        !wouldLeaveKingInCheck(state, move)
    }
}

/** Sinh tất cả nước đi hợp lệ cho bên hiện tại */
fun generateAllLegalMoves(state: GameState): List<Move> {
    val b = state.boards
    val side = state.sideToMove
    val allPieces = if (side == Side.WHITE) {
        occupancy(b).white
    } else {
        occupancy(b).black
    }
    
    val allMoves = ArrayList<Move>()
    for (sq in squaresFrom(allPieces)) {
        allMoves.addAll(generateMoves(state, sq))
    }
    return allMoves
}

/* -------------------------- Áp dụng 1 nước đi Move -------------------------- */

/**
 * Áp dụng nước đi với xử lý âm thanh
 */
fun applyMove(
    state: GameState, 
    m: Move, 
    soundManager: ChessSoundManager? = null,
    isGameEnd: Boolean = false
): GameState {
    val b = state.boards
    val who = pieceAt(b, m.from) ?: return state
    val (side, type) = who
    
    // Phát hiện các trường hợp đặc biệt để phát âm thanh
    val isCapture = pieceAt(b, m.to) != null || m.isEnPassant
    val newState = applyMoveInternal(state, m)
    val isCheck = isKingInCheck(newState, if (side == Side.WHITE) Side.BLACK else Side.WHITE)
    
    // Phát âm thanh phù hợp
    soundManager?.playMoveSound(
        isCapture = isCapture,
        isCheck = isCheck,
        isGameEnd = isGameEnd
    )
    
    return newState
}

/**
 * Áp dụng nước đi không có âm thanh (phiên bản nội bộ)
 */
private fun applyMoveInternal(state: GameState, m: Move): GameState {
    val b = state.boards
    val who = pieceAt(b, m.from) ?: return state
    val (side, type) = who

    fun removeAtAll(bb: Bitboards, idx: Int): Bitboards = bb.copy(
        WP = clearAt(bb.WP, idx), WN = clearAt(bb.WN, idx), WB = clearAt(bb.WB, idx),
        WR = clearAt(bb.WR, idx), WQ = clearAt(bb.WQ, idx), WK = clearAt(bb.WK, idx),
        BP = clearAt(bb.BP, idx), BN = clearAt(bb.BN, idx), BB = clearAt(bb.BB, idx),
        BR = clearAt(bb.BR, idx), BQ = clearAt(bb.BQ, idx), BK = clearAt(bb.BK, idx),
    )

    var nb = b
    // 1) Xóa quân bị bắt
    if (!m.isEnPassant) {
        nb = removeAtAll(nb, m.to)
    } else {
        val (rTo, cTo) = rowColFromIndex(m.to)
        val capRow = if (side == Side.WHITE) rTo + 1 else rTo - 1
        nb = removeAtAll(nb, indexFromRowCol(capRow, cTo))
    }

    // 2) Di chuyển quân from -> to
    fun mv(bb: ULong): ULong = setAt(clearAt(bb, m.from), m.to)
    nb = when (side) {
        Side.WHITE -> when (type) {
            'P' -> nb.copy(WP = mv(nb.WP))
            'N' -> nb.copy(WN = mv(nb.WN))
            'B' -> nb.copy(WB = mv(nb.WB))
            'R' -> nb.copy(WR = mv(nb.WR))
            'Q' -> nb.copy(WQ = mv(nb.WQ))
            'K' -> nb.copy(WK = mv(nb.WK))
            else -> nb
        }
        Side.BLACK -> when (type) {
            'P' -> nb.copy(BP = mv(nb.BP))
            'N' -> nb.copy(BN = mv(nb.BN))
            'B' -> nb.copy(BB = mv(nb.BB))
            'R' -> nb.copy(BR = mv(nb.BR))
            'Q' -> nb.copy(BQ = mv(nb.BQ))
            'K' -> nb.copy(BK = mv(nb.BK))
            else -> nb
        }
    }

    // 3) Nhập thành -> kéo Xe
    if (m.isCastle) {
        if (side == Side.WHITE) {
            if (m.to == indexFromRowCol(7,6)) { // O-O
                nb = nb.copy(WR = setAt(clearAt(nb.WR, indexFromRowCol(7,7)), indexFromRowCol(7,5)))
            } else if (m.to == indexFromRowCol(7,2)) { // O-O-O
                nb = nb.copy(WR = setAt(clearAt(nb.WR, indexFromRowCol(7,0)), indexFromRowCol(7,3)))
            }
        } else {
            if (m.to == indexFromRowCol(0,6)) { // O-O
                nb = nb.copy(BR = setAt(clearAt(nb.BR, indexFromRowCol(0,7)), indexFromRowCol(0,5)))
            } else if (m.to == indexFromRowCol(0,2)) { // O-O-O
                nb = nb.copy(BR = setAt(clearAt(nb.BR, indexFromRowCol(0,0)), indexFromRowCol(0,3)))
            }
        }
    }

    // 4) Phong cấp (nếu có)
    if (m.promo != null && type == 'P') {
        val (r, _) = rowColFromIndex(m.to)
        val onLast = (side == Side.WHITE && r == 0) || (side == Side.BLACK && r == 7)
        if (onLast) {
            if (side == Side.WHITE) {
                nb = nb.copy(WP = clearAt(nb.WP, m.to))
                nb = when (m.promo) {
                    'Q' -> nb.copy(WQ = setAt(nb.WQ, m.to))
                    'R' -> nb.copy(WR = setAt(nb.WR, m.to))
                    'B' -> nb.copy(WB = setAt(nb.WB, m.to))
                    'N' -> nb.copy(WN = setAt(nb.WN, m.to))
                    else -> nb
                }
            } else {
                nb = nb.copy(BP = clearAt(nb.BP, m.to))
                nb = when (m.promo) {
                    'Q' -> nb.copy(BQ = setAt(nb.BQ, m.to))
                    'R' -> nb.copy(BR = setAt(nb.BR, m.to))
                    'B' -> nb.copy(BB = setAt(nb.BB, m.to))
                    'N' -> nb.copy(BN = setAt(nb.BN, m.to))
                    else -> nb
                }
            }
        }
    }

    // 5) Quyền nhập thành (hạ khi vua/xe di chuyển hoặc xe bị bắt)
    fun dropRights(cr: CastlingRights, s: Side, kingMoved: Boolean, rookFrom: Int?, rookTo: Int?): CastlingRights {
        var x = cr
        if (s == Side.WHITE) {
            if (kingMoved) x = x.copy(whiteKingSide = false, whiteQueenSide = false)
            if (rookFrom == indexFromRowCol(7,7) || rookTo == indexFromRowCol(7,7)) x = x.copy(whiteKingSide = false)
            if (rookFrom == indexFromRowCol(7,0) || rookTo == indexFromRowCol(7,0)) x = x.copy(whiteQueenSide = false)
        } else {
            if (kingMoved) x = x.copy(blackKingSide = false, blackQueenSide = false)
            if (rookFrom == indexFromRowCol(0,7) || rookTo == indexFromRowCol(0,7)) x = x.copy(blackKingSide = false)
            if (rookFrom == indexFromRowCol(0,0) || rookTo == indexFromRowCol(0,0)) x = x.copy(blackQueenSide = false)
        }
        return x
    }

    val kingMoved = (type == 'K')
    val rookMovedFrom = if (type == 'R') m.from else null
    val rookMovedTo   = if (type == 'R') m.to else null
    var newCastling = dropRights(state.castling, side, kingMoved, rookMovedFrom, rookMovedTo)

    // Nếu bắt được Xe ở góc -> cũng hạ quyền phía đối thủ
    fun adjustOppOnRookCapture(capturedAt: Int?) {
        if (capturedAt == null) return
        if (side == Side.WHITE) {
            if (capturedAt == indexFromRowCol(0,7)) newCastling = newCastling.copy(blackKingSide = false)
            if (capturedAt == indexFromRowCol(0,0)) newCastling = newCastling.copy(blackQueenSide = false)
        } else {
            if (capturedAt == indexFromRowCol(7,7)) newCastling = newCastling.copy(whiteKingSide = false)
            if (capturedAt == indexFromRowCol(7,0)) newCastling = newCastling.copy(whiteQueenSide = false)
        }
    }

    val pre = state.boards
    val capturedSquare = if (!m.isEnPassant && pieceAt(pre, m.to)?.first == (if (side == Side.WHITE) Side.BLACK else Side.WHITE)) m.to else null
    adjustOppOnRookCapture(capturedSquare)

    // 6) Cập nhật ô EP (nếu vừa đẩy tốt 2 ô)
    val newEp = if (m.isDoublePawnPush) {
        val (rf, cf) = rowColFromIndex(m.from)
        val (rt, _)  = rowColFromIndex(m.to)
        indexFromRowCol((rf + rt) / 2, cf)
    } else null

    return state.copy(
        boards = nb,
        sideToMove = if (state.sideToMove == Side.WHITE) Side.BLACK else Side.WHITE,
        castling = newCastling,
        enPassantSquare = newEp
    )
}
