package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_species.domain.PetSpecies
import kotlinx.serialization.Serializable

/**
 * Every pet the player has raised (one per species) plus the one currently
 * being looked after on the main screen.
 */
@Serializable
data class PetRosterEntity(
    val pets: List<PetEntity> = emptyList(),
    /** `null` only before the first adoption. */
    val activeId: String? = null,
) {
    val active: PetEntity? get() = pets.firstOrNull { it.id == activeId }

    fun byId(id: String) = pets.firstOrNull { it.id == id }

    fun bySpecies(species: PetSpecies) = pets.firstOrNull { it.species == species }

    fun hasRaised(species: PetSpecies) = bySpecies(species) != null

    /** Same roster with the pet that has [pet]'s id replaced by [pet]. */
    fun replace(pet: PetEntity) = copy(pets = pets.map { if (it.id == pet.id) pet else it })

    companion object {
        val empty = PetRosterEntity()
    }
}
