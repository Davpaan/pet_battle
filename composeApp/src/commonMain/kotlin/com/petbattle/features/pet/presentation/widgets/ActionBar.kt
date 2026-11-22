package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_status.domain.PetStatusRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

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
    Row(modifier.padding(12.dp)) {
        // Las reglas deciden la disponibilidad; la UI solo las refleja.
        ActionButton(Icons.Filled.Restaurant, "Alimentar", if (PetStatusRules.canFeed(pet.status)) onFeed else null)
        ActionButton(if (sleeping) Icons.Filled.WbSunny else Icons.Filled.Bedtime, if (sleeping) "Despertar" else "Dormir", onToggleSleep)
        ActionButton(Icons.Filled.Shower, "Limpiar", if (PetStatusRules.canClean(pet.status)) onClean else null)
        ActionButton(Icons.Filled.Shield, "Defender", onDefend)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
) {
    FilledTonalButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
