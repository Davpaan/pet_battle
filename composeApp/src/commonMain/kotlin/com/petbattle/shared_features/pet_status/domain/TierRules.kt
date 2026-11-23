package com.petbattle.shared_features.pet_status.domain

import kotlin.random.Random

/**
 * Rareza (tier) de una mascota: de [MIN_TIER] a [MAX_TIER] estrellas (común, poco común, raro,
 * épico y legendario). De momento no tiene efecto en el juego: solo se muestra.
 * - Huevos comprados: [PURCHASED_TWO_STAR_CHANCE] de 2 estrellas; si no, 1.
 * - Cría, padres del mismo tier: [SAME_TIER_UPGRADE_CHANCE] de que nazca con una estrella más
 *   (sin pasar de [MAX_TIER]); si no, el de sus padres.
 * - Cría, padres de distinto tier: cualquiera entre el más bajo y el más alto, todos igual de
 *   probables (2 + 4 -> 2, 3 o 4).
 */
object TierRules {
    const val MIN_TIER = 1
    const val MAX_TIER = 5
    const val PURCHASED_TWO_STAR_CHANCE = 0.1
    const val SAME_TIER_UPGRADE_CHANCE = 0.2

    /** Tier de un polluelo de un huevo comprado. */
    fun purchased(random: Random) = if (random.nextDouble() < PURCHASED_TWO_STAR_CHANCE) 2 else 1

    /** Tier de la cría de dos padres de tiers [a] y [b]. */
    fun child(a: Int, b: Int, random: Random): Int =
        if (a == b) {
            if (random.nextDouble() < SAME_TIER_UPGRADE_CHANCE) (a + 1).coerceAtMost(MAX_TIER) else a
        } else {
            random.nextInt(minOf(a, b), maxOf(a, b) + 1)
        }
}
