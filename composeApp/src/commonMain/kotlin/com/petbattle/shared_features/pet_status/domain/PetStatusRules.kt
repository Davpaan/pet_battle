package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import kotlin.math.min

/**
 * Reglas puras de estado: `status in -> status out`. Sin UI, sin E/S, sin reloj. El
 * mismo [advanceTo] sirve tanto para el tick en vivo como para la puesta al día offline.
 */
object PetStatusRules {
    private const val MS_PER_HOUR = 3_600_000.0

    /** Simula todo lo ocurrido entre `lastUpdatedAt` y [now]. */
    fun advanceTo(pet: PetStatusEntity, now: Long): PetStatusEntity {
        var elapsed = now - pet.lastUpdatedAt
        // El reloj retrocedió: no se "des-decae", solo se reancla.
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
            // Las mascotas totalmente descansadas se despiertan solas (también sin conexión).
            return if (next.energy >= PetStatusBalance.MAX_STAT) next.copy(isSleeping = false) else next
        }
        return pet.copy(
            satiety = clamp(pet.satiety - PetStatusBalance.SATIETY_DECAY_PER_HOUR * h),
            hygiene = clamp(pet.hygiene - PetStatusBalance.HYGIENE_DECAY_PER_HOUR * h),
            happiness = clamp(pet.happiness - happinessRate * h),
        )
    }

    // Acciones del jugador. `canX` permite a la UI deshabilitar botones; `x` no hace nada
    // si la acción no está permitida, así que quien llama no puede corromper el estado.

    fun canFeed(pet: PetStatusEntity) = !pet.isSleeping && pet.satiety < PetStatusBalance.MAX_STAT

    fun feed(pet: PetStatusEntity) = if (!canFeed(pet)) pet else pet.copy(
        satiety = clamp(pet.satiety + PetStatusBalance.FEED_SATIETY),
        hygiene = clamp(pet.hygiene - PetStatusBalance.FEED_HYGIENE_COST),
    )

    /**
     * Ofrecer [food] a una mascota cuya dieta es [diet]: si le gusta se la come ([feed]); si
     * no, la rechaza y su estado no cambia.
     */
    fun offerFood(pet: PetStatusEntity, food: Food, diet: List<Food>): Pair<PetStatusEntity, FeedResult> =
        if (food in diet) feed(pet) to FeedResult.eaten else pet to FeedResult.rejected

    fun toggleSleep(pet: PetStatusEntity) = pet.copy(isSleeping = !pet.isSleeping)

    fun canClean(pet: PetStatusEntity) = !pet.isSleeping && pet.hygiene < PetStatusBalance.MAX_STAT

    fun clean(pet: PetStatusEntity) =
        if (!canClean(pet)) pet else pet.copy(hygiene = clamp(pet.hygiene + PetStatusBalance.CLEAN_HYGIENE))

    /** Tocar a la mascota: la forma de hacerla más feliz. */
    fun cuddle(pet: PetStatusEntity) =
        if (pet.isSleeping) pet else pet.copy(happiness = clamp(pet.happiness + PetStatusBalance.CUDDLE_HAPPINESS))

    /** Cansancio por luchar: la única forma de que baje la energía. */
    fun spendEnergy(pet: PetStatusEntity, amount: Double) =
        if (amount <= 0) pet else pet.copy(energy = clamp(pet.energy - amount))

    private fun clamp(v: Double) = v.coerceIn(PetStatusBalance.MIN_STAT, PetStatusBalance.MAX_STAT)
}
