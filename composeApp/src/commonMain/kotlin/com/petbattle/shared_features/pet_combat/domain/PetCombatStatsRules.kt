package com.petbattle.shared_features.pet_combat.domain

import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity

/** Valores base de los stats de combate. Solo datos: modifícalos con libertad. */
object PetCombatBalance {
    /** Daño de una mascota de nivel 0. */
    const val LEVEL_ZERO_DAMAGE = 1.0

    /** Daño por nivel; igual a los PV del enemigo por nivel (nivel N mata a N de un golpe). */
    const val DAMAGE_PER_LEVEL = 1.2

    /** Segundos entre disparos con la velocidad al máximo. */
    const val BASE_FIRE_INTERVAL = 0.7

    const val MAX_CONFIDENCE = 100.0
}

/**
 * Cómo se calculan los stats de combate. Los totales dependen del nivel; lo que rinde ahora
 * es el total multiplicado por la media de tres necesidades (todas al 100% = el total):
 *
 * | stat de combate     | necesidades que lo suben o bajan          |
 * |---------------------|-------------------------------------------|
 * | daño                | hambre (saciedad), plumaje, pico y garras |
 * | velocidad de ataque | descanso (energía), digestión, clima      |
 * | confianza (acierto) | diversión, social, higiene                |
 *
 * Además, sin energía está agotada y no ataca (si no, podría disparar sin fin).
 */
object PetCombatStatsRules {
    /** Daño total de una mascota de nivel [level] (con todas sus necesidades al máximo). */
    fun maxDamage(level: Int) =
        if (level <= 0) PetCombatBalance.LEVEL_ZERO_DAMAGE else PetCombatBalance.DAMAGE_PER_LEVEL * level

    /** Ataques por segundo totales (de momento no depende del nivel). */
    val maxAttacksPerSecond get() = 1.0 / PetCombatBalance.BASE_FIRE_INTERVAL

    /** Stats de combate de [pet] ahora; [socialization] depende de su bioma (PetDerivedNeeds). */
    fun statsFor(pet: PetEntity, socialization: Double): PetCombatStats {
        val n = pet.needs
        val damageFactor = mean(n.satiety, n.plumage, n.beakAndClaws)
        val speedFactor = if (n.energy <= 0.0) 0.0 else mean(n.energy, n.digestion, PetDerivedNeeds.climate(pet))
        val maxDamage = maxDamage(pet.progression.level)
        return PetCombatStats(
            damage = maxDamage * damageFactor,
            maxDamage = maxDamage,
            attacksPerSecond = maxAttacksPerSecond * speedFactor,
            maxAttacksPerSecond = maxAttacksPerSecond,
            confidence = confidence(pet, socialization),
        )
    }

    /** Confianza (0..100): el equilibrio entre diversión, social e higiene. */
    fun confidence(pet: PetEntity, socialization: Double) =
        PetCombatBalance.MAX_CONFIDENCE * mean(pet.needs.happiness, socialization, pet.needs.hygiene)

    /** Media de necesidades como fracción 0..1. */
    private fun mean(vararg needs: Double) =
        (needs.sum() / needs.size / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0)
}
