package com.petbattle.shared_features.pet_status.domain

/**
 * Valores de ajuste del estado de la mascota (hambre, cansancio, diversión, higiene) y de cómo
 * afecta al combate. Solo datos, sin lógica: modifícalos con libertad.
 */
object PetStatusBalance {
    const val MIN_STAT = 0.0
    const val MAX_STAT = 100.0

    /** Multiplica el paso del tiempo en todas las reglas. */
    const val TIME_SCALE = 1.0

    // Pérdida por hora real mientras está despierta. La energía (cansancio) NO decae con
    // el tiempo: solo se gasta luchando.
    const val SATIETY_DECAY_PER_HOUR = 18.0
    const val HAPPINESS_DECAY_PER_HOUR = 12.0
    const val HYGIENE_DECAY_PER_HOUR = 8.0

    /** Durmiendo, la energía se recupera rápido: de vacía a llena en ~10 segundos. */
    const val SLEEP_ENERGY_REGEN_PER_HOUR = MAX_STAT / 10 * 3600

    /** Durmiendo, todo lo demás decae más despacio. */
    const val SLEEP_DECAY_MULTIPLIER = 0.4

    /**
     * Cualquier estadística por debajo de esto está "descuidada" y acelera la pérdida de
     * felicidad.
     */
    const val NEGLECT_THRESHOLD = 20.0
    const val NEGLECT_HAPPINESS_PENALTY = 0.5 // +50% por estadística descuidada

    // Acciones.
    const val FEED_SATIETY = 25.0
    const val FEED_HYGIENE_COST = 3.0
    const val CLEAN_HYGIENE = 45.0

    /** Felicidad ganada por cada toque a la mascota. */
    const val CUDDLE_HAPPINESS = 2.0

    /** Energía gastada por cada disparo de la mascota. */
    const val ENERGY_PER_SHOT = 1.0

    /** Granularidad (ms) y límite (ms) de la puesta al día sin conexión. */
    const val SIMULATION_STEP_MS = 60_000L
    const val MAX_OFFLINE_CATCH_UP_MS = 7L * 24 * 3600 * 1000
}
