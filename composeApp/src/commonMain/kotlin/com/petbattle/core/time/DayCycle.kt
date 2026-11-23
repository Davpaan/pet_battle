package com.petbattle.core.time

/** Desfase (ms) de la zona horaria local respecto a UTC en el instante [epochMillis]. */
expect fun localUtcOffsetMillis(epochMillis: Long): Int

/** Momentos del día del juego: cuatro franjas de 6 horas. */
enum class DayPhase {
    /** De 0 a [DayCycle.MORNING_HOUR]: pantalla oscura y azulada; las mascotas descansan. */
    night,

    /** De [DayCycle.MORNING_HOUR] a [DayCycle.MIDDAY_HOUR]: el alba, tonos rosados y dorados con neblina. */
    morning,

    /** De [DayCycle.MIDDAY_HOUR] a [DayCycle.EVENING_HOUR]: la imagen tal cual. */
    midday,

    /** De [DayCycle.EVENING_HOUR] a medianoche: atardecer anaranjado. */
    evening,
}

/** Ciclo del día del juego, con la hora local del dispositivo. */
object DayCycle {
    const val MORNING_HOUR = 6
    const val MIDDAY_HOUR = 12
    const val EVENING_HOUR = 18
    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_DAY = 24 * MS_PER_HOUR

    /** Hora (0..23) en [epochMillis] con un desfase horario de [offsetMillis]. */
    fun hourOf(epochMillis: Long, offsetMillis: Int): Int =
        (epochMillis + offsetMillis).floorDiv(MS_PER_HOUR).mod(24L).toInt()

    fun phaseOfHour(hour: Int) = when {
        hour < MORNING_HOUR -> DayPhase.night
        hour < MIDDAY_HOUR -> DayPhase.morning
        hour < EVENING_HOUR -> DayPhase.midday
        else -> DayPhase.evening
    }

    /** Número de día (fecha local) en [epochMillis] con un desfase horario de [offsetMillis]. */
    fun dayOf(epochMillis: Long, offsetMillis: Int): Long = (epochMillis + offsetMillis).floorDiv(MS_PER_DAY)

    /** Número de día según la fecha local: cambia a medianoche. */
    fun dayAt(epochMillis: Long) = dayOf(epochMillis, localUtcOffsetMillis(epochMillis))

    /** Momento del día en [epochMillis] según la hora local. */
    fun phaseAt(epochMillis: Long) = phaseOfHour(hourOf(epochMillis, localUtcOffsetMillis(epochMillis)))
}
