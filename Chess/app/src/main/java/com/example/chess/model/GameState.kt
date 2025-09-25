package com.example.chess.model

/** Trạng thái ván cờ để xử lý các nước đặc biệt đúng luật. */
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
)

data class Move(
    val from: Int,
    val to: Int,
    val promo: Char? = null,           // 'Q','R','B','N' (nếu phong cấp)
    val isCastle: Boolean = false,
    val isEnPassant: Boolean = false,
    val isDoublePawnPush: Boolean = false
)

/** Trạng thái đầy đủ cho bộ sinh move “đúng luật” */
data class GameState(
    val boards: Bitboards,
    val sideToMove: Side = Side.WHITE,
    val castling: CastlingRights = CastlingRights(),
    val enPassantSquare: Int? = null    // ô đích có thể EP (sau khi đối thủ đẩy tốt 2 ô)
)
