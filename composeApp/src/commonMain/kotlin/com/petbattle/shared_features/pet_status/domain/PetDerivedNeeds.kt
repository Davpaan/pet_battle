package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/**
 * Necesidades ambientales: no dependen de la interacción del jugador y no se guardan, se
 * calculan a partir de la partida. Igual que las demás (PetNeedsEntity), cada una sube o baja
 * un stat de combate: social -> confianza, clima -> velocidad (ver PetCombatStatsRules).
 */
object PetDerivedNeeds {
    /**
     * Socialización: llena si en su bioma vive al menos otra mascota de su misma especie, a la
     * mitad si solo hay mascotas de otras especies, y vacía si está sola.
     */
    fun socialization(pet: PetEntity, gameState: GameStateEntity): Double {
        // Los huevos aún no hacen compañía.
        val mates = gameState.petsIn(pet.biome).filter { it.id != pet.id && !it.isEgg }
        return when {
            mates.any { it.species == pet.species } -> PetNeedsBalance.MAX_STAT
            mates.isNotEmpty() -> PetNeedsBalance.MAX_STAT / 2
            else -> PetNeedsBalance.MIN_STAT
        }
    }

    /** Temperatura y humedad. De momento, siempre llena. */
    @Suppress("UNUSED_PARAMETER")
    fun climate(pet: PetEntity) = PetNeedsBalance.MAX_STAT

    /**
     * Satisfacción global (0..100): la media de **todas** sus necesidades, guardadas y
     * ambientales. Por encima de BreedingRules.SATISFACTION_TO_BREED puede criar.
     */
    fun satisfaction(pet: PetEntity, gameState: GameStateEntity): Double {
        val n = pet.needs
        val all = listOf(
            n.satiety, n.energy, n.happiness, n.digestion,
            n.hygiene, n.plumage, n.beakAndClaws,
            socialization(pet, gameState), climate(pet),
        )
        return all.sum() / all.size
    }
}
