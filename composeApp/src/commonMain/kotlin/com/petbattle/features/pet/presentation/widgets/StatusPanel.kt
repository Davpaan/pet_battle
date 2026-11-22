package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_skills.presentation.ElementChip
import com.petbattle.shared_features.pet_skills.presentation.TypeChip
import com.petbattle.shared_features.pet_status.domain.PetMood
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.mood

@Composable
fun StatusPanel(pet: PetEntity, onChangeSpecies: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pet.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text(moodLabel(pet.status.mood)) })
                Spacer(Modifier.weight(1f))
                if (onChangeSpecies != null) {
                    IconButton(onClick = onChangeSpecies) {
                        Icon(Icons.Filled.Pets, contentDescription = "Cambiar mascota")
                    }
                }
            }
            LevelRow(pet)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                StatBar(Icons.Filled.Restaurant, "Hambre", pet.status.satiety, Modifier.weight(1f))
                StatBar(Icons.Filled.Bolt, "Descanso", pet.status.energy, Modifier.weight(1f))
                StatBar(Icons.Filled.Celebration, "Diversión", pet.status.happiness, Modifier.weight(1f))
                StatBar(Icons.Filled.WaterDrop, "Higiene", pet.status.hygiene, Modifier.weight(1f))
            }
        }
    }
}

/** "Nv. 3", XP progress towards the next level, element and type. */
@Composable
private fun LevelRow(pet: PetEntity) {
    val skills = pet.skills
    val maxed = PetProgression.isMaxLevel(skills)
    val need = PetProgression.xpToNextLevel(skills.level)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Text(
            "Nv. ${skills.level}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { PetProgression.levelProgress(skills).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Spacer(Modifier.height(2.dp))
            Text(if (maxed) "Nivel máximo" else "${skills.xp} / $need XP", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        ElementChip(skills.element)
        Spacer(Modifier.width(4.dp))
        TypeChip(skills.type)
    }
}

private fun moodLabel(mood: PetMood) = when (mood) {
    PetMood.sleeping -> "😴 Durmiendo"
    PetMood.hungry -> "🍽️ Hambriento"
    PetMood.tired -> "🥱 Cansado"
    PetMood.dirty -> "🫧 Sucio"
    PetMood.sad -> "😢 Triste"
    PetMood.content -> "🙂 Tranquilo"
    PetMood.happy -> "😄 Feliz"
}
