package com.petbattle.shared_features.pet_skills.domain.entities

import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.type
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * What a pet can do in combat and how far it has grown: its element and type
 * (which decide the skills it can learn), its level and its XP. Kept apart
 * from the day-to-day status (hunger, tiredness...): skills only change by
 * fighting.
 */
@Serializable
data class PetSkillsEntity(
    val element: PetElement,
    val type: PetType,
    val level: Int = 0,
    /** Experience accumulated towards the next level (resets on level-up). */
    val xp: Int = 0,
) {
    companion object {
        /** Level 0 with the natural traits of [species]. */
        fun forSpecies(species: PetSpecies) =
            PetSkillsEntity(element = species.element, type = species.type)
    }
}
