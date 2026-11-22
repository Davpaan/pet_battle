package com.petbattle.core.time

/** Desfase (ms) de la zona horaria local respecto a UTC en el instante [epochMillis]. */
expect fun localUtcOffsetMillis(epochMillis: Long): Int

/** Momentos del día del juego. */
enum class DayPhase {
    /** De [DayCycle.MORNING_HOUR] a [DayCycle.EVENING_HOUR]: la imagen tal cual. */
    morning,

    /** De [DayCycle.EVENING_HOUR] a medianoche: un filtro de atardecer sutil; siguen activas. */
    evening,

    /** De medianoche a [DayCycle.MORNING_HOUR]: pantalla oscura y las mascotas descansan. */
    night,
}

/** Ciclo del día del juego, con la hora local del dispositivo. */
object DayCycle {
    const val MORNING_HOUR = 6
    const val EVENING_HOUR = 18
    private const val MS_PER_HOUR = 3_600_000L

    /** Hora (0..23) en [epochMillis] con un desfase horario de [offsetMillis]. */
    fun hourOf(epochMillis: Long, offsetMillis: Int): Int =
        (epochMillis + offsetMillis).floorDiv(MS_PER_HOUR).mod(24L).toInt()

    fun phaseOfHour(hour: Int) = when {
        hour < MORNING_HOUR -> DayPhase.night
        hour < EVENING_HOUR -> DayPhase.morning
        else -> DayPhase.evening
    }

    /** Momento del día en [epochMillis] según la hora local. */
    fun phaseAt(epochMillis: Long) = phaseOfHour(hourOf(epochMillis, localUtcOffsetMillis(epochMillis)))
}
