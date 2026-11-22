package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_status.domain.PetStatusRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/** Ancho de la columna de acciones (la escena deja ese hueco libre a la derecha). */
val ActionBarWidth: Dp = 104.dp

/** Acciones sobre la mascota activa, en una columna a la derecha de la pantalla (horizontal). */
@Composable
fun ActionBar(
    pet: PetEntity,
    onFeed: () -> Unit,
    onToggleSleep: () -> Unit,
    onClean: () -> Unit,
    onDefend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sleeping = pet.status.isSleeping
    Column(
        modifier.width(ActionBarWidth).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Las reglas deciden la disponibilidad; la UI solo las refleja.
        ActionButton(Icons.Filled.Restaurant, "Alimentar", if (PetStatusRules.canFeed(pet.status)) onFeed else null)
        ActionButton(if (sleeping) Icons.Filled.WbSunny else Icons.Filled.Bedtime, if (sleeping) "Despertar" else "Dormir", onToggleSleep)
        ActionButton(Icons.Filled.Shower, "Limpiar", if (PetStatusRules.canClean(pet.status)) onClean else null)
        ActionButton(Icons.Filled.Shield, "Defender", onDefend)
    }
}

@Composable
private fun ColumnScope.ActionButton(icon: ImageVector, label: String, onClick: (() -> Unit)?) {
    FilledTonalButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(2.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}
