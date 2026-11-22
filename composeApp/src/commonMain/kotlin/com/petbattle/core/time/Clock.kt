package com.petbattle.core.time

/** Fuente de "ahora" como milisegundos de epoch UTC. Sustituible en tests. */
fun interface Clock {
    fun nowMillis(): Long
}

expect fun systemNowMillis(): Long

val SystemClock = Clock { systemNowMillis() }
