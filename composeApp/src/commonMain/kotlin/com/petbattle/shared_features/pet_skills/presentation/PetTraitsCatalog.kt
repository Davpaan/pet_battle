package com.petbattle.shared_features.pet_skills.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetType

/** Datos de presentación de los rasgos. Fuera del dominio, como el catálogo de especies. */
val PetElement.label get() = when (this) {
    PetElement.fire -> "Fuego"
    PetElement.air -> "Aire"
    PetElement.water -> "Agua"
    PetElement.earth -> "Tierra"
}

val PetElement.icon: ImageVector get() = when (this) {
    PetElement.fire -> Icons.Filled.LocalFireDepartment
    PetElement.air -> Icons.Filled.Air
    PetElement.water -> Icons.Filled.WaterDrop
    PetElement.earth -> Icons.Filled.Terrain
}

val PetElement.color get() = when (this) {
    PetElement.fire -> Color(0xFFE65100)
    PetElement.air -> Color(0xFF0288D1)
    PetElement.water -> Color(0xFF1565C0)
    PetElement.earth -> Color(0xFF6D4C41)
}

val PetType.label get() = when (this) {
    PetType.light -> "Luz"
    PetType.darkness -> "Oscuridad"
    PetType.elemental -> "Elemental"
}

val PetType.icon: ImageVector get() = when (this) {
    PetType.light -> Icons.Filled.LightMode
    PetType.darkness -> Icons.Filled.DarkMode
    PetType.elemental -> Icons.Filled.AutoAwesome
}

val PetType.color get() = when (this) {
    PetType.light -> Color(0xFFF9A825)
    PetType.darkness -> Color(0xFF4A148C)
    PetType.elemental -> Color(0xFF00897B)
}

/** Pequeña etiqueta redondeada con un icono, p. ej. [fire icon] "Fuego". */
@Composable
fun TraitChip(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .background(color.copy(alpha = 0.14f), shape)
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ElementChip(element: PetElement) = TraitChip(element.icon, element.label, element.color)

@Composable
fun TypeChip(type: PetType) = TraitChip(type.icon, type.label, type.color)
