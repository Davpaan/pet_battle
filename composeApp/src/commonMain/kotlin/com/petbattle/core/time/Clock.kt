package com.petbattle.core.time

/** Source of "now" as UTC epoch milliseconds. Overridable in tests. */
fun interface Clock {
    fun nowMillis(): Long
}

expect fun systemNowMillis(): Long

val SystemClock = Clock { systemNowMillis() }
