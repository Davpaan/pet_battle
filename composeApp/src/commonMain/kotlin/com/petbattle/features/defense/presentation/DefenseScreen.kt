package com.petbattle.features.defense.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.petbattle.core.ui.LandscapeWhileShown
import com.petbattle.core.ui.PlatformBackHandler
import com.petbattle.features.defense.domain.entities.DefenseMapEntity
import com.petbattle.features.defense.domain.maps.jungleCampMap
import com.petbattle.features.defense.presentation.game.DefenseGame
import com.petbattle.features.defense.presentation.game.DefenseHudState
import com.petbattle.features.defense.presentation.game.DefenseSprites
import com.petbattle.features.defense.presentation.game.TextPainter
import com.petbattle.features.defense.presentation.game.drawDefense
import com.petbattle.resources.Res
import com.petbattle.resources.enemy_slime
import com.petbattle.resources.map_jungle_camp
import com.petbattle.resources.skill_air
import com.petbattle.resources.skill_earth
import com.petbattle.resources.skill_fire
import com.petbattle.resources.skill_water
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_species.presentation.widgets.BirdFrame
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.PetStatusBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import org.jetbrains.compose.resources.imageResource
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tower-defense run with every pet the player has raised. Landscape while
 * open; the exit button is always available. XP and tiredness from fighting
 * go straight to [PetController], which auto-saves them.
 */
@Composable
fun DefenseScreen(controller: PetController, onExit: () -> Unit, map: DefenseMapEntity = jungleCampMap) {
    LandscapeWhileShown()
    PlatformBackHandler(onExit)

    val roster by controller.roster.collectAsState()
    val pets = roster?.pets.orEmpty()
    val game = remember {
        DefenseGame(
            map = map,
            onEnemyKilledBy = { controller.awardXp(it, PetProgression.XP_PER_ENEMY) },
            onPetFired = { controller.spendEnergy(it, PetStatusBalance.ENERGY_PER_SHOT) },
        )
    }
    LaunchedEffect(pets) { game.updatePets(pets) }

    val sprites = rememberDefenseSprites()
    val text = TextPainter(rememberTextMeasurer())
    var hud by remember { mutableStateOf(DefenseHudState()) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                game.update(min(dt, 0.1))
                hud = game.hud
                frame++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF26301E))) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(game) {
                detectTapGestures { pos ->
                    val t = fit(size.width.toFloat(), size.height.toFloat(), map)
                    game.placePetAt((pos - t.second) / t.first)
                }
            },
        ) {
            frame // redraw every frame
            val (s, offset) = fit(size.width, size.height, map)
            translate(offset.x, offset.y) {
                scale(s, s, pivot = Offset.Zero) { drawDefense(game, sprites, text) }
            }
        }
        Hud(hud, pets, onSelect = game::selectPet, onExit = onExit, onRestart = game::restart)
    }
}

/** Letterboxed fit of the whole map: (scale, top-left offset). */
private fun fit(w: Float, h: Float, map: DefenseMapEntity): Pair<Float, Offset> {
    val s = min(w / map.width, h / map.height)
    return s to Offset((w - map.width * s) / 2, (h - map.height * s) / 2)
}

@Composable
private fun rememberDefenseSprites(): DefenseSprites {
    val pets: Map<PetSpecies, ImageBitmap> = PetSpecies.entries.associateWith { imageResource(it.sheet) }
    return DefenseSprites(
        map = imageResource(Res.drawable.map_jungle_camp),
        slime = imageResource(Res.drawable.enemy_slime),
        skills = mapOf(
            PetElement.fire to imageResource(Res.drawable.skill_fire),
            PetElement.water to imageResource(Res.drawable.skill_water),
            PetElement.air to imageResource(Res.drawable.skill_air),
            PetElement.earth to imageResource(Res.drawable.skill_earth),
        ),
        pets = pets,
    )
}

@Composable
private fun Hud(
    hud: DefenseHudState,
    pets: List<PetEntity>,
    onSelect: (String) -> Unit,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        FilledTonalIconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Salir")
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudChip(Icons.Filled.Favorite, "${hud.lives}")
            HudChip(Icons.Filled.Bolt, "${hud.kills}")
            HudChip(Icons.Filled.Timer, "${hud.seconds}s")
            HudChip(Icons.Filled.Speed, "x${(hud.speedMultiplier * 10).roundToInt() / 10.0}")
        }
        if (!hud.gameOver) {
            PetBar(hud, pets, onSelect, Modifier.align(Alignment.BottomCenter))
        } else {
            Card(Modifier.align(Alignment.Center)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡Te han superado!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Enemigos derrotados: ${hud.kills} · Tiempo: ${hud.seconds}s")
                    Spacer(Modifier.height(16.dp))
                    Row {
                        TextButton(onClick = onExit) { Text("Salir") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onRestart) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudChip(icon: ImageVector, text: String) {
    Card {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(text)
        }
    }
}

/**
 * Raised pets available for the run. The selected one is placed by the next
 * tap on the map; placed pets are marked and can't be placed again.
 */
@Composable
private fun PetBar(hud: DefenseHudState, pets: List<PetEntity>, onSelect: (String) -> Unit, modifier: Modifier) {
    Card(modifier.padding(8.dp)) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hud.selectedId != null) "Toca una zona verde para colocar a la mascota elegida"
                else "Todas tus mascotas están en el mapa",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pets.forEach { pet ->
                    val placed = pet.id in hud.placedIds
                    val selected = hud.selectedId == pet.id
                    val scheme = MaterialTheme.colorScheme
                    Surface(
                        onClick = { onSelect(pet.id) },
                        enabled = !placed,
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
                        modifier = Modifier.alpha(if (placed) 0.45f else 1f),
                    ) {
                        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            BirdFrame(pet.species, 40.dp)
                            Column {
                                Text(pet.name, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${if (placed) "En el mapa" else "Nv. ${pet.skills.level}"} · ⚡${pet.status.energy.roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
