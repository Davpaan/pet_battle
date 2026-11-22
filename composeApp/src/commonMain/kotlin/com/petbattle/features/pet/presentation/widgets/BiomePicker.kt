package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petbattle.features.pet.presentation.game.drawBiomeBackdrop
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.background
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import org.jetbrains.compose.resources.imageResource

/**
 * Menú de biomas. Cada uno muestra cuántas mascotas viven allí; los vacíos se marcan como
 * nuevos. Elegir uno viaja a él.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiomePickerSheet(
    roster: PetRosterEntity,
    title: String,
    onPicked: (Biome) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(Biome.entries) { biome ->
                    BiomeTile(
                        biome = biome,
                        petCount = roster.petsIn(biome).size,
                        selected = roster.currentBiome == biome,
                        onClick = { onPicked(biome) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BiomeTile(biome: Biome, petCount: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val image = imageResource(biome.background)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(8.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                drawBiomeBackdrop(image)
            }
            Text(biome.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Text(
                when (petCount) {
                    0 -> "Vacío"
                    1 -> "1 mascota"
                    else -> "$petCount mascotas"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (petCount == 0) scheme.outline else scheme.primary,
            )
        }
    }
}
