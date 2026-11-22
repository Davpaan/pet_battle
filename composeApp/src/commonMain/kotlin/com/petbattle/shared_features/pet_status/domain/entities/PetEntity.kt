package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * One raised pet: who it is ([id], [name], [species]), how it is doing
 * ([status]) and what it can do in combat ([skills]). Status and skills are
 * separate entities with their own rules.
 */
@Serializable
data class PetEntity(
    val id: String,
    val name: String,
    val species: PetSpecies,
    val status: PetStatusEntity,
    val skills: PetSkillsEntity,
) {
    companion object {
        /** A freshly adopted pet: level 0, traits taken from its species. */
        fun newborn(name: String, species: PetSpecies, now: Long, id: String? = null) = PetEntity(
            id = id ?: "${species.name}-$now",
            name = name,
            species = species,
            status = PetStatusEntity.initial(now),
            skills = PetSkillsEntity.forSpecies(species),
        )
    }
}
