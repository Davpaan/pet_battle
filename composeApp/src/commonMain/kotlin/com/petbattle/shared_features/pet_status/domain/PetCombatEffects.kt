package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity

/**
 * How a pet's status affects its performance in combat. Every effect is
 * proportional to its bar: 100% bar = 100% effect, 50% bar = 50%...
 *
 * | status            | combat effect                     |
 * |-------------------|-----------------------------------|
 * | energy (tired)    | attack speed (shots per second)   |
 * | satiety (hunger)  | damage per hit                    |
 * | happiness (fun)   | chance of hitting the target      |
 */
object PetCombatEffects {
    private fun fraction(stat: Double) = (stat / PetStatusBalance.MAX_STAT).coerceIn(0.0, 1.0)

    /** Multiplier of the attack speed. 0 = too tired to attack at all. */
    fun attackSpeed(status: PetStatusEntity) = fraction(status.energy)

    /** Multiplier of the damage dealt by each hit. */
    fun damage(status: PetStatusEntity) = fraction(status.satiety)

    /** Probability (0..1) that a shot hits its target. */
    fun hitChance(status: PetStatusEntity) = fraction(status.happiness)

    /** Seconds between shots for a base [baseInterval]; null when too tired. */
    fun fireInterval(status: PetStatusEntity, baseInterval: Double): Double? {
        val speed = attackSpeed(status)
        return if (speed <= 0) null else baseInterval / speed
    }
}
