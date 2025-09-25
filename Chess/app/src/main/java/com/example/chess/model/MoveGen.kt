
package com.example.chess.model

import com.example.chess.audio.ChessSoundManager
import com.example.chess.audio.playMoveSound

/* ---------------------------- Move generation ----------------------------- */
enum class Side { WHITE, BLACK }

data class Occupancy(val white: ULong, val black: ULong, val all: ULong)

private fun has(bb: ULong, i: Int): Boolean = isSet(bb, i)

fun occupancy(b: Bitboards): Occupancy {
    val w = b.WP or b.WN or b.WB or b.WR or b.WQ or b.WK
    val bl = b.BP or b.BN or b.BB or b.BR or b.BQ or b.BK
    return Occupancy(w, bl, w or bl)
}

/** Trả về (Side, Type) ở ô index nếu có. Type: 'K','Q','R','B','N','P' */
fun pieceAt(b: Bitboards, index: Int): Pair<Side, Char>? {
    val m = bitAt(index)
    return when {
        (b.WK and m) != 0UL -> Side.WHITE to 'K'
        (b.WQ and m) != 0UL -> Side.WHITE to 'Q'
        (b.WR and m) != 0UL -> Side.WHITE to 'R'
        (b.WB and m) != 0UL -> Side.WHITE to 'B'
        (b.WN and m) != 0UL -> Side.WHITE to 'N'
        (b.WP and m) != 0UL -> Side.WHITE to 'P'
        (b.BK and m) != 0UL -> Side.BLACK to 'K'
        (b.BQ and m) != 0UL -> Side.BLACK to 'Q'
        (b.BR and m) != 0UL -> Side.BLACK to 'R'
        (b.BB and m) != 0UL -> Side.BLACK to 'B'
        (b.BN and m) != 0UL -> Side.BLACK to 'N'
        (b.BP and m) != 0UL -> Side.BLACK to 'P'
        else -> null
    }
}

private fun onBoard(r: Int, c: Int) = r in 0..7 && c in 0..7

fun kingMoves(from: Int, own: ULong): ULong {
    val (r0, c0) = rowColFromIndex(from)
    var moves = 0UL
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val r = r0 + dr; val c = c0 + dc
        if (!onBoard(r, c)) continue
        val i = indexFromRowCol(r, c)
        if (!has(own, i)) moves = moves or bitAt(i)
    }
    return moves
}

fun knightMoves(from: Int, own: ULong): ULong {
    val (r0, c0) = rowColFromIndex(from)
    var moves = 0UL
    val deltas = arrayOf(
        -2 to -1, -2 to +1, -1 to -2, -1 to +2,
        +1 to -2, +1 to +2, +2 to -1, +2 to +1
    )
    for ((dr, dc) in deltas) {
        val r = r0 + dr; val c = c0 + dc
        if (!onBoard(r, c)) continue
        val i = indexFromRowCol(r, c)
        if (!has(own, i)) moves = moves or bitAt(i)
    }
    return moves
}

private fun slide(from: Int, own: ULong, opp: ULong, dirs: Array<Pair<Int, Int>>): ULong {
    val (r0, c0) = rowColFromIndex(from)
    var res = 0UL
    for ((dr, dc) in dirs) {
        var r = r0; var c = c0
        while (true) {
            r += dr; c += dc
            if (!onBoard(r, c)) break
            val i = indexFromRowCol(r, c)
            if (has(own, i)) break
            res = res or bitAt(i)
            if (has(opp, i)) break
        }
    }
    return res
}

fun rookMoves(from: Int, own: ULong, opp: ULong): ULong =
    slide(from, own, opp, arrayOf(0 to +1, 0 to -1, +1 to 0, -1 to 0))

fun bishopMoves(from: Int, own: ULong, opp: ULong): ULong =
    slide(from, own, opp, arrayOf(+1 to +1, +1 to -1, -1 to +1, -1 to -1))

fun queenMoves(from: Int, own: ULong, opp: ULong): ULong =
    rookMoves(from, own, opp) or bishopMoves(from, own, opp)

