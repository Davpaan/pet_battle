package com.petbattle.features.pet.presentation.widgets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.GameBar
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.GameIconImage
import com.petbattle.core.ui.GameUi
import com.petbattle.core.ui.WoodButton
import com.petbattle.core.ui.WoodPanel
import com.petbattle.shared_features.pet_status.domain.PetHiddenStats
import com.petbattle.shared_features.pet_status.domain.PetMood
import com.petbattle.shared_features.pet_status.domain.PetStatusBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.mood

/**
 * Estados de la mascota seleccionada (arriba a la derecha), en columna: los 4 básicos siempre;
 * al desplegarlo, también los avanzados (que el jugador no puede subir directamente) y el
 * botón de liberarla. Hace scroll si no cabe. [socialization] se calcula fuera (depende del
 * bioma, ver PetHiddenStats).
 */
@Composable
fun StatsPanel(
    pet: PetEntity,
    socialization: Double,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f)
    WoodPanel(modifier.width(196.dp)) {
        Column(
            Modifier
                .animateContentSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    moodLabel(pet.status.mood),
                    style = GameUi.text(14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GameIconImage(GameIcon.ArrowDown, 22.dp, Modifier.rotate(arrow), if (expanded) "Ver menos" else "Ver todos los estados")
            }
            Spacer(Modifier.height(4.dp))
            StatRow(GameIcon.Hunger, "Hambre", pet.status.satiety)
            StatRow(GameIcon.Energy, "Descanso", pet.status.energy)
            StatRow(GameIcon.Fun, "Diversión", pet.status.happiness)
            StatRow(GameIcon.Hygiene, "Higiene", pet.status.hygiene)
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                StatRow(GameIcon.Social, "Social", socialization)
                StatRow(GameIcon.Climate, "Clima", PetHiddenStats.climate(pet))
                StatRow(GameIcon.Plumage, "Plumaje", PetHiddenStats.plumage(pet))
                StatRow(GameIcon.Digestion, "Digestión", pet.status.digestion)
                StatRow(GameIcon.Beak, "Pico y garras", pet.status.beakAndClaws)
                StatRow(GameIcon.Trust, "Confianza", pet.status.trust)
                Spacer(Modifier.height(8.dp))
                WoodButton("Liberar", onRelease, Modifier.fillMaxWidth(), icon = GameIcon.Release)
            }
        }
    }
}

/** Icono + nombre pequeño + barra de color (rojo vacío -> verde lleno). */
@Composable
private fun StatRow(icon: GameIcon, label: String, value: Double) {
    val t = (value / PetStatusBalance.MAX_STAT).coerceIn(0.0, 1.0).toFloat()
    val color = lerp(Color(0xFFE5484D), Color(0xFF5DBB46), t * t)
    val animated by animateFloatAsState(t)
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIconImage(icon, 26.dp, contentDescription = "$label ${value.toInt()}%")
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = GameUi.text(11.sp), maxLines = 1)
            GameBar(animated, color, Modifier.fillMaxWidth(), height = 10.dp)
        }
    }
}

private fun moodLabel(mood: PetMood) = when (mood) {
    PetMood.sleeping -> "Durmiendo"
    PetMood.hungry -> "Hambriento"
    PetMood.tired -> "Cansado"
    PetMood.dirty -> "Sucio"
    PetMood.sad -> "Triste"
    PetMood.content -> "Tranquilo"
    PetMood.happy -> "Feliz"
}
