package com.petbattle.features.pet.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petbattle.features.pet.presentation.game.PetScene
import com.petbattle.features.pet.presentation.widgets.ActionBar
import com.petbattle.features.pet.presentation.widgets.PetNameDialog
import com.petbattle.features.pet.presentation.widgets.SpeciesPickerSheet
import com.petbattle.features.pet.presentation.widgets.StatusPanel
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.application.PetController

/**
 * Pantalla principal: la mascota activa en su pequeño mundo, su estado y las
 * acciones. El único lugar donde se encuentran el controlador y la escena.
 */
@Composable
fun PetScreen(controller: PetController, onDefend: () -> Unit) {
    val roster by controller.roster.collectAsState()
    var picker by remember { mutableStateOf<String?>(null) } // título de la hoja, null = cerrada
    var naming by remember { mutableStateOf<PetSpecies?>(null) }

    Box(Modifier.fillMaxSize()) {
        val current = roster
        val pet = current?.active
        when {
            current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            pet == null -> {
                Box(Modifier.fillMaxSize()) {
                    PetSceneBackgroundOnly()
                    FirstPetCard(onPick = { picker = "Tu primera mascota" }, modifier = Modifier.align(Alignment.Center))
                }
            }
            else -> {
                PetScene(pet, onPetTapped = controller::cuddle, modifier = Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    StatusPanel(pet, onChangeSpecies = { picker = "Elige tu mascota" })
                    Spacer(Modifier.weight(1f))
                    ActionBar(
                        pet = pet,
                        onFeed = controller::feed,
                        onToggleSleep = controller::toggleSleep,
                        onClean = controller::clean,
                        onDefend = onDefend,
                    )
                }
            }
        }

        val title = picker
        if (title != null && current != null) {
            SpeciesPickerSheet(
                roster = current,
                title = title,
                onPicked = { species ->
                    picker = null
                    // Vuelve a la mascota criada de esa especie, o adopta una nueva
                    // tras ponerle nombre: una mascota no se puede guardar sin nombre.
                    val raised = current.bySpecies(species)
                    if (raised != null) controller.switchTo(raised.id) else naming = species
                },
                onDismiss = { picker = null },
            )
        }
        naming?.let { species ->
            PetNameDialog(
                species = species,
                submit = { name -> controller.adopt(species, name) },
                onDismiss = { naming = null },
            )
        }
    }
}

@Composable
private fun PetSceneBackgroundOnly() {
    Box(Modifier.fillMaxSize().padding(0.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFBFE6FF))
        }
    }
}

/** Se muestra hasta que se adopta la primera mascota. */
@Composable
private fun FirstPetCard(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.padding(24.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Bienvenido!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Elige tu primera mascota y ponle nombre.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPick) {
                Icon(Icons.Filled.Pets, contentDescription = null)
                Text("  Elegir mascota")
            }
        }
    }
}
