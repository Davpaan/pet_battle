package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_food.presentation.label

/**
 * Menú de comidas al pulsar "Alimentar". Muestra todas: adivinar qué le gusta a cada especie
 * forma parte del juego.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(petName: String, onPicked: (Food) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("¿Qué le das a $petName?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(Food.entries) { food ->
                    FilledTonalButton(
                        onClick = { onPicked(food) },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(food.icon, contentDescription = null)
                            Spacer(Modifier.height(4.dp))
                            Text(food.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
