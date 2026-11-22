package com.petbattle.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Dibuja una celda [frame]x[frame] (columna [col], fila [row]) de un spritesheet
 * en el cuadrado de lado [size] cuyo vértice superior izquierdo es [topLeft]. Por defecto
 * usa muestreo nearest-neighbour para que el pixel art se vea nítido; los sprites de alta
 * resolución (pájaros) pasan un [filterQuality] suavizado. [flipX] lo refleja respecto a su
 * propio centro.
 */
fun DrawScope.drawSpriteFrame(
    image: ImageBitmap,
    frame: Int,
    col: Int,
    row: Int,
    topLeft: Offset,
    size: Float,
    flipX: Boolean = false,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.None,
) {
    val dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
    val dstSize = IntSize(size.roundToInt(), size.roundToInt())
    val draw: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            srcOffset = IntOffset(col * frame, row * frame),
            srcSize = IntSize(frame, frame),
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
        )
    }
    if (flipX) {
        scale(-1f, 1f, pivot = Offset(topLeft.x + size / 2, topLeft.y + size / 2)) { draw() }
    } else {
        draw()
    }
}
