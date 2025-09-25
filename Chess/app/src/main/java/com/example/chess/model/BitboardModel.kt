package com.example.chess.model

/* ----------------------------- Bitboard model ----------------------------- */
// Chuẩn bitboard: bit 0 = a1, bit 7 = h1, bit 56 = a8, bit 63 = h8 (tăng trái→phải, dưới→trên)
data class Bitboards(
    val WP: ULong, val WN: ULong, val WB: ULong, val WR: ULong, val WQ: ULong, val WK: ULong,
    val BP: ULong, val BN: ULong, val BB: ULong, val BR: ULong, val BQ: ULong, val BK: ULong
)

fun indexFromRowCol(row: Int, col: Int): Int = (7 - row) * 8 + col
fun rowColFromIndex(index: Int): Pair<Int, Int> = (7 - index / 8) to (index % 8)

fun bbOf(vararg idx: Int): ULong {
    var bb = 0UL
    for (i in idx) bb = bb or (1UL shl i)
    return bb
}

fun squaresFrom(bb: ULong): List<Int> {
    if (bb == 0UL) return emptyList()
    val out = ArrayList<Int>(16)
    var x = bb
    var i = 0
    while (i < 64) {
        if (((x shr i) and 1UL) != 0UL) out.add(i)
        i++
    }
    return out
}

/* Vị trí khởi đầu chuẩn */
fun initialBitboards(): Bitboards {
    // Pawns
    val wp = bbOf(*(0.until(8).map { indexFromRowCol(6, it) }.toIntArray()))
    val bp = bbOf(*(0.until(8).map { indexFromRowCol(1, it) }.toIntArray()))
    // Rooks
    val wr = bbOf(indexFromRowCol(7, 0), indexFromRowCol(7, 7))
    val br = bbOf(indexFromRowCol(0, 0), indexFromRowCol(0, 7))
    // Knights
    val wn = bbOf(indexFromRowCol(7, 1), indexFromRowCol(7, 6))
    val bn = bbOf(indexFromRowCol(0, 1), indexFromRowCol(0, 6))
    // Bishops
    val wb = bbOf(indexFromRowCol(7, 2), indexFromRowCol(7, 5))
    val bb = bbOf(indexFromRowCol(0, 2), indexFromRowCol(0, 5))
    // Queens
    val wq = bbOf(indexFromRowCol(7, 3))
    val bq = bbOf(indexFromRowCol(0, 3))
    // Kings
    val wk = bbOf(indexFromRowCol(7, 4))
    val bk = bbOf(indexFromRowCol(0, 4))

    return Bitboards(
        WP = wp, WN = wn, WB = wb, WR = wr, WQ = wq, WK = wk,
        BP = bp, BN = bn, BB = bb, BR = br, BQ = bq, BK = bk
    )
}
