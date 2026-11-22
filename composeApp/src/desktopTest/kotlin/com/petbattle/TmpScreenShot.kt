package com.petbattle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.core.time.DayPhase
import com.petbattle.features.pet.presentation.PetScreen
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class TmpScreenShot {
    @Test fun top() {
        val repo = LocalPetRepository(InMemoryKeyValueStore())
        val now = System.currentTimeMillis()
        val kai = PetEntity.newborn("Kai", PetSpecies.crow, now, id = "Kai", biome = Biome.grassland)
        runBlocking { repo.save(GameStateEntity.newGame.copy(pets = listOf(kai), activeId = "Kai", freeEggUsed = true)) }
        val c = PetController(repo, { System.currentTimeMillis() }, CoroutineScope(Dispatchers.Unconfined), autoTick = false, dayPhaseAt = { DayPhase.morning })
        runBlocking { c.load() }
        ImageComposeScene(1600, 720, Density(2f)).use { scene ->
            scene.setContent { MaterialTheme { PetScreen(c, onDefend = {}) } }
            var t = 0L
            repeat(30) { Thread.sleep(1); t += 16_666_666L; scene.render(t) }
            File("build/visual").mkdirs()
            File("build/visual/top.png").writeBytes(scene.render(t + 16_666_666L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
