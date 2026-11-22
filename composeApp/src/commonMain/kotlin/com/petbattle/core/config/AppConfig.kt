package com.petbattle.core.config

/** App-wide technical knobs (not game balance: that lives in each domain). */
object AppConfig {
    /** How often the live simulation advances while the app is in foreground. */
    const val TICK_INTERVAL_MS = 1_000L

    /** Minimum time between two disk writes while state keeps changing. */
    const val SAVE_THROTTLE_MS = 3_000L
}
