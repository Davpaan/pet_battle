package com.petbattle.core.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

actual fun createWindPainter(image: ImageBitmap, wind: ImageBitmap): WindPainter? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { AgslWindPainter(image, wind) }.getOrNull()
    } else {
        null
    }

/** En Android el shader es AGSL (RuntimeShader), disponible desde Android 13. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslWindPainter(image: ImageBitmap, wind: ImageBitmap) : WindPainter {
    private val shader = RuntimeShader(WIND_SHADER_SOURCE).apply {
        setInputShader("image", image.linearShader())
        setInputShader("wind", wind.linearShader())
        setFloatUniform("windScale", wind.width.toFloat() / image.width, wind.height.toFloat() / image.height)
    }
    private val brush = ShaderBrush(shader)

    override fun brush(srcLeft: Float, srcTop: Float, srcPerDst: Float, time: Float): ShaderBrush {
        // Los uniforms se leen al dibujar: basta con actualizarlos en el mismo shader.
        shader.setFloatUniform("srcOrigin", srcLeft, srcTop)
        shader.setFloatUniform("srcPerDst", srcPerDst)
        shader.setFloatUniform("time", time)
        return brush
    }

    /** Muestreo suavizado (las capas son ilustraciones, no pixel art) y bordes repetidos. */
    private fun ImageBitmap.linearShader() =
        BitmapShader(asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
}
