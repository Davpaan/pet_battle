package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/**
 * Fila superior: el estado de la mascota activa (o el nombre del bioma si está vacío) y, en la
 * misma fila, las tres acciones del bioma: nueva mascota, nuevo bioma y liberar.
 */
@Composable
fun TopBar(
    biome: Biome,
    pet: PetEntity?,
    onNewPet: () -> Unit,
    onChangeBiome: () -> Unit,
    onRelease: (PetEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRelease by remember { mutableStateOf<PetEntity?>(null) }
    Card(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(biome.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (pet != null) StatusPanel(pet)
            }
            Spacer(Modifier.width(8.dp))
            BadgedIconButton(Icons.Filled.Egg, "Nueva mascota", onNewPet)
            BadgedIconButton(Icons.Filled.Landscape, "Nuevo bioma", onChangeBiome)
            FilledTonalIconButton(onClick = { confirmRelease = pet }, enabled = pet != null, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = "Liberar")
            }
        }
    }

    confirmRelease?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRelease = null },
            title = { Text("¿Liberar a ${target.name}?") },
            text = { Text("Volará libre y no volverá. No se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRelease = null
                        onRelease(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Liberar") }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = null }) { Text("Cancelar") } },
        )
    }
}

/** Botón de icono con un pequeño "+" en la esquina (crear algo nuevo). */
@Composable
private fun BadgedIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box {
            Icon(icon, contentDescription = description)
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 4.dp)
                    .size(13.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
