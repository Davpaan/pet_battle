package com.petbattle.core.config

/** Ajustes técnicos de toda la app (no el balance del juego: eso vive en cada dominio). */
object AppConfig {
    /** Cada cuánto avanza la simulación en vivo mientras la app está en primer plano. */
    const val TICK_INTERVAL_MS = 1_000L

    /** Tiempo mínimo entre dos escrituras a disco mientras el estado sigue cambiando. */
    const val SAVE_THROTTLE_MS = 3_000L
}
