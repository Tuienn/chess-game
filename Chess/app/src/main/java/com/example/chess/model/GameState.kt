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

/** Time control settings for chess clock */
data class TimeControl(
    val totalTimeMs: Long, // 1m=60000, 3m=180000, 5m=300000, 10m=600000, 30m=1800000
    val enabled: Boolean = true
) {
    companion object {
        val ONE_MINUTE = TimeControl(60_000, true)
        val THREE_MINUTES = TimeControl(180_000, true)
        val FIVE_MINUTES = TimeControl(300_000, true)
        val TEN_MINUTES = TimeControl(600_000, true)
        val THIRTY_MINUTES = TimeControl(1_800_000, true)
        val NO_LIMIT = TimeControl(0, false)
    }
}

/** Player timers for chess clock */
data class PlayerTimers(
    val whiteRemainingMs: Long,
    val blackRemainingMs: Long,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

/** Trạng thái đầy đủ cho bộ sinh move "đúng luật" */
data class GameState(
    val boards: Bitboards,
    val sideToMove: Side = Side.WHITE,
    val castling: CastlingRights = CastlingRights(),
    val enPassantSquare: Int? = null    // ô đích có thể EP (sau khi đối thủ đẩy tốt 2 ô)
)
