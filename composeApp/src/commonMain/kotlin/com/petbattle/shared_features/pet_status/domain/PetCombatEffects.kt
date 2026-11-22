package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity

/**
 * Cómo afecta el estado de una mascota a su rendimiento en combate. Cada efecto es
 * proporcional a su barra: barra al 100% = efecto al 100%, barra al 50% = 50%...
 *
 * | estado                  | efecto en combate                      |
 * |-------------------------|----------------------------------------|
 * | energy (cansancio)      | velocidad de ataque (disparos/segundo) |
 * | satiety (hambre)        | daño por golpe                         |
 * | happiness (diversión)   | probabilidad de acertar al objetivo    |
 */
object PetCombatEffects {
    private fun fraction(stat: Double) = (stat / PetStatusBalance.MAX_STAT).coerceIn(0.0, 1.0)

    /** Multiplicador de la velocidad de ataque. 0 = demasiado cansada para atacar. */
    fun attackSpeed(status: PetStatusEntity) = fraction(status.energy)

    /** Multiplicador del daño infligido por cada golpe. */
    fun damage(status: PetStatusEntity) = fraction(status.satiety)

    /** Probabilidad (0..1) de que un disparo acierte al objetivo. */
    fun hitChance(status: PetStatusEntity) = fraction(status.happiness)

    /** Segundos entre disparos para un [baseInterval] base; null si está demasiado cansada. */
    fun fireInterval(status: PetStatusEntity, baseInterval: Double): Double? {
        val speed = attackSpeed(status)
        return if (speed <= 0) null else baseInterval / speed
    }
}
