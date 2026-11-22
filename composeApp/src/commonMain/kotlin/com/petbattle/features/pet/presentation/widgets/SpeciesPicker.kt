package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_species.presentation.widgets.BirdFrame
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity

/**
 * Species menu. Raised species show their pet's name and level; the rest are
 * marked as new (picking one adopts a new pet at level 0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesPickerSheet(
    roster: PetRosterEntity,
    title: String,
    onPicked: (PetSpecies) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PetSpecies.entries) { species ->
                    SpeciesTile(
                        species = species,
                        pet = roster.bySpecies(species),
                        selected = roster.active?.species == species,
                        onClick = { onPicked(species) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeciesTile(species: PetSpecies, pet: PetEntity?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.aspectRatio(0.75f),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BirdFrame(species, 64.dp, Modifier.weight(1f))
            Spacer(Modifier.height(4.dp))
            Text(
                pet?.name ?: species.label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (pet == null) "Nueva" else "Nv. ${pet.skills.level}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (pet == null) scheme.outline else scheme.primary,
            )
        }
    }
}
