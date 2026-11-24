package com.petbattle.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Dibuja una imagen deformada por el viento: cada píxel se desplaza en horizontal según un mapa
 * de pesos (canal rojo = árboles, que se mecen despacio; canal verde = hierba, que ondea en
 * olas que recorren el prado). El canal azul dice cuánto sobresale la hierba, para repintar
 * la que queda delante de una mascota. Ver [WIND_SHADER_SOURCE].
 */
interface WindPainter {
    /**
     * Pincel que pinta, sobre el lienzo empezando en (0, 0), la imagen a partir del píxel
     * ([srcLeft], [srcTop]) y con [srcPerDst] píxeles de imagen por píxel de lienzo, en el
     * instante [time] (segundos).
     *
     * Con [grassFrontOf] >= 0 (una altura del suelo, en píxeles de imagen) solo pinta la hierba
     * que nace por delante de ese suelo y sobresale por encima: lo que debe tapar a una
     * mascota posada (o volando bajo) sobre él.
     */
    fun brush(srcLeft: Float, srcTop: Float, srcPerDst: Float, time: Float, grassFrontOf: Float = -1f): ShaderBrush
}

/**
 * Crea el pintor de viento para [image] con su mapa de pesos [wind] (cualquier tamaño: se
 * muestrea suavizado). Devuelve null si la plataforma no admite shaders en tiempo de
 * ejecución (Android anterior a 13): entonces la imagen se dibuja quieta y la hierba no tapa.
 */
expect fun createWindPainter(image: ImageBitmap, wind: ImageBitmap): WindPainter?

/**
 * Shader de viento, válido tanto en SkSL (escritorio) como en AGSL (Android 13+).
 * Desplazamientos en píxeles de imagen. El viento sopla hacia la izquierda, como avanzan las
 * nubes: las copas se inclinan un poco hacia ese lado y las olas de la hierba van en esa
 * dirección. Unas ráfagas lentas modulan la fuerza a lo largo del prado.
 *
 * Hierba delante ([grassFrontOf] >= 0): un píxel del prado a una distancia d por encima de la
 * línea del suelo de la mascota la tapa si la hierba sobresale más de d. Lo que sobresale
 * sale del canal azul (crece hacia la cámara) y de la propia textura: los trazos claros (hojas
 * iluminadas) sobresalen más que los huecos oscuros, así que la mascota se ve entre las
 * briznas en lugar de cortada en recto.
 */
const val WIND_SHADER_SOURCE = """
uniform shader image;
uniform shader wind;
uniform float2 srcOrigin;
uniform float srcPerDst;
uniform float2 windScale;
uniform float time;
uniform float grassFrontOf;

float luma(half4 c) { return dot(float3(c.rgb), float3(0.3, 0.59, 0.11)); }

half4 main(float2 p) {
    float2 uv = srcOrigin + p * srcPerDst;
    half4 w = wind.eval(uv * windScale);
    float gust = 0.55 + 0.45 * sin(time * 0.37 + uv.x * 0.003);
    float tree = w.r * gust * (-1.2 + 4.5 * sin(time * 1.1 + uv.x * 0.004) + 1.4 * sin(time * 2.3 + uv.y * 0.02));
    float grass = w.g * gust * (2.6 * sin(time * 2.2 + uv.x * 0.025 + uv.y * 0.05) + 1.1 * sin(time * 3.7 + uv.x * 0.07 - uv.y * 0.03));
    float2 s = float2(uv.x - tree - grass, uv.y);
    half4 c = image.eval(s);
    if (grassFrontOf < 0.0) return c;

    float above = grassFrontOf - uv.y;
    if (above < 0.0 || w.b < 0.004) return half4(0.0);
    // Claridad respecto al entorno: hojas iluminadas (+) frente a huecos en sombra (-).
    float around = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        around += luma(image.eval(s + float2(cos(a), sin(a)) * 9.0));
    }
    float bright = clamp((luma(c) - around / 8.0) * 6.0 + 0.5, 0.0, 1.0);
    float height = w.b * 64.0 * (0.3 + 1.1 * bright);
    return c * half(smoothstep(above - 2.0, above + 2.0, height));
}
"""
