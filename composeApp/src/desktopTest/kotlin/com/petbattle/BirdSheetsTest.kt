package com.petbattle

import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cada hoja de pájaro casa con lo que declara BirdSheet: tiene dibujo en todos los frames de
 * cada animación (si no, el pájaro parpadearía al llegar a un frame vacío) y mide lo justo.
 */
class BirdSheetsTest {
    private val res = File("src/commonMain/composeResources/drawable")

    private fun sheetOf(species: PetSpecies): Bitmap {
        val name = "pet_" + species.name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }
        return Bitmap.makeFromImage(Image.makeFromEncoded(File(res, "$name.png").readBytes()))
    }

    /** Si la casilla ([col], [row]) tiene algún píxel visible (muestreando cada 4 px). */
    private fun Bitmap.hasDrawing(col: Int, row: Int): Boolean {
        val s = BirdSheet.FRAME_SIZE
        for (y in row * s until (row + 1) * s step 4) for (x in col * s until (col + 1) * s step 4) {
            if ((getColor(x, y) ushr 24) > 40) return true
        }
        return false
    }

    @Test
    fun everyDeclaredFrameHasDrawing() {
        for (species in PetSpecies.adults) {
            val sheet = sheetOf(species)
            val rows = listOf(
                BirdSheet.FLY_ROW, BirdSheet.IDLE_ROW, BirdSheet.HOP_ROW,
                BirdSheet.LOOK_UP_ROW, BirdSheet.REST_ROW, BirdSheet.SLEEP_ROW,
            )
            val columns = rows.maxOf { BirdSheet.frames(it, species) }
            assertEquals(columns * BirdSheet.FRAME_SIZE, sheet.width, "$species: ancho de la hoja")
            for (row in rows) for (col in 0 until BirdSheet.frames(row, species)) {
                assertTrue(sheet.hasDrawing(col, row), "$species: frame $col de la fila $row vacío")
            }
        }
    }

    @Test
    fun jayHasTheSmootherAnimations() {
        assertEquals(12, BirdSheet.frames(BirdSheet.FLY_ROW, PetSpecies.blueJay))
        assertEquals(16, BirdSheet.frames(BirdSheet.IDLE_ROW, PetSpecies.blueJay))
        // Mismo ciclo que las demás especies, con frames más cortos.
        val t = BirdSheet.cycleSeconds(BirdSheet.IDLE_ROW, PetSpecies.blueJay) / 2
        assertEquals(8, BirdSheet.loopFrame(BirdSheet.IDLE_ROW, PetSpecies.blueJay, t + 1e-6))
        assertEquals(3, BirdSheet.loopFrame(BirdSheet.IDLE_ROW, PetSpecies.cardinal, t + 1e-6))
        // Mirar arriba: de una sola vez, se queda en el último frame.
        assertEquals(15, BirdSheet.onceFrame(BirdSheet.LOOK_UP_ROW, PetSpecies.blueJay, 99.0))
    }
}
