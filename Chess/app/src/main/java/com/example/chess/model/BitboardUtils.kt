
package com.example.chess.model

/* -------------------------- Bitboard helper utils -------------------------- */
fun bitAt(i: Int): ULong = 1UL shl i
fun isSet(bb: ULong, i: Int): Boolean = ((bb shr i) and 1UL) != 0UL

/** Xóa bit ở vị trí i. */
fun clearAt(bb: ULong, i: Int): ULong = bb and bitAt(i).inv()

/** Đặt bit ở vị trí i. */
fun setAt(bb: ULong, i: Int): ULong = bb or bitAt(i)