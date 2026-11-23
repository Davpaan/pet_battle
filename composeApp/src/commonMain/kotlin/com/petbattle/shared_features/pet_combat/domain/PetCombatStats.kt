package com.petbattle.shared_features.pet_combat.domain

/**
 * Stats de combate de una mascota en un momento dado. No se guardan: se calculan a partir de
 * su nivel (valores totales) y de sus necesidades (cuánto de ese total rinde ahora), ver
 * PetCombatStatsRules.
 *
 * - [damage] / [maxDamage]: daño por golpe.
 * - [attacksPerSecond] / [maxAttacksPerSecond]: velocidad de ataque.
 * - [confidence] / [maxConfidence]: confianza (0..100); es la probabilidad de acertar.
 * - [range]: alcance (px de mapa). Fijo por especie: no depende de las necesidades.
 *   [maxRange] es el de la especie de más alcance (para comparar).
 */
data class PetCombatStats(
    val damage: Double,
    val maxDamage: Double,
    val attacksPerSecond: Double,
    val maxAttacksPerSecond: Double,
    val confidence: Double,
    val maxConfidence: Double = PetCombatBalance.MAX_CONFIDENCE,
    val range: Double = PetCombatBalance.DEFAULT_RANGE,
    val maxRange: Double = PetCombatStatsRules.maxRange,
) {
    /** Probabilidad (0..1) de que un disparo acierte. */
    val hitChance get() = (confidence / maxConfidence).coerceIn(0.0, 1.0)

    /** Agotada: sin energía no puede atacar. */
    val exhausted get() = attacksPerSecond <= 0.0

    /** Segundos entre disparos; `null` si está agotada. */
    val fireInterval get() = if (exhausted) null else 1.0 / attacksPerSecond
}
