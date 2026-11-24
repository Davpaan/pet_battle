package com.petbattle.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Dibuja una imagen deformada por el viento: cada píxel se desplaza en horizontal según un mapa
 * de pesos (canal rojo = árboles, que se mecen despacio; canal verde = hierba, que ondea en
 * olas que recorren el prado). Ver [WIND_SHADER_SOURCE].
 */
interface WindPainter {
    /**
     * Pincel que pinta, sobre un rectángulo que empieza en (0, 0) del lienzo, la imagen a partir
     * del píxel ([srcLeft], [srcTop]) y con [srcPerDst] píxeles de imagen por píxel de lienzo,
     * en el instante [time] (segundos).
     */
    fun brush(srcLeft: Float, srcTop: Float, srcPerDst: Float, time: Float): ShaderBrush
}

/**
 * Crea el pintor de viento para [image] con su mapa de pesos [wind] (cualquier tamaño: se
 * muestrea suavizado). Devuelve null si la plataforma no admite shaders en tiempo de
 * ejecución (Android anterior a 13): entonces la imagen se dibuja quieta.
 */
expect fun createWindPainter(image: ImageBitmap, wind: ImageBitmap): WindPainter?

/**
 * Shader de viento, válido tanto en SkSL (escritorio) como en AGSL (Android 13+).
 * Desplazamientos en píxeles de imagen. El viento sopla hacia la izquierda, como avanzan las
 * nubes: las copas se inclinan un poco hacia ese lado y las olas de la hierba van en esa
 * dirección. Unas ráfagas lentas modulan la fuerza a lo largo del prado.
 */
const val WIND_SHADER_SOURCE = """
uniform shader image;
uniform shader wind;
uniform float2 srcOrigin;
uniform float srcPerDst;
uniform float2 windScale;
uniform float time;

half4 main(float2 p) {
    float2 uv = srcOrigin + p * srcPerDst;
    half4 w = wind.eval(uv * windScale);
    float gust = 0.55 + 0.45 * sin(time * 0.37 + uv.x * 0.003);
    float tree = w.r * gust * (-1.2 + 4.5 * sin(time * 1.1 + uv.x * 0.004) + 1.4 * sin(time * 2.3 + uv.y * 0.02));
    float grass = w.g * gust * (2.6 * sin(time * 2.2 + uv.x * 0.025 + uv.y * 0.05) + 1.1 * sin(time * 3.7 + uv.x * 0.07 - uv.y * 0.03));
    return image.eval(float2(uv.x - tree - grass, uv.y));
}
"""
