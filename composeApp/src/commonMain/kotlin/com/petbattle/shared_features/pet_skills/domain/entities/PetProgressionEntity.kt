package com.petbattle.shared_features.pet_skills.domain.entities

import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.type
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * Lo que una mascota puede hacer en combate y cuánto ha crecido: su elemento y tipo (que
 * deciden las habilidades que puede aprender), su nivel y su XP. Separado del estado diario
 * (hambre, cansancio...): las habilidades solo cambian peleando.
 */
@Serializable
data class PetProgressionEntity(
    val element: PetElement,
    val type: PetType,
    val level: Int = 0,
    /** Experiencia acumulada hacia el siguiente nivel (se reinicia al subir de nivel). */
    val xp: Int = 0,
) {
    companion object {
    /** Nivel 0 con los rasgos naturales de [species]. */
        fun forSpecies(species: PetSpecies) =
            PetProgressionEntity(element = species.element, type = species.type)
    }
}
