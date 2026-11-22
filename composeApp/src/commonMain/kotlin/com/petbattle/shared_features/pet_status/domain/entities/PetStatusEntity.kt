package com.petbattle.shared_features.pet_status.domain.entities

import kotlinx.serialization.Serializable

/**
 * Day-to-day needs of a pet. Every stat: 0 = critical, 100 = perfect.
 * - [satiety]   hunger bar (full = not hungry). In combat: damage.
 * - [energy]    tiredness bar (full = rested). In combat: attack speed.
 *               Only fighting drains it; sleeping refills it.
 * - [happiness] fun bar. In combat: hit chance.
 * - [hygiene]   cleanliness.
 */
@Serializable
data class PetStatusEntity(
    val satiety: Double,
    val energy: Double,
    val happiness: Double,
    val hygiene: Double,
    val isSleeping: Boolean = false,
    /** UTC epoch milliseconds. */
    val lastUpdatedAt: Long,
) {
    companion object {
        /** A freshly adopted pet: fully rested, everything else at 80. */
        fun initial(now: Long) = PetStatusEntity(
            satiety = 80.0, energy = 100.0, happiness = 80.0, hygiene = 80.0,
            lastUpdatedAt = now,
        )
    }
}
