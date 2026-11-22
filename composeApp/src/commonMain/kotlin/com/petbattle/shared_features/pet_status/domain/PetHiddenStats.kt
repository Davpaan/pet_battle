package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/**
 * Estadísticas avanzadas que no se guardan: se calculan a partir de la partida. Junto con
 * digestión, pico y garras y confianza (en PetStatusEntity) forman los estados ocultos.
 * De momento no afectan al combate.
 */
object PetHiddenStats {
    /**
     * Socialización: llena si en su bioma vive al menos otra mascota de su misma especie, a la
     * mitad si solo hay mascotas de otras especies, y vacía si está sola.
     */
    fun socialization(pet: PetEntity, gameState: GameStateEntity): Double {
        val mates = gameState.petsIn(pet.biome).filter { it.id != pet.id }
        return when {
            mates.any { it.species == pet.species } -> PetStatusBalance.MAX_STAT
            mates.isNotEmpty() -> PetStatusBalance.MAX_STAT / 2
            else -> PetStatusBalance.MIN_STAT
        }
    }

    /** Temperatura y humedad. De momento, siempre llena. */
    @Suppress("UNUSED_PARAMETER")
    fun climate(pet: PetEntity) = PetStatusBalance.MAX_STAT

    /** Plumaje. De momento, siempre lleno. */
    @Suppress("UNUSED_PARAMETER")
    fun plumage(pet: PetEntity) = PetStatusBalance.MAX_STAT
}
