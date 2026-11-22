package com.petbattle.shared_features.pet_status.domain.entities

import kotlinx.serialization.Serializable

/**
 * Necesidades del día a día de una mascota. Cada estadística: 0 = crítico, 100 = perfecto.
 * - [satiety]   barra de hambre (llena = sin hambre). En combate: daño.
 * - [energy]    barra de cansancio (llena = descansada). En combate: velocidad de ataque.
 *               Solo luchar la gasta; dormir la recupera.
 * - [happiness] barra de diversión. En combate: probabilidad de acierto.
 * - [hygiene]   limpieza.
 */
@Serializable
data class PetStatusEntity(
    val satiety: Double,
    val energy: Double,
    val happiness: Double,
    val hygiene: Double,
    val isSleeping: Boolean = false,
    /** Milisegundos epoch UTC. */
    val lastUpdatedAt: Long,
) {
    companion object {
        /** Una mascota recién adoptada: totalmente descansada y el resto al 80. */
        fun initial(now: Long) = PetStatusEntity(
            satiety = 80.0, energy = 100.0, happiness = 80.0, hygiene = 80.0,
            lastUpdatedAt = now,
        )
    }
}
