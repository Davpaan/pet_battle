package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.pet_status.domain.PetStatusBalance
import kotlinx.serialization.Serializable

/**
 * Estado de una mascota. Cada estadística: 0 = crítico, 100 = perfecto.
 *
 * Básicas (visibles; el jugador las cuida directamente):
 * - [satiety]   barra de hambre (llena = sin hambre). En combate: daño.
 * - [energy]    barra de cansancio (llena = descansada). En combate: velocidad de ataque.
 *               Solo luchar la gasta; dormir la recupera.
 * - [happiness] barra de diversión. En combate: probabilidad de acierto.
 * - [hygiene]   limpieza.
 *
 * Avanzadas (ocultas tras un desplegable; el jugador no puede subirlas directamente, dependen
 * de cómo la trata). De momento no afectan al combate:
 * - [digestion]    baja si come lo que no le gusta o come sin hambre; sube si come su comida
 *                  con hambre. Siempre es temporal: se recupera sola con el tiempo.
 * - [beakAndClaws] pico y garras: se oxidan con la inactividad y se afilan luchando.
 * - [trust]        confianza en el jugador: empieza baja y crece despacio al cuidarla; baja
 *                  si se la descuida.
 * Socialización, clima y plumaje no se guardan: se calculan (ver PetHiddenStats).
 */
@Serializable
data class PetStatusEntity(
    val satiety: Double,
    val energy: Double,
    val happiness: Double,
    val hygiene: Double,
    val isSleeping: Boolean = false,
    // Partidas antiguas sin estas estadísticas: valores iniciales.
    val digestion: Double = PetStatusBalance.MAX_STAT,
    val beakAndClaws: Double = PetStatusBalance.MAX_STAT,
    val trust: Double = PetStatusBalance.INITIAL_TRUST,
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
