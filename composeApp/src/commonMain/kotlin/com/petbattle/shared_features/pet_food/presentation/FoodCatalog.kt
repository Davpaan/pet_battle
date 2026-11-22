package com.petbattle.shared_features.pet_food.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.ui.graphics.vector.ImageVector
import com.petbattle.shared_features.pet_food.domain.Food

/** Datos de presentación de cada comida: nombre visible e icono. */
val Food.label: String
    get() = when (this) {
        Food.seeds -> "Semillas"
        Food.nuts -> "Frutos secos"
        Food.fruit -> "Fruta"
        Food.insects -> "Insectos"
        Food.worms -> "Gusanos"
        Food.nectar -> "Néctar"
        Food.carrion -> "Carroña"
    }

val Food.icon: ImageVector
    get() = when (this) {
        Food.seeds -> Icons.Filled.Grain
        Food.nuts -> Icons.Filled.Park
        Food.fruit -> Icons.Filled.Eco
        Food.insects -> Icons.Filled.BugReport
        Food.worms -> Icons.Filled.Gesture
        Food.nectar -> Icons.Filled.LocalFlorist
        Food.carrion -> Icons.Filled.SetMeal
    }
