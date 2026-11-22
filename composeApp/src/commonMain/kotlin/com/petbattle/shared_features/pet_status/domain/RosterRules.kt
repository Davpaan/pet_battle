package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity

/** Why a name can't be used. The UI maps each case to a message. */
enum class PetNameError { empty, tooLong, taken }

/**
 * Pure rules over the whole collection of raised pets.
 * - One pet per species. Picking a species you've never raised adopts a new
 *   pet at level 0 and it must be given a name.
 * - Only the active pet lives in real time. The others rest: their stats are
 *   frozen while inactive and resume when they're picked again.
 */
object RosterRules {
    const val MAX_NAME_LENGTH = 16

    fun normalizeName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    /** `null` when [raw] is a valid name for a new pet in [roster]. */
    fun validateName(roster: PetRosterEntity, raw: String): PetNameError? {
        val name = normalizeName(raw)
        if (name.isEmpty()) return PetNameError.empty
        if (name.length > MAX_NAME_LENGTH) return PetNameError.tooLong
        if (roster.pets.any { it.name.equals(name, ignoreCase = true) }) return PetNameError.taken
        return null
    }

    fun canAdopt(roster: PetRosterEntity, species: PetSpecies) = !roster.hasRaised(species)

    /** Adds a new level-0 pet and makes it active. Callers validate first. */
    fun adopt(roster: PetRosterEntity, species: PetSpecies, name: String, now: Long): PetRosterEntity {
        require(canAdopt(roster, species)) { "$species already raised" }
        val error = validateName(roster, name)
        require(error == null) { "invalid name: $error" }
        val pet = PetEntity.newborn(name = normalizeName(name), species = species, now = now)
        return roster.copy(pets = roster.pets + pet, activeId = pet.id)
    }

    /** Makes [petId] active; its clock is re-anchored so resting didn't decay it. */
    fun switchTo(roster: PetRosterEntity, petId: String, now: Long): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        if (petId == roster.activeId) return roster
        return roster.replace(pet.copy(status = pet.status.copy(lastUpdatedAt = now)))
            .copy(activeId = petId)
    }

    /** Applies [change] to the active pet (no-op without one). */
    fun updateActive(roster: PetRosterEntity, change: (PetEntity) -> PetEntity): PetRosterEntity {
        val active = roster.active ?: return roster
        return roster.replace(change(active))
    }

    /** Applies [change] to the status of the active pet. */
    fun updateActiveStatus(
        roster: PetRosterEntity,
        change: (PetStatusEntity) -> PetStatusEntity,
    ) = updateActive(roster) { it.copy(status = change(it.status)) }

    /** Gives [amount] XP to pet [petId] (any pet, active or resting). */
    fun awardXp(roster: PetRosterEntity, petId: String, amount: Int): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        return roster.replace(pet.copy(skills = PetProgression.gainXp(pet.skills, amount)))
    }

    /** Pet [petId] gets tired by fighting (any pet, active or resting). */
    fun spendEnergy(roster: PetRosterEntity, petId: String, amount: Double): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        return roster.replace(pet.copy(status = PetStatusRules.spendEnergy(pet.status, amount)))
    }
}
