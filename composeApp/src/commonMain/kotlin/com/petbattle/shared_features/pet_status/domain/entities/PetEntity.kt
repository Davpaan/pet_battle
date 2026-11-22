package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * Una mascota criada: quién es ([id], [name], [species]), cómo está
 * ([status]) y qué puede hacer en combate ([skills]). Estado y habilidades son
 * entidades separadas con sus propias reglas.
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
        /** Una mascota recién adoptada: nivel 0, con los rasgos de su especie. */
        fun newborn(name: String, species: PetSpecies, now: Long, id: String? = null) = PetEntity(
            id = id ?: "${species.name}-$now",
            name = name,
            species = species,
            status = PetStatusEntity.initial(now),
            skills = PetSkillsEntity.forSpecies(species),
        )
    }
}
