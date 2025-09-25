package com.example.chess.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/* ------------------------------- Composables ------------------------------ */
/** Composable Piece (tách riêng): vẽ 1 quân cờ ở (row,col) với kích thước 1 ô. */
@Composable
fun Piece(
    code: String,
    row: Int,
    col: Int,
    square: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    @DrawableRes val resId = resForPiece(code)
    Image(
        painter = painterResource(resId),
        contentDescription = code,
        contentScale = contentScale,
        modifier = modifier
            .size(square)
            .offset(x = square * col, y = square * row)
    )
}