/** Pawn: đi thẳng 1 ô; ăn chéo 1 ô. Không 2 ô, không en passant, không phong cấp. */
private fun pawnMoves(from: Int, side: Side, occ: Occupancy): ULong {
    val (r0, c0) = rowColFromIndex(from)
    var res = 0UL
    when (side) {
        Side.WHITE -> {
            val rf = r0 - 1
            if (rf >= 0) {
                val iF = indexFromRowCol(rf, c0)
                if (!isSet(occ.all, iF)) res = res or bitAt(iF)
            }
            for ((r, c) in arrayOf(r0 - 1 to c0 - 1, r0 - 1 to c0 + 1)) {
                if (!onBoard(r, c)) continue
                val i = indexFromRowCol(r, c)
                if (isSet(occ.black, i)) res = res or bitAt(i)
            }
        }
        Side.BLACK -> {
            val rf = r0 + 1
            if (rf <= 7) {
                val iF = indexFromRowCol(rf, c0)
                if (!isSet(occ.all, iF)) res = res or bitAt(iF)
            }
            for ((r, c) in arrayOf(r0 + 1 to c0 - 1, r0 + 1 to c0 + 1)) {
                if (!onBoard(r, c)) continue
                val i = indexFromRowCol(r, c)
                if (isSet(occ.white, i)) res = res or bitAt(i)
            }
        }
    }
    return res
}

/** API chính: bitboard ô đích (pseudo-legal) cho ô fromIndex. */
fun movesForSquare(board: Bitboards, fromIndex: Int): ULong {
    val who = pieceAt(board, fromIndex) ?: return 0UL
    val (side, type) = who
    val occ = occupancy(board)
    val own = if (side == Side.WHITE) occ.white else occ.black
    val all = occ.all

    return when (type) {
        'K' -> kingMoves(fromIndex, own)
        'Q' -> queenMoves(fromIndex, own, all)
        'R' -> rookMoves(fromIndex, own, all)
        'B' -> bishopMoves(fromIndex, own, all)
        'N' -> knightMoves(fromIndex, own)
        'P' -> {
            // TỐT: thêm logic **đi 2 ô lần đầu** vào mask cơ bản
            val (r0, c0) = rowColFromIndex(fromIndex)
            val dir = if (side == Side.WHITE) -1 else +1
            var mask = 0UL

            // 1 ô thẳng nếu trống
            val r1 = r0 + dir
            if (r1 in 0..7) {
                val i1 = indexFromRowCol(r1, c0)
                if (!isSet(all, i1)) {
                    mask = mask or bitAt(i1)
                    // 2 ô ở hàng xuất phát (nếu cả 2 ô trống)
                    val startRank = if (side == Side.WHITE) 6 else 1
                    if (r0 == startRank) {
                        val r2 = r0 + 2 * dir
                        val i2 = indexFromRowCol(r2, c0)
                        if (!isSet(all, i2)) mask = mask or bitAt(i2)
                    }
                }
            }
            // Ăn chéo (cơ bản, không EP)
            for (dc in intArrayOf(-1, +1)) {
                val rr = r0 + dir
                val cc = c0 + dc
                if (rr in 0..7 && cc in 0..7) {
                    val i = indexFromRowCol(rr, cc)
                    val targetHasOpp = if (side == Side.WHITE) isSet(occ.black, i) else isSet(occ.white, i)
                    if (targetHasOpp) mask = mask or bitAt(i)
                }
            }
            mask
        }
        else -> 0UL
    }
}

/**
 * Kiểm tra xem nước đi có phải là capture không
 */
fun isCapture(board: Bitboards, from: Int, to: Int): Boolean {
    return pieceAt(board, to) != null
}

/**
 * Kiểm tra xem nước đi có gây ra chiếu vua không
 */
