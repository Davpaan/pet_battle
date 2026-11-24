package com.petbattle.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

actual fun createWindPainter(image: ImageBitmap, wind: ImageBitmap): WindPainter? =
    runCatching { SkiaWindPainter(image, wind) }.getOrNull()

/** En escritorio el shader se compila con Skia (SkSL). */
private class SkiaWindPainter(image: ImageBitmap, wind: ImageBitmap) : WindPainter {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(WIND_SHADER_SOURCE)).apply {
        child("image", image.linearShader())
        child("wind", wind.linearShader())
        uniform("windScale", wind.width.toFloat() / image.width, wind.height.toFloat() / image.height)
    }

    override fun brush(srcLeft: Float, srcTop: Float, srcPerDst: Float, time: Float): ShaderBrush {
        builder.uniform("srcOrigin", srcLeft, srcTop)
        builder.uniform("srcPerDst", srcPerDst)
        builder.uniform("time", time)
        return ShaderBrush(builder.makeShader())
    }

    /** Muestreo suavizado (las capas son ilustraciones, no pixel art) y bordes repetidos. */
    private fun ImageBitmap.linearShader() = Image.makeFromBitmap(asSkiaBitmap())
        .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null)
}
