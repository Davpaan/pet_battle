package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.biomes.domain.Biome
import kotlinx.serialization.Serializable

/**
 * Todas las mascotas que ha criado el jugador, repartidas por biomas, el bioma que está
 * visitando y la mascota que se está cuidando en la pantalla principal (siempre de ese bioma).
 */
@Serializable
data class PetRosterEntity(
    val pets: List<PetEntity> = emptyList(),
    /** `null` si el bioma actual está vacío. */
    val activeId: String? = null,
    /** `null` solo antes de elegir el primer bioma. */
    val currentBiome: Biome? = null,
) {
    val active: PetEntity? get() = pets.firstOrNull { it.id == activeId }

    fun byId(id: String) = pets.firstOrNull { it.id == id }

    fun petsIn(biome: Biome) = pets.filter { it.biome == biome }

    /** Mascotas del bioma que se está visitando. */
    val biomePets: List<PetEntity> get() = currentBiome?.let(::petsIn).orEmpty()

    /** Mismo plantel con la mascota que tiene el id de [pet] sustituida por [pet]. */
    fun replace(pet: PetEntity) = copy(pets = pets.map { if (it.id == pet.id) pet else it })

    companion object {
        val empty = PetRosterEntity()
    }
}