fun causesCheck(board: Bitboards, from: Int, to: Int, movingSide: Side): Boolean {
    val newBoard = applyMoveInternal(board, from, to)
    val oppSide = if (movingSide == Side.WHITE) Side.BLACK else Side.WHITE
    val oppKingBB = if (oppSide == Side.WHITE) newBoard.WK else newBoard.BK
    val oppKingSq = squaresFrom(oppKingBB).firstOrNull() ?: return false
    
    // Kiểm tra xem vua đối phương có bị tấn công không
    val occ = occupancy(newBoard)
    val ownPieces = if (movingSide == Side.WHITE) occ.white else occ.black
    
    // Kiểm tra từ tất cả quân của bên vừa đi
    for (sq in squaresFrom(ownPieces)) {
        val piece = pieceAt(newBoard, sq) ?: continue
        if (piece.first != movingSide) continue
        
        val attackMask = when (piece.second) {
            'K' -> kingMoves(sq, ownPieces)
            'Q' -> queenMoves(sq, ownPieces, occ.all)
            'R' -> rookMoves(sq, ownPieces, occ.all)
            'B' -> bishopMoves(sq, ownPieces, occ.all)
            'N' -> knightMoves(sq, ownPieces)
            'P' -> {
                val (r, c) = rowColFromIndex(sq)
                val dir = if (movingSide == Side.WHITE) -1 else +1
                var pawnAttacks = 0UL
                for (dc in intArrayOf(-1, +1)) {
                    val rr = r + dir
                    val cc = c + dc
                    if (rr in 0..7 && cc in 0..7) {
                        pawnAttacks = pawnAttacks or bitAt(indexFromRowCol(rr, cc))
                    }
                }
                pawnAttacks
            }
            else -> 0UL
        }
        
        if (isSet(attackMask, oppKingSq)) return true
    }
    
    return false
}

/**
 * Áp dụng 1 nước đi cơ bản (phiên bản nội bộ) - không xử lý âm thanh
 */
private fun applyMoveInternal(board: Bitboards, from: Int, to: Int): Bitboards {
    val who = pieceAt(board, from) ?: return board
    val (side, type) = who

    // Clear quân bị ăn ở ô đích
    var nb = board.copy(
        WP = clearAt(board.WP, to), WN = clearAt(board.WN, to), WB = clearAt(board.WB, to),
        WR = clearAt(board.WR, to), WQ = clearAt(board.WQ, to), WK = clearAt(board.WK, to),
        BP = clearAt(board.BP, to), BN = clearAt(board.BN, to), BB = clearAt(board.BB, to),
        BR = clearAt(board.BR, to), BQ = clearAt(board.BQ, to), BK = clearAt(board.BK, to)
    )

    fun mv(bb: ULong): ULong = setAt(clearAt(bb, from), to)
    nb = when (side) {
        Side.WHITE -> when (type) {
            'P' -> {
                // Auto-queen nếu tới hàng 8 (để tránh kẹt UI)
                val (r, _) = rowColFromIndex(to)
                if (r == 0) {
                    nb.copy(WP = clearAt(nb.WP, from), WQ = setAt(nb.WQ, to))
                } else nb.copy(WP = mv(nb.WP))
            }
            'N' -> nb.copy(WN = mv(nb.WN))
            'B' -> nb.copy(WB = mv(nb.WB))
            'R' -> nb.copy(WR = mv(nb.WR))
            'Q' -> nb.copy(WQ = mv(nb.WQ))
            'K' -> nb.copy(WK = mv(nb.WK))
            else -> nb
        }
        Side.BLACK -> when (type) {
            'P' -> {
                val (r, _) = rowColFromIndex(to)
                if (r == 7) {
                    nb.copy(BP = clearAt(nb.BP, from), BQ = setAt(nb.BQ, to))
                } else nb.copy(BP = mv(nb.BP))
            }
            'N' -> nb.copy(BN = mv(nb.BN))
            'B' -> nb.copy(BB = mv(nb.BB))
            'R' -> nb.copy(BR = mv(nb.BR))
            'Q' -> nb.copy(BQ = mv(nb.BQ))
            'K' -> nb.copy(BK = mv(nb.BK))
            else -> nb
        }
    }
    return nb
}

/**
 * Áp dụng 1 nước đi cơ bản với xử lý âm thanh (không EP/castling/promotion lựa chọn).
 * — Dùng cho demo ChessBoardBitboard hiện tại.
 * — Nếu muốn "đúng luật", hãy dùng applyMove(GameState, Move) trong MoveEngine.kt.
 */
fun applyMove(
    board: Bitboards, 
    from: Int, 
    to: Int, 
    soundManager: ChessSoundManager? = null,
    isGameEnd: Boolean = false
): Bitboards {
    val who = pieceAt(board, from) ?: return board
    val (side, type) = who
    
    // Phát hiện các trường hợp đặc biệt để phát âm thanh
    val isCapture = isCapture(board, from, to)
    val isCheck = causesCheck(board, from, to, side)
    
    // Phát âm thanh phù hợp
    soundManager?.playMoveSound(
        isCapture = isCapture,
        isCheck = isCheck,
        isGameEnd = isGameEnd
    )
    
    // Áp dụng nước đi
    return applyMoveInternal(board, from, to)
}

