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

    /**
     * Simula todo lo ocurrido entre `lastUpdatedAt` y [now]. Si [beakDecays] es `false` (los
     * polluelos, que aún no pueden luchar), el pico y las garras no se oxidan.
     */
    fun advanceTo(pet: PetStatusEntity, now: Long, beakDecays: Boolean = true): PetStatusEntity {
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
            current = step(current, chunk / MS_PER_HOUR, beakDecays)
            remaining -= chunk
        }
        return current.copy(lastUpdatedAt = now)
    }

    private fun step(pet: PetStatusEntity, realHours: Double, beakDecays: Boolean): PetStatusEntity {
        val h = realHours * PetStatusBalance.TIME_SCALE
        val neglected = listOf(pet.satiety, pet.energy, pet.hygiene)
            .count { it < PetStatusBalance.NEGLECT_THRESHOLD }
        val happinessRate = PetStatusBalance.HAPPINESS_DECAY_PER_HOUR *
            (1 + neglected * PetStatusBalance.NEGLECT_HAPPINESS_PENALTY)
        val slowdown = if (pet.isSleeping) PetStatusBalance.SLEEP_DECAY_MULTIPLIER else 1.0
        // Avanzadas: el pico se oxida sin luchar, la confianza cae si se la descuida y la
        // digestión se recupera sola (los empachos siempre son temporales).
        val trained = pet.copy(
            digestion = clamp(pet.digestion + PetStatusBalance.DIGESTION_RECOVERY_PER_HOUR * h),
            beakAndClaws = if (beakDecays) clamp(pet.beakAndClaws - PetStatusBalance.BEAK_DECAY_PER_HOUR * slowdown * h) else pet.beakAndClaws,
            trust = clamp(pet.trust - PetStatusBalance.TRUST_NEGLECT_PER_HOUR * neglected * h),
        )
        return stepBasics(trained, h, happinessRate)
    }

    private fun stepBasics(pet: PetStatusEntity, h: Double, happinessRate: Double): PetStatusEntity {
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

    /** Siempre se le puede dar de comer despierta (aunque no tenga hambre: le sentará mal). */
    fun canFeed(pet: PetStatusEntity) = !pet.isSleeping

    /** Tiene hambre: comer ahora le sienta bien. */
    fun isHungry(pet: PetStatusEntity) = pet.satiety < PetStatusBalance.NEED_THRESHOLD

    /** Se come algo de su dieta: sacia (y mancha un poco). */
    fun feed(pet: PetStatusEntity) = if (!canFeed(pet)) pet else pet.copy(
        satiety = clamp(pet.satiety + PetStatusBalance.FEED_SATIETY),
        hygiene = clamp(pet.hygiene - PetStatusBalance.FEED_HYGIENE_COST),
    )

    /**
     * Ofrecer [food] a una mascota cuya dieta es [diet]. Siempre se la come, pero:
     * - de su dieta y con hambre: sacia, mejora la digestión y gana confianza;
     * - de su dieta sin hambre: sacia, pero empeora la digestión;
     * - fuera de su dieta: no sacia y empeora la digestión (más aún si encima no tenía hambre).
     * Dormida no come: estado sin cambios y resultado `null`.
     */
    fun offerFood(pet: PetStatusEntity, food: Food, diet: List<Food>): Pair<PetStatusEntity, FeedResult?> {
        if (!canFeed(pet)) return pet to null
        val hungry = isHungry(pet)
        val overeating = if (hungry) 0.0 else PetStatusBalance.DIGESTION_OVEREATING
        if (food !in diet) {
            val messy = pet.copy(hygiene = clamp(pet.hygiene - PetStatusBalance.FEED_HYGIENE_COST))
            return digest(messy, -PetStatusBalance.DIGESTION_DISLIKED_FOOD - overeating) to FeedResult.disliked
        }
        val fed = feed(pet)
        return if (hungry) care(digest(fed, PetStatusBalance.DIGESTION_GOOD_MEAL)) to FeedResult.eaten
        else digest(fed, -overeating) to FeedResult.overate
    }

    /** Acostarla cuando está cansada cuenta como cuidarla. */
    fun toggleSleep(pet: PetStatusEntity): PetStatusEntity {
        val next = pet.copy(isSleeping = !pet.isSleeping)
        return if (next.isSleeping && pet.energy < PetStatusBalance.NEED_THRESHOLD) care(next) else next
    }

    fun canClean(pet: PetStatusEntity) = !pet.isSleeping && pet.hygiene < PetStatusBalance.MAX_STAT

    /** Limpiarla cuando está sucia cuenta como cuidarla. */
    fun clean(pet: PetStatusEntity): PetStatusEntity {
        if (!canClean(pet)) return pet
        val cleaned = pet.copy(hygiene = clamp(pet.hygiene + PetStatusBalance.CLEAN_HYGIENE))
        return if (pet.hygiene < PetStatusBalance.NEED_THRESHOLD) care(cleaned) else cleaned
    }

    /** Tocar a la mascota: la forma de hacerla más feliz (y algo de confianza si estaba baja de ánimo). */
    fun cuddle(pet: PetStatusEntity): PetStatusEntity {
        if (pet.isSleeping) return pet
        val cuddled = pet.copy(happiness = clamp(pet.happiness + PetStatusBalance.CUDDLE_HAPPINESS))
        return if (pet.happiness < PetStatusBalance.NEED_THRESHOLD) {
            cuddled.copy(trust = clamp(cuddled.trust + PetStatusBalance.TRUST_PER_CUDDLE))
        } else cuddled
    }

    /** Un disparo en combate: cansa y afila el pico y las garras. */
    fun fire(pet: PetStatusEntity) = spendEnergy(pet, PetStatusBalance.ENERGY_PER_SHOT)
        .let { it.copy(beakAndClaws = clamp(it.beakAndClaws + PetStatusBalance.BEAK_PER_SHOT)) }

    private fun digest(pet: PetStatusEntity, delta: Double) = pet.copy(digestion = clamp(pet.digestion + delta))

    private fun care(pet: PetStatusEntity) = pet.copy(trust = clamp(pet.trust + PetStatusBalance.TRUST_PER_CARE))

    /** Cansancio por luchar: la única forma de que baje la energía. */
    fun spendEnergy(pet: PetStatusEntity, amount: Double) =
        if (amount <= 0) pet else pet.copy(energy = clamp(pet.energy - amount))

    private fun clamp(v: Double) = v.coerceIn(PetStatusBalance.MIN_STAT, PetStatusBalance.MAX_STAT)
}
