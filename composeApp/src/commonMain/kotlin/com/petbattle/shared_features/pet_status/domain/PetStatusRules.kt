package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import kotlin.math.min

/**
 * Pure status rules: `status in -> status out`. No UI, no I/O, no clock. The
 * same [advanceTo] powers both the live tick and the offline catch-up.
 */
object PetStatusRules {
    private const val MS_PER_HOUR = 3_600_000.0

    /** Simulates everything that happened between `lastUpdatedAt` and [now]. */
    fun advanceTo(pet: PetStatusEntity, now: Long): PetStatusEntity {
        var elapsed = now - pet.lastUpdatedAt
        // Clock moved backwards: don't "un-decay", just re-anchor.
        if (elapsed <= 0) return pet.copy(lastUpdatedAt = now)
        if (elapsed > PetStatusBalance.MAX_OFFLINE_CATCH_UP_MS) {
            elapsed = PetStatusBalance.MAX_OFFLINE_CATCH_UP_MS
        }
        var current = pet
        var remaining = elapsed
        while (remaining > 0) {
            val chunk = min(PetStatusBalance.SIMULATION_STEP_MS, remaining)
            current = step(current, chunk / MS_PER_HOUR)
            remaining -= chunk
        }
        return current.copy(lastUpdatedAt = now)
    }

    private fun step(pet: PetStatusEntity, realHours: Double): PetStatusEntity {
        val h = realHours * PetStatusBalance.TIME_SCALE
        val neglected = listOf(pet.satiety, pet.energy, pet.hygiene)
            .count { it < PetStatusBalance.NEGLECT_THRESHOLD }
        val happinessRate = PetStatusBalance.HAPPINESS_DECAY_PER_HOUR *
            (1 + neglected * PetStatusBalance.NEGLECT_HAPPINESS_PENALTY)

        if (pet.isSleeping) {
            val m = PetStatusBalance.SLEEP_DECAY_MULTIPLIER
            val next = pet.copy(
                energy = clamp(pet.energy + PetStatusBalance.SLEEP_ENERGY_REGEN_PER_HOUR * h),
                satiety = clamp(pet.satiety - PetStatusBalance.SATIETY_DECAY_PER_HOUR * m * h),
                hygiene = clamp(pet.hygiene - PetStatusBalance.HYGIENE_DECAY_PER_HOUR * m * h),
                happiness = clamp(pet.happiness - happinessRate * m * h),
            )
            // Fully rested pets wake up on their own (also while offline).
            return if (next.energy >= PetStatusBalance.MAX_STAT) next.copy(isSleeping = false) else next
        }
        return pet.copy(
            satiety = clamp(pet.satiety - PetStatusBalance.SATIETY_DECAY_PER_HOUR * h),
            hygiene = clamp(pet.hygiene - PetStatusBalance.HYGIENE_DECAY_PER_HOUR * h),
            happiness = clamp(pet.happiness - happinessRate * h),
        )
    }

    // Player actions. `canX` lets the UI disable buttons; `x` is a no-op when
    // the action is not allowed, so callers can't corrupt the state.

    fun canFeed(pet: PetStatusEntity) = !pet.isSleeping && pet.satiety < PetStatusBalance.MAX_STAT

    fun feed(pet: PetStatusEntity) = if (!canFeed(pet)) pet else pet.copy(
        satiety = clamp(pet.satiety + PetStatusBalance.FEED_SATIETY),
        hygiene = clamp(pet.hygiene - PetStatusBalance.FEED_HYGIENE_COST),
    )

    fun toggleSleep(pet: PetStatusEntity) = pet.copy(isSleeping = !pet.isSleeping)

    fun canClean(pet: PetStatusEntity) = !pet.isSleeping && pet.hygiene < PetStatusBalance.MAX_STAT

    fun clean(pet: PetStatusEntity) =
        if (!canClean(pet)) pet else pet.copy(hygiene = clamp(pet.hygiene + PetStatusBalance.CLEAN_HYGIENE))

    /** Tapping the pet: the way to make it happier. */
    fun cuddle(pet: PetStatusEntity) =
        if (pet.isSleeping) pet else pet.copy(happiness = clamp(pet.happiness + PetStatusBalance.CUDDLE_HAPPINESS))

    /** Tiredness from fighting: the only way energy goes down. */
    fun spendEnergy(pet: PetStatusEntity, amount: Double) =
        if (amount <= 0) pet else pet.copy(energy = clamp(pet.energy - amount))

    private fun clamp(v: Double) = v.coerceIn(PetStatusBalance.MIN_STAT, PetStatusBalance.MAX_STAT)
}
