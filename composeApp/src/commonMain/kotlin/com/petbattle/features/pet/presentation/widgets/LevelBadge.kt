package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.GameBar
import com.petbattle.core.ui.GameIcon
import com.petbattle.core.ui.GameIconImage
import com.petbattle.core.ui.GameUi
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_status.domain.GrowthRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/**
 * Nivel de la mascota seleccionada (arriba en el centro): insignia con el número y barra de
 * experiencia. Un polluelo muestra en su lugar el huevo y cuánto le falta para crecer.
 */
@Composable
fun LevelBadge(pet: PetEntity, modifier: Modifier = Modifier) {
    val chick = pet.species.isChick
    val skills = pet.skills
    val (fraction, caption) = if (chick) {
        val eaten = GrowthRules.mealsEaten(pet)
        eaten.toFloat() / GrowthRules.MEALS_TO_GROW to "Crecimiento $eaten / ${GrowthRules.MEALS_TO_GROW}"
    } else if (PetProgression.isMaxLevel(skills)) {
        1f to "Nivel máximo"
    } else {
        PetProgression.levelProgress(skills).toFloat() to "${skills.xp} / ${PetProgression.xpToNextLevel(skills.level)} XP"
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${pet.name} · ${pet.species.label}",
            style = GameUi.text(15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                GameIconImage(if (chick) GameIcon.Egg else GameIcon.Level, 50.dp)
                if (!chick) Text("${skills.level}", style = GameUi.text(20.sp), modifier = Modifier.offset(y = (-2).dp))
            }
            Column(Modifier.offset(x = (-6).dp)) {
                GameBar(fraction, if (chick) GameUi.Green else GameUi.Purple, Modifier.width(170.dp), height = 16.dp)
                Text(caption, style = GameUi.text(11.sp), modifier = Modifier.padding(start = 8.dp, top = 1.dp))
            }
        }
    }
}
