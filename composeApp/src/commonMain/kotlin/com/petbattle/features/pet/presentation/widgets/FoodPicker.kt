package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.GameIconImage
import com.petbattle.core.ui.GameUi
import com.petbattle.core.ui.WoodIconButton
import com.petbattle.core.ui.WoodPanel
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_food.presentation.icon
import com.petbattle.shared_features.pet_food.presentation.label

/** Comidas por fila del menú (7 comidas: 4 arriba y 3 abajo, centradas). */
private const val FOODS_PER_ROW = 4

/**
 * Menú de comidas al pulsar "Alimentar": un modal con casillas cuadradas de madera en dos
 * filas. La elegida se queda en la mano para dejarla en el suelo. Muestra todas: averiguar qué
 * le gusta a cada especie forma parte del juego.
 */
@Composable
fun FoodPickerSheet(onPicked: (Food) -> Unit, onDismiss: () -> Unit) {
    // Sin el ancho máximo por defecto de los diálogos, que recortaría la primera fila.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WoodPanel {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Qué comida dejas en el suelo?", style = GameUi.text(18.sp), modifier = Modifier.padding(end = 12.dp))
                    WoodIconButton(GameIcon.Close, "Cerrar", onDismiss, size = 40.dp)
                }
                for (row in Food.entries.chunked(FOODS_PER_ROW)) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { food -> FoodTile(food) { onPicked(food) } }
                    }
                }
            }
        }
    }
}

/** Casilla cuadrada: icono grande y el nombre debajo, sobre madera oscura. */
@Composable
private fun FoodTile(food: Food, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(104.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GameUi.WoodDark, GameUi.Inset)))
            .border(3.dp, GameUi.Outline, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GameIconImage(food.icon, 50.dp, contentDescription = food.label)
            Text(
                food.label,
                style = GameUi.text(12.sp),
                maxLines = 2,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
