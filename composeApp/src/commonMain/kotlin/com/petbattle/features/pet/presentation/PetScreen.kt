package com.petbattle.features.pet.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petbattle.features.pet.presentation.game.BiomeScene
import com.petbattle.features.pet.presentation.widgets.ActionBar
import com.petbattle.features.pet.presentation.widgets.ActionBarWidth
import com.petbattle.features.pet.presentation.widgets.BiomePickerSheet
import com.petbattle.features.pet.presentation.widgets.FoodPickerSheet
import com.petbattle.features.pet.presentation.widgets.PetNameDialog
import com.petbattle.features.pet.presentation.widgets.TopBar
import com.petbattle.shared_features.pet_food.presentation.label
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.domain.FeedResult
import kotlinx.coroutines.delay

/**
 * Pantalla principal. El jugador elige un bioma (nunca una especie); en él cría polluelos que
 * crecen según lo que comen. Muestra la mascota activa del bioma, su estado y las acciones.
 * El único lugar donde se encuentran el controlador y la escena.
 */
@Composable
fun PetScreen(controller: PetController, onDefend: () -> Unit) {
    val roster by controller.roster.collectAsState()
    var biomePicker by remember { mutableStateOf<String?>(null) } // título de la hoja, null = cerrada
    var naming by remember { mutableStateOf(false) }
    var choosingFood by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) } // aviso tras ofrecer comida

    // El aviso desaparece solo al cabo de un rato.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_500)
            feedback = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        val current = roster
        val biome = current?.currentBiome
        val pet = current?.active
        when {
            current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            biome == null -> {
                Box(Modifier.fillMaxSize().padding(0.dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFFBFE6FF)) }
                    PromptCard(
                        title = "¡Bienvenido!",
                        text = "Elige el bioma donde criarás tus mascotas.",
                        button = "Elegir bioma",
                        icon = Icons.Filled.Landscape,
                        onClick = { biomePicker = "Elige un bioma" },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            else -> {
                BiomeScene(
                    biome = biome,
                    pets = current.biomePets,
                    activeId = current.activeId,
                    // Tocar una mascota la selecciona y la mima.
                    onPetTapped = { id ->
                        if (id != current.activeId) controller.switchTo(id)
                        controller.cuddle()
                    },
                    modifier = Modifier.fillMaxSize(),
                    walkEndInset = if (pet != null) ActionBarWidth else 0.dp,
                )
                if (pet == null) {
                    PromptCard(
                        title = "Este bioma está vacío",
                        text = "Cría un polluelo: lo que le des de comer decidirá en qué pájaro se convierte.",
                        button = "Nueva mascota",
                        icon = Icons.Filled.Egg,
                        onClick = { naming = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Row(Modifier.fillMaxSize().safeDrawingPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            biome = biome,
                            pet = pet,
                            onNewPet = { naming = true },
                            onChangeBiome = { biomePicker = "Cambiar de bioma" },
                            onRelease = { controller.release(it.id) },
                        )
                        Spacer(Modifier.weight(1f))
                        feedback?.let { FeedbackBubble(it, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) }
                    }
                    if (pet != null) {
                        ActionBar(
                            pet = pet,
                            onFeed = { choosingFood = true },
                            onToggleSleep = controller::toggleSleep,
                            onClean = controller::clean,
                            onDefend = onDefend,
                        )
                    }
                }
            }
        }

        val title = biomePicker
        if (title != null && current != null) {
            BiomePickerSheet(
                roster = current,
                title = title,
                onPicked = { picked ->
                    biomePicker = null
                    controller.enterBiome(picked)
                },
                onDismiss = { biomePicker = null },
            )
        }
        if (choosingFood && pet != null) {
            FoodPickerSheet(
                petName = pet.name,
                onPicked = { food ->
                    choosingFood = false
                    feedback = when (controller.feed(food)) {
                        FeedResult.eaten -> "¡A ${pet.name} le encanta! Ñam ñam"
                        FeedResult.rejected -> "${pet.name} rechaza ${food.label.lowercase()}"
                        FeedResult.grew -> {
                            val adult = controller.roster.value?.active?.species
                            "¡${pet.name} ha crecido! Ahora es: ${adult?.label ?: "?"}"
                        }
                        null -> null
                    }
                },
                onDismiss = { choosingFood = false },
            )
        }
        if (naming && biome != null) {
            PetNameDialog(
                biome = biome,
                submit = { name -> controller.hatch(name) },
                onDismiss = { naming = false },
            )
        }
    }
}

/** Aviso breve sobre la barra de acciones (p. ej. si le gustó la comida). */
@Composable
private fun FeedbackBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/** Tarjeta central con una llamada a la acción (primer bioma, bioma vacío). */
@Composable
private fun PromptCard(
    title: String,
    text: String,
    button: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.padding(24.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) {
                Icon(icon, contentDescription = null)
                Text("  $button")
            }
        }
    }
}
