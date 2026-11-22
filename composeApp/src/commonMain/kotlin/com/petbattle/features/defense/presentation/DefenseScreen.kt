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
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_species.presentation.widgets.BirdFrame
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import org.jetbrains.compose.resources.imageResource
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Partida de tower defense con las mascotas adultas del bioma actual. El botón de salir
 * siempre está disponible. La XP y el
 * cansancio del combate van directamente a [PetController], que los guarda solo.
 */
@Composable
fun DefenseScreen(controller: PetController, onExit: () -> Unit, map: DefenseMapEntity = jungleCampMap) {
    PlatformBackHandler(onExit)

    val gameState by controller.gameState.collectAsState()
    // Solo combaten los adultos del bioma actual: los polluelos aún no saben luchar.
    val pets = gameState?.biomePets.orEmpty().filter { !it.species.isChick }
    val game = remember {
        DefenseGame(
            map = map,
            onEnemyKilledBy = {
                controller.awardXp(it, PetProgression.XP_PER_ENEMY)
                controller.awardGold(GameRules.GOLD_PER_ENEMY)
            },
            onPetFired = { controller.petFired(it) },
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
                    game.tapAt((pos - t.second) / t.first)
                }
            },
        ) {
            frame // redibuja en cada frame
            val (s, offset) = fit(size.width, size.height, map)
            translate(offset.x, offset.y) {
                scale(s, s, pivot = Offset.Zero) { drawDefense(game, sprites, text) }
            }
        }
        Hud(hud, pets, onSelect = game::selectPet, onStart = game::start, onExit = onExit, onRestart = game::restart)
    }
}

/** Ajuste con bandas del mapa completo: (escala, desplazamiento superior izquierdo). */
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
    onStart: () -> Unit,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        FilledTonalIconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Salir")
        }
        // Los marcadores del combate solo cuando ya ha empezado: la preparación queda despejada.
        if (hud.started) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudChip(Icons.Filled.Favorite, "${hud.lives}")
                HudChip(Icons.Filled.MonetizationOn, "+${hud.kills}") // 1 de oro por enemigo
                HudChip(Icons.Filled.Timer, "${hud.seconds}s")
                HudChip(Icons.Filled.Speed, "x${(hud.speedMultiplier * 10).roundToInt() / 10.0}")
            }
        }
        if (!hud.started) {
            // Preparación: colocar y mover pájaros, y "Empezar" cuando estés listo.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PetBar(hud, pets, onSelect, Modifier.weight(1f, fill = false))
                Button(
                    onClick = onStart,
                    enabled = hud.canStart,
                    modifier = Modifier.padding(start = 8.dp).height(56.dp),
                    // Desactivado (aún sin pájaros colocados) también debe verse sobre el mapa.
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Empezar", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else if (hud.gameOver) {
            Card(Modifier.align(Alignment.Center)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¡Te han superado!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Enemigos derrotados: ${hud.kills} · Oro ganado: ${hud.kills * GameRules.GOLD_PER_ENEMY} · Tiempo: ${hud.seconds}s")
                    Spacer(Modifier.height(16.dp))
                    Row {
                        TextButton(onClick = onExit) { Text("Salir") }
                        Spacer(Modifier.width(8.dp))
                        // Vuelve a la preparación con los pájaros donde estaban.
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
 * Mascotas disponibles en la preparación. La seleccionada se coloca con el próximo toque en
 * el mapa; elegir una ya colocada la recoge para recolocarla.
 */
@Composable
private fun PetBar(hud: DefenseHudState, pets: List<PetEntity>, onSelect: (String) -> Unit, modifier: Modifier) {
    val editing = pets.firstOrNull { it.id == hud.editingId }
    val selected = pets.firstOrNull { it.id == hud.selectedId }
    Card(modifier) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    pets.isEmpty() -> "Los polluelos no combaten: aliméntalos para que crezcan"
                    editing != null -> "Toca la hierba para mover a ${editing.name} (tócalo otra vez para dejarlo)"
                    selected != null -> "Toca una zona verde para colocar a ${selected.name}"
                    else -> "Toca un pájaro para moverlo, o pulsa Empezar"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pets.forEach { pet ->
                    val placed = pet.id in hud.placedIds
                    val active = hud.selectedId == pet.id || hud.editingId == pet.id
                    val scheme = MaterialTheme.colorScheme
                    Surface(
                        onClick = { onSelect(pet.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) scheme.primaryContainer else scheme.surfaceContainerHighest,
                        border = if (active) BorderStroke(2.dp, scheme.primary) else null,
                        modifier = Modifier.alpha(if (placed && hud.editingId != pet.id) 0.6f else 1f),
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