/**
 * Áp dụng nước đi với khả năng chỉ định quân phong cấp và xử lý âm thanh.
 * — Dùng cho ChessBoardBitboard khi cần phong cấp thủ công.
 */
fun applyMove(
    board: Bitboards, 
    from: Int, 
    to: Int, 
    promotionPiece: Char? = null,
    soundManager: ChessSoundManager? = null,
    isGameEnd: Boolean = false
): Bitboards {
    val who = pieceAt(board, from) ?: return board
    val (side, type) = who
    
    // Phát hiện các trường hợp đặc biệt để phát âm thanh
    val isCapture = isCapture(board, from, to)
    val isCheck = causesCheck(board, from, to, side)
    
    // Phát âm thanh phù hợp
    soundManager?.playMoveSound(
        isCapture = isCapture,
        isCheck = isCheck,
        isGameEnd = isGameEnd
    )

    // Clear quân bị ăn ở ô đích
    var nb = board.copy(
        WP = clearAt(board.WP, to), WN = clearAt(board.WN, to), WB = clearAt(board.WB, to),
        WR = clearAt(board.WR, to), WQ = clearAt(board.WQ, to), WK = clearAt(board.WK, to),
        BP = clearAt(board.BP, to), BN = clearAt(board.BN, to), BB = clearAt(board.BB, to),
        BR = clearAt(board.BR, to), BQ = clearAt(board.BQ, to), BK = clearAt(board.BK, to)
    )

    fun mv(bb: ULong): ULong = setAt(clearAt(bb, from), to)
    nb = when (side) {
        Side.WHITE -> when (type) {
            'P' -> {
                val (r, _) = rowColFromIndex(to)
                if (r == 0) {
                    // Phong cấp - sử dụng promotionPiece nếu có, không thì mặc định Queen
                    val piece = promotionPiece?.uppercaseChar() ?: 'Q'
                    nb = nb.copy(WP = clearAt(nb.WP, from))
                    when (piece) {
                        'Q' -> nb.copy(WQ = setAt(nb.WQ, to))
                        'R' -> nb.copy(WR = setAt(nb.WR, to))
                        'B' -> nb.copy(WB = setAt(nb.WB, to))
                        'N' -> nb.copy(WN = setAt(nb.WN, to))
                        else -> nb.copy(WQ = setAt(nb.WQ, to))
                    }
                } else nb.copy(WP = mv(nb.WP))
            }
            'N' -> nb.copy(WN = mv(nb.WN))
            'B' -> nb.copy(WB = mv(nb.WB))
            'R' -> nb.copy(WR = mv(nb.WR))
            'Q' -> nb.copy(WQ = mv(nb.WQ))
            'K' -> nb.copy(WK = mv(nb.WK))
            else -> nb
        }
        Side.BLACK -> when (type) {
            'P' -> {
                val (r, _) = rowColFromIndex(to)
                if (r == 7) {
                    // Phong cấp - sử dụng promotionPiece nếu có, không thì mặc định Queen
                    val piece = promotionPiece?.uppercaseChar() ?: 'Q'
                    nb = nb.copy(BP = clearAt(nb.BP, from))
                    when (piece) {
                        'Q' -> nb.copy(BQ = setAt(nb.BQ, to))
                        'R' -> nb.copy(BR = setAt(nb.BR, to))
                        'B' -> nb.copy(BB = setAt(nb.BB, to))
                        'N' -> nb.copy(BN = setAt(nb.BN, to))
                        else -> nb.copy(BQ = setAt(nb.BQ, to))
                    }
                } else nb.copy(BP = mv(nb.BP))
            }
            'N' -> nb.copy(BN = mv(nb.BN))
            'B' -> nb.copy(BB = mv(nb.BB))
            'R' -> nb.copy(BR = mv(nb.BR))
            'Q' -> nb.copy(BQ = mv(nb.BQ))
            'K' -> nb.copy(BK = mv(nb.BK))
            else -> nb
        }
    }
    return nb
}