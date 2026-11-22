package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import kotlinx.serialization.Serializable

/**
 * Necesidades de una mascota (su mantenimiento). Cada una: 0 = crítico, 100 = perfecto, y
 * cada una sube o baja un stat de combate (ver PetCombatStatsRules). Todas bajan con el
 * tiempo, cada una a su ritmo; qué las sube está en PetNeedsRules.
 *
 * - [satiety]      hambre (llena = sin hambre). Comida. Combate: daño.
 * - [energy]       descanso. Dormir (o la noche) lo recupera; luchar lo gasta. Combate: velocidad.
 * - [happiness]    diversión. Interacción directa (mimos). Combate: confianza.
 * - [digestion]    comida: mejora con su comida y hambre; empeora con comida ajena o sin
 *                  hambre. Combate: velocidad.
 *
 * Salud (mejora limpiándola):
 * - [hygiene]      higiene. Luchar ensucia. Combate: confianza.
 * - [plumage]      plumaje. Luchar despeina. Combate: daño.
 * - [beakAndClaws] pico y garras. Luchar los afila. Combate: daño.
 *
 * Las ambientales (social y clima) no dependen de la interacción y no se guardan: se
 * calculan (ver PetDerivedNeeds).
 */
@Serializable
data class PetNeedsEntity(
    val satiety: Double,
    val energy: Double,
    val happiness: Double,
    val hygiene: Double,
    val isSleeping: Boolean = false,
    // Partidas antiguas sin estas estadísticas: valores iniciales.
    val digestion: Double = PetNeedsBalance.MAX_STAT,
    val beakAndClaws: Double = PetNeedsBalance.MAX_STAT,
    val plumage: Double = PetNeedsBalance.MAX_STAT,
    /** Milisegundos epoch UTC. */
    val lastUpdatedAt: Long,
) {
    companion object {
        /** Una mascota recién adoptada: totalmente descansada y el resto al 80. */
        fun initial(now: Long) = PetNeedsEntity(
            satiety = 80.0, energy = 100.0, happiness = 80.0, hygiene = 80.0,
            lastUpdatedAt = now,
        )
    }
}
