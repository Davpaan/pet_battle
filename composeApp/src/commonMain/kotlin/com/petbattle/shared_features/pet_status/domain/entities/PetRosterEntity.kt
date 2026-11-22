package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * Todas las mascotas que ha criado el jugador (una por especie) más la que se está
 * cuidando actualmente en la pantalla principal.
 */
@Serializable
data class PetRosterEntity(
    val pets: List<PetEntity> = emptyList(),
    /** `null` solo antes de la primera adopción. */
    val activeId: String? = null,
) {
    val active: PetEntity? get() = pets.firstOrNull { it.id == activeId }

    fun byId(id: String) = pets.firstOrNull { it.id == id }

    fun bySpecies(species: PetSpecies) = pets.firstOrNull { it.species == species }

    fun hasRaised(species: PetSpecies) = bySpecies(species) != null

    /** Mismo plantel con la mascota que tiene el id de [pet] sustituida por [pet]. */
    fun replace(pet: PetEntity) = copy(pets = pets.map { if (it.id == pet.id) pet else it })

    companion object {
        val empty = PetRosterEntity()
    }
}
