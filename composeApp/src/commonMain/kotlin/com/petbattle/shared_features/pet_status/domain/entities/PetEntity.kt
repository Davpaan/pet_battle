package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.biomes
import com.petbattle.shared_features.pet_species.domain.diet
import kotlinx.serialization.Serializable

/**
 * Una mascota criada: quién es ([id], [name], [species]), dónde vive ([biome]), cómo está
 * ([status]) y qué puede hacer en combate ([skills]). Estado y habilidades son
 * entidades separadas con sus propias reglas. Mientras es polluelo, [meals] cuenta lo que
 * ha comido para decidir en qué especie se convierte.
 */
@Serializable
data class PetEntity(
    val id: String,
    val name: String,
    val species: PetSpecies,
    val status: PetStatusEntity,
    val skills: PetSkillsEntity,
    /** Partidas antiguas sin bioma: el primero de su especie. */
    val biome: Biome = species.biomes.first(),
    /** Veces que ha comido cada comida siendo polluelo. */
    val meals: Map<Food, Int> = emptyMap(),
) {
    /**
     * Biomas donde vive la mascota, según su especie. Se calcula (no se guarda) para que
     * cambiar el mapeo especie -> biomas actualice también las partidas guardadas.
     */
    val biomes: List<Biome> get() = species.biomes

    /** Comidas que acepta, según su especie (calculado, igual que [biomes]). */
    val diet: List<Food> get() = species.diet

    companion object {
        /** Una mascota nueva de nivel 0 de [species] (en los juegos normales, siempre polluelo). */
        fun newborn(
            name: String,
            species: PetSpecies,
            now: Long,
            id: String? = null,
            biome: Biome = species.biomes.first(),
        ) = PetEntity(
            id = id ?: "${species.name}-$now",
            name = name,
            species = species,
            status = PetStatusEntity.initial(now),
            skills = PetSkillsEntity.forSpecies(species),
            biome = biome,
        )

        /** Un polluelo recién nacido en [biome]; [id] debe ser único en la partida. */
        fun hatch(name: String, biome: Biome, now: Long, id: String) =
            newborn(name, PetSpecies.chick, now, id = id, biome = biome)
    }
}
