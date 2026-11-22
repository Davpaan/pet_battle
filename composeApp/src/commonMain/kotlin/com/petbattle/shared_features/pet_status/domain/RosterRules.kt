package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import kotlin.random.Random

/** Motivo por el que no se puede usar un nombre. La UI asigna un mensaje a cada caso. */
enum class PetNameError { empty, tooLong, taken }

/**
 * Reglas puras sobre toda la colección de mascotas criadas.
 * - El jugador elige bioma, nunca especie. Una mascota nueva es siempre un polluelo del bioma
 *   actual (hay que ponerle nombre); varias mascotas pueden convivir en el mismo bioma.
 * - Cambiar de bioma muestra las mascotas que viven allí (o ninguna, si está vacío).
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

    /** Solo se puede criar una mascota nueva tras elegir un bioma. */
    fun canHatch(roster: PetRosterEntity) = roster.currentBiome != null

    /** Añade un polluelo al bioma actual y lo activa. Quien llama debe validar antes. */
    fun hatch(roster: PetRosterEntity, name: String, now: Long): PetRosterEntity {
        val biome = requireNotNull(roster.currentBiome) { "no biome chosen" }
        val error = validateName(roster, name)
        require(error == null) { "invalid name: $error" }
        // El número de mascotas evita ids repetidos si se crían dos en el mismo milisegundo.
        val id = "chick-$now-${roster.pets.size}"
        val pet = PetEntity.hatch(name = normalizeName(name), biome = biome, now = now, id = id)
        return roster.copy(pets = roster.pets + pet, activeId = pet.id)
    }

    /** Viaja a [biome]: se activa su primera mascota, o ninguna si está vacío. */
    fun enterBiome(roster: PetRosterEntity, biome: Biome, now: Long): PetRosterEntity {
        if (biome == roster.currentBiome) return roster
        val first = roster.petsIn(biome).firstOrNull()
        val moved = roster.copy(currentBiome = biome, activeId = null)
        return if (first == null) moved else switchTo(moved, first.id, now)
    }

    /**
     * Libera a la mascota [petId]: desaparece para siempre (su nombre vuelve a quedar libre).
     * Si era la activa, pasa a serlo otra del mismo bioma, o ninguna.
     */
    fun release(roster: PetRosterEntity, petId: String, now: Long): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        val rest = roster.copy(pets = roster.pets - pet)
        if (petId != roster.activeId) return rest
        val next = rest.petsIn(pet.biome).firstOrNull() ?: return rest.copy(activeId = null)
        return switchTo(rest.copy(activeId = null), next.id, now)
    }

    /** Activa [petId] (y su bioma); su reloj se reancla para que el descanso no la haga decaer. */
    fun switchTo(roster: PetRosterEntity, petId: String, now: Long): PetRosterEntity {
        val pet = roster.byId(petId) ?: return roster
        if (petId == roster.activeId) return roster
        return roster.replace(pet.copy(status = pet.status.copy(lastUpdatedAt = now)))
            .copy(activeId = petId, currentBiome = pet.biome)
    }

    /**
     * Ofrece [food] a la mascota activa. Si es un polluelo, cuenta la comida y, al llegar a
     * GrowthRules.MEALS_TO_GROW, crece. `null` si ahora no se le puede dar de comer.
     */
    fun feedActive(roster: PetRosterEntity, food: Food, random: Random): Pair<PetRosterEntity, FeedResult?> {
        val pet = roster.active ?: return roster to null
        if (!PetStatusRules.canFeed(pet.status)) return roster to null
        val (status, result) = PetStatusRules.offerFood(pet.status, food, pet.diet)
        if (result == FeedResult.rejected) return roster to result
        val fed = GrowthRules.recordMeal(pet.copy(status = status), food)
        if (GrowthRules.shouldGrow(fed)) return roster.replace(GrowthRules.grow(fed, random)) to FeedResult.grew
        return roster.replace(fed) to result
    }

    /** Partidas de antes de los biomas: se coloca al jugador en el bioma de su mascota activa. */
    fun migrate(roster: PetRosterEntity): PetRosterEntity =
        if (roster.currentBiome != null) roster
        else roster.copy(currentBiome = (roster.active ?: roster.pets.firstOrNull())?.biome)

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
