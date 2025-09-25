package com.example.chess.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.example.chess.R
import com.example.chess.model.Side

/* ----------------------------- Piece resources ---------------------------- */
fun resForPiece(code: String): Int = when (code) {
    "wk" -> R.drawable.wk; "wq" -> R.drawable.wq; "wr" -> R.drawable.wr
    "wb" -> R.drawable.wb; "wn" -> R.drawable.wn; "wp" -> R.drawable.wp
    "bk" -> R.drawable.bk; "bq" -> R.drawable.bq; "br" -> R.drawable.br
    "bb" -> R.drawable.bb; "bn" -> R.drawable.bn; "bp" -> R.drawable.bp
    else -> error("Unknown piece code: $code")
}

@Composable
fun piecePainter(side: Side, pieceType: Char): Painter {
    val sideChar = if (side == Side.WHITE) "w" else "b"
    val typeChar = pieceType.lowercase()
    val code = "$sideChar$typeChar"
    return painterResource(resForPiece(code))
}
