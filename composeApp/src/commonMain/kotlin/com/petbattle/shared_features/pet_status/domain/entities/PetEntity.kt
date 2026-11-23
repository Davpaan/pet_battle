package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_skills.domain.entities.PetProgressionEntity
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.biomes
import com.petbattle.shared_features.pet_species.domain.diet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una mascota criada: quién es ([id], [name], [species]), dónde vive ([biome]), cómo está
 * ([needs]) y cómo ha progresado ([progression]). Necesidades y progresión son
 * entidades separadas con sus propias reglas. Mientras es polluelo, [meals] cuenta lo que
 * ha comido para decidir en qué especie se convierte.
 */
@Serializable
data class PetEntity(
    val id: String,
    val name: String,
    val species: PetSpecies,
    // Claves JSON antiguas ("status", "skills") para no romper partidas guardadas.
    @SerialName("status") val needs: PetNeedsEntity,
    @SerialName("skills") val progression: PetProgressionEntity,
    /** Partidas antiguas sin bioma: el primero de su especie. */
    val biome: Biome = species.biomes.first(),
    /** Veces que ha comido cada comida siendo polluelo. */
    val meals: Map<Food, Int> = emptyMap(),
    /**
     * Especie en la que crecerá este polluelo, decida lo que decida su comida. La tienen los
     * nacidos de una pareja (la de sus padres); `null` = la decide la comida (GrowthRules).
     */
    val destinedSpecies: PetSpecies? = null,
    /** Si no es `null`, aún es un huevo: eclosiona en este instante (ms epoch). */
    val hatchesAt: Long? = null,
    /** Último día (fecha local, ver DayCycle.dayAt) en que intentó criar con su pareja. */
    val lastBreedingDay: Long = -1,
    /** Rareza, de 1 a 5 estrellas (ver TierRules). Partidas antiguas: 1. */
    val tier: Int = 1,
) {
    /** Todavía es un huevo: no se puede seleccionar ni cuidar, y no vive el tiempo. */
    val isEgg: Boolean get() = hatchesAt != null

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
            needs = PetNeedsEntity.initial(now),
            progression = PetProgressionEntity.forSpecies(species),
            biome = biome,
        )

        /**
         * Un huevo puesto por una pareja de [parents] en [biome]: eclosiona en [hatchesAt] en un
         * polluelo que crecerá a [parents]. [id] debe ser único en la partida.
         */
        fun egg(name: String, parents: PetSpecies, biome: Biome, now: Long, hatchesAt: Long, id: String, tier: Int = 1) =
            newborn(name, PetSpecies.chick, now, id = id, biome = biome)
                .copy(destinedSpecies = parents, hatchesAt = hatchesAt, tier = tier)

        /** Un polluelo recién nacido en [biome]; [id] debe ser único en la partida. */
        fun hatch(name: String, biome: Biome, now: Long, id: String) =
            newborn(name, PetSpecies.chick, now, id = id, biome = biome)
    }
}
