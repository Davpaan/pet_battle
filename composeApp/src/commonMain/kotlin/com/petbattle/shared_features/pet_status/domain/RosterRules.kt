package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity

/** Motivo por el que no se puede usar un nombre. La UI asigna un mensaje a cada caso. */
enum class PetNameError { empty, tooLong, taken }

/**
 * Reglas puras sobre toda la colección de mascotas criadas.
 * - Una mascota por especie. Elegir una especie que nunca has criado adopta una
 *   mascota nueva de nivel 0 y hay que ponerle nombre.
 * - Solo la mascota activa vive en tiempo real. Las demás descansan: sus estadísticas
 *   se congelan mientras están inactivas y se reanudan al volver a elegirlas.
 */
object RosterRules {
    const val MAX_NAME_LENGTH = 16

    fun normalizeName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    /** `null` si [raw] es un nombre válido para una mascota nueva en [roster]. */
    fun validateName(roster: PetRosterEntity, raw: String): PetNameError? {
        val name = normalizeName(raw)
        if (name.isEmpty()) return PetNameError.empty
        if (name.length > MAX_NAME_LENGTH) return PetNameError.tooLong
        if (roster.pets.any { it.name.equals(name, ignoreCase = true) }) return PetNameError.taken
        return null
    }

    fun canAdopt(roster: PetRosterEntity, species: PetSpecies) = !roster.hasRaised(species)

    /** Añade una mascota nueva de nivel 0 y la activa. Quien llama debe validar antes. */
    fun adopt(roster: PetRosterEntity, species: PetSpecies, name: String, now: Long): PetRosterEntity {
        require(canAdopt(roster, species)) { "$species already raised" }
        val error = validateName(roster, name)
        require(error == null) { "invalid name: $error" }
        val pet = PetEntity.newborn(name = normalizeName(name), species = species, now = now)
        return roster.copy(pets = roster.pets + pet, activeId = pet.id)
    }

    /** Activa [petId]; su reloj se reancla para que el descanso no la haga decaer. */
    fun switchTo(roster: PetRosterEntity, petId: String, now: Long): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        if (petId == roster.activeId) return roster
        return roster.replace(pet.copy(status = pet.status.copy(lastUpdatedAt = now)))
            .copy(activeId = petId)
    }

    /** Aplica [change] a la mascota activa (no hace nada si no hay). */
    fun updateActive(roster: PetRosterEntity, change: (PetEntity) -> PetEntity): PetRosterEntity {
        val active = roster.active ?: return roster
        return roster.replace(change(active))
    }

    /** Aplica [change] al estado de la mascota activa. */
    fun updateActiveStatus(
        roster: PetRosterEntity,
        change: (PetStatusEntity) -> PetStatusEntity,
    ) = updateActive(roster) { it.copy(status = change(it.status)) }

    /** Da [amount] XP a la mascota [petId] (cualquiera, activa o en reposo). */
    fun awardXp(roster: PetRosterEntity, petId: String, amount: Int): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        return roster.replace(pet.copy(skills = PetProgression.gainXp(pet.skills, amount)))
    }

    /** La mascota [petId] se cansa por luchar (cualquiera, activa o en reposo). */
    fun spendEnergy(roster: PetRosterEntity, petId: String, amount: Double): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        return roster.replace(pet.copy(status = PetStatusRules.spendEnergy(pet.status, amount)))
    }
}
