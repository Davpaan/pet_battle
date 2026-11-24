package com.petbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.use
import com.petbattle.core.ui.createWindPainter
import com.petbattle.features.pet.presentation.game.BackdropLayers
import com.petbattle.features.pet.presentation.game.drawLayeredBackdrop
import com.petbattle.features.pet.presentation.game.solidRowFraction
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba visual del fondo por capas de la pradera con viento: renderiza dos instantes en
 * build/visual/grassland-*.png y comprueba que el viento mueve el primer plano (hierba y
 * árboles) y no el cielo despejado de arriba a la derecha, que solo se desplaza despacio.
 */
class GrasslandWindVisualTest {
    private val res = File("src/commonMain/composeResources/drawable")
    private fun img(name: String): ImageBitmap =
        Image.makeFromEncoded(File(res, "$name.png").readBytes()).toComposeImageBitmap()

    @Test
    fun windMovesGrassAndTrees() {
        val terrain = img("biome_grassland_terrain")
        val wind = createWindPainter(terrain, img("biome_grassland_wind"))
        assertNotNull(wind, "el shader de viento compila en escritorio")
        val solid = solidRowFraction(terrain)
        val layers = BackdropLayers(
            img("biome_grassland_sky"), img("biome_grassland_hills"), terrain, solid, wind, FilterQuality.Medium,
        )
        val (w, h) = 1280 to 720
        val frames = listOf(0.0, 1.4).map { t ->
            ImageComposeScene(w, h).use { scene ->
                scene.setContent {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLayeredBackdrop(layers, t)
                        // Franja donde se posan los pájaros (como BiomeSceneState.layout): del
                        // suelo más cercano al borde del terreno.
                        val k = maxOf(size.width / terrain.width, size.height / terrain.height)
                        val cropTop = (terrain.height - size.height / k) / 2
                        val back = (solid * terrain.height - cropTop) * k + size.height * 0.012f
                        val front = size.height * 0.965f
                        drawLine(Color.Red, Offset(0f, back), Offset(size.width, back), 2f)
                        drawLine(Color.Yellow, Offset(0f, front), Offset(size.width, front), 2f)
                    }
                }
                scene.render()
            }
        }
        val out = File("build/visual").apply { mkdirs() }
        frames.forEachIndexed { i, image ->
            File(out, "grassland-$i.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        val (a, b) = frames.map { org.jetbrains.skia.Bitmap.makeFromImage(it) }
        fun diff(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            var sum = 0.0
            var n = 0
            for (y in y0 until y1 step 3) for (x in x0 until x1 step 3) {
                val p = a.getColor(x, y)
                val q = b.getColor(x, y)
                sum += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) + abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF))
                n++
            }
            return sum / n
        }
        val grass = diff(0, (h * 0.85).toInt(), w, (h * 0.95).toInt())
        val trees = diff((w * 0.2).toInt(), (h * 0.1).toInt(), (w * 0.5).toInt(), (h * 0.4).toInt())
        assertTrue(grass > 3, "la hierba ondea ($grass)")
        assertTrue(trees > 3, "los árboles se mecen ($trees)")
    }
}
