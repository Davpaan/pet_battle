package com.petbattle.shared_features.pet_status.domain

/**
 * Tuning values for pet status (hunger, tiredness, fun, hygiene) and how it
 * affects combat. Pure data, no logic: tweak freely.
 */
object PetStatusBalance {
    const val MIN_STAT = 0.0
    const val MAX_STAT = 100.0

    /** Multiplies the passage of time for every rule. */
    const val TIME_SCALE = 1.0

    // Decay per real hour while awake. Energy (tiredness) does NOT decay with
    // time: only fighting spends it.
    const val SATIETY_DECAY_PER_HOUR = 18.0
    const val HAPPINESS_DECAY_PER_HOUR = 12.0
    const val HYGIENE_DECAY_PER_HOUR = 8.0

    /** While sleeping energy recovers fast: empty to full in ~10 seconds. */
    const val SLEEP_ENERGY_REGEN_PER_HOUR = MAX_STAT / 10 * 3600

    /** While sleeping everything else decays slower. */
    const val SLEEP_DECAY_MULTIPLIER = 0.4

    /** Any stat under this is "neglected" and accelerates happiness decay. */
    const val NEGLECT_THRESHOLD = 20.0
    const val NEGLECT_HAPPINESS_PENALTY = 0.5 // +50% per neglected stat

    // Actions.
    const val FEED_SATIETY = 25.0
    const val FEED_HYGIENE_COST = 3.0
    const val CLEAN_HYGIENE = 45.0

    /** Happiness gained per tap on the pet. */
    const val CUDDLE_HAPPINESS = 2.0

    /** Energy spent by every shot a pet fires. */
    const val ENERGY_PER_SHOT = 1.0

    /** Offline catch-up granularity (ms) and cap (ms). */
    const val SIMULATION_STEP_MS = 60_000L
    const val MAX_OFFLINE_CATCH_UP_MS = 7L * 24 * 3600 * 1000
}
