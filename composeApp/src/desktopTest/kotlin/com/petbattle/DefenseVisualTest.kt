package com.petbattle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.use
import com.petbattle.features.defense.domain.maps.jungleCampMap
import com.petbattle.features.defense.presentation.game.DefenseGame
import com.petbattle.features.defense.presentation.game.DefenseSprites
import com.petbattle.features.defense.presentation.game.TextPainter
import com.petbattle.features.defense.presentation.game.drawDefense
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test de humo de la escena de defensa: juega unos segundos con una mascota de cada
 * elemento y renderiza el frame fuera de pantalla en build/visual/defense-*.png,
 * de modo que se ejecute todo el renderer (sprites, overlays, proyectiles, texto).
 */
class DefenseVisualTest {
    private val res = File("src/commonMain/composeResources/drawable")
    private fun img(name: String): ImageBitmap =
        Image.makeFromEncoded(File(res, "$name.png").readBytes()).toComposeImageBitmap()

    private val sprites = DefenseSprites(
        map = img("map_jungle_camp"),
        slime = img("enemy_slime"),
        skills = PetElement.entries.associateWith { img("skill_${it.name}") },
        pets = PetSpecies.adults.associateWith { sp ->
            img("pet_" + sp.name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() })
        },
    )

    @Test
    fun setupPhaseLetsYouPlaceAndMovePetsUntilYouPressStart() {
        val game = DefenseGame(jungleCampMap, {}, {}, Random(1))
        val (a, b) = listOf(PetSpecies.crow to "A", PetSpecies.cardinal to "B").map { (s, n) -> PetEntity.newborn(n, s, 0) }
        game.updatePets(listOf(a, b))
        assertFalse(game.canStart)
        game.tapAt(Offset(1262f, 539f)) // coloca A (la primera seleccionada)
        game.tapAt(Offset(1000f, 540f)) // coloca B
        assertEquals(2, game.defenders.size)
        assertTrue(game.canStart)
        // Tocar a A la recoge; tocar hierba la mueve; tocarla otra vez sin mover la suelta.
        game.tapAt(Offset(1262f, 500f))
        assertEquals(a.id, game.editingId)
        game.tapAt(Offset(700f, 560f))
        assertNull(game.editingId)
        assertEquals(Offset(700f, 560f), game.defenders.getValue(a.id).foot)
        game.tapAt(Offset(700f, 520f))
        game.tapAt(Offset(700f, 520f))
        assertNull(game.editingId)
        // Antes de empezar no aparecen enemigos.
        repeat(120) { game.update(1.0 / 60) }
        assertTrue(game.enemies.isEmpty())
        game.start()
        assertTrue(game.running)
        // En combate ya no se puede mover a nadie.
        game.tapAt(Offset(700f, 520f))
        assertNull(game.editingId)
        repeat(120) { game.update(1.0 / 60) }
        assertTrue(game.enemies.isNotEmpty())
        // Reintentar vuelve a la preparación con los pájaros donde estaban.
        game.restart()
        assertFalse(game.started)
        assertEquals(Offset(700f, 560f), game.defenders.getValue(a.id).foot)
    }

    @Test
    fun rendersAFullRun() {
        var killed = 0
        var shots = 0
        val game = DefenseGame(jungleCampMap, { killed++ }, { shots++ }, Random(7))
        val pets = listOf(
            PetSpecies.cardinal to "Fuego", PetSpecies.woodThrush to "Agua",
            PetSpecies.hummingbird to "Aire", PetSpecies.crow to "Tierra",
        ).map { (s, n) ->
            val p = PetEntity.newborn(n, s, 0)
            p.copy(progression = p.progression.copy(level = 1))
        }
        game.updatePets(pets)
        val spots = listOf(Offset(1262f, 539f), Offset(1000f, 540f), Offset(700f, 560f), Offset(440f, 540f))
        pets.zip(spots).forEach { (p, at) -> game.selectPet(p.id); game.placePetAt(at) }
        assertTrue(game.defenders.size == 4, "all four pets placed")
        game.start()

        val out = File("build/visual").apply { mkdirs() }
        for ((i, seconds) in listOf(6, 14, 22).withIndex()) {
            while (game.time < seconds) game.update(1.0 / 60)
            ImageComposeScene(1528, 814).use { scene ->
                scene.setContent {
                    val text = TextPainter(rememberTextMeasurer())
                    Canvas(Modifier.fillMaxSize()) {
                        scale(size.width / game.map.width, Offset.Zero) { drawDefense(game, sprites, text) }
                    }
                }
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "defense-$i.png").writeBytes(png)
            }
        }
        assertTrue(shots > 0, "pets fired")
        assertTrue(killed > 0, "enemies were defeated")
    }
}
