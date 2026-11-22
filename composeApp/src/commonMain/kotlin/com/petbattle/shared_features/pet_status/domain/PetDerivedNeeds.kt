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
        val mates = gameState.petsIn(pet.biome).filter { it.id != pet.id }
        return when {
            mates.any { it.species == pet.species } -> PetNeedsBalance.MAX_STAT
            mates.isNotEmpty() -> PetNeedsBalance.MAX_STAT / 2
            else -> PetNeedsBalance.MIN_STAT
        }
    }

    /** Temperatura y humedad. De momento, siempre llena. */
    @Suppress("UNUSED_PARAMETER")
    fun climate(pet: PetEntity) = PetNeedsBalance.MAX_STAT
}
