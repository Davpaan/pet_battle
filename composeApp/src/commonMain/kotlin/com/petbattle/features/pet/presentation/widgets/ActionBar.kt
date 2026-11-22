package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.WoodIconButton
import com.petbattle.shared_features.pet_status.domain.PetNeedsRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/** Ancho que ocupa la columna de acciones (la escena deja ese hueco libre a la izquierda). */
val ActionBarWidth: Dp = 72.dp

/** Acciones sobre la mascota seleccionada: pequeña columna de botones abajo a la izquierda. */
@Composable
fun ActionBar(
    pet: PetEntity,
    onFeed: () -> Unit,
    onToggleSleep: () -> Unit,
    onClean: () -> Unit,
    onDefend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sleeping = pet.needs.isSleeping
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Las reglas deciden la disponibilidad; la UI solo las refleja.
        WoodIconButton(GameIcon.Feed, "Alimentar", if (PetNeedsRules.canFeed(pet.needs)) onFeed else null)
        WoodIconButton(if (sleeping) GameIcon.Wake else GameIcon.Sleep, if (sleeping) "Despertar" else "Dormir", onToggleSleep)
        WoodIconButton(GameIcon.Clean, "Limpiar", if (PetNeedsRules.canClean(pet.needs)) onClean else null)
        WoodIconButton(GameIcon.Defend, "Defender", onDefend)
    }
}
