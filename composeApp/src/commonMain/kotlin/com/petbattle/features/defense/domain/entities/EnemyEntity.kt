package com.petbattle.features.defense.domain.entities

import com.petbattle.shared_features.pet_skills.domain.PetProgression
import kotlin.math.min

/** A damage-over-time effect burning an enemy. */
data class EnemyBurn(
    val damagePerSecond: Double,
    val secondsLeft: Double,
    /** Pet that set it on fire: credited if the burn finishes the enemy. */
    val byPetId: String,
)

/**
 * One enemy of a defense run: its level, remaining hit points and the effects
 * currently on it (burns, slow). Immutable: every change returns a new enemy.
 */
data class EnemyEntity(
    val level: Int,
    val hp: Double,
    val burns: List<EnemyBurn> = emptyList(),
    /** Speed multiplier while slowed (1 = normal speed). */
    val slowFactor: Double = 1.0,
    val slowSecondsLeft: Double = 0.0,
    /** Pet whose damage landed last: the one credited for the kill. */
    val lastHitBy: String? = null,
) {
    val maxHp get() = maxHpFor(level)
    val isDefeated get() = hp <= EPSILON
    val isBurning get() = burns.isNotEmpty()
    val isSlowed get() = slowSecondsLeft > 0

    /** Multiplier to apply to the walking speed right now. */
    val speedFactor get() = if (isSlowed) slowFactor else 1.0

    /** Remaining health, 0..1. */
    val healthFraction get() = (hp / maxHp).coerceIn(0.0, 1.0)

    /** Instant damage. */
    fun hit(damage: Double, byPetId: String? = null) =
        if (damage <= 0) this else copy(hp = hp - damage, lastHitBy = byPetId ?: lastHitBy)

    /** Sets it on fire: [totalDamage] dealt evenly over [seconds]. Burns stack. */
    fun burn(totalDamage: Double, seconds: Double, byPetId: String): EnemyEntity {
        if (totalDamage <= 0 || seconds <= 0) return this
        return copy(burns = burns + EnemyBurn(totalDamage / seconds, seconds, byPetId))
    }

    /** Slows don't stack: the strongest factor and the longest time win. */
    fun slow(factor: Double, seconds: Double): EnemyEntity {
        if (seconds <= 0 || factor >= 1) return this
        return copy(
            slowFactor = if (isSlowed && slowFactor < factor) slowFactor else factor,
            slowSecondsLeft = maxOf(slowSecondsLeft, seconds),
        )
    }

    /** Lets [dt] seconds pass: burns deal their damage, effects wear off. */
    fun tick(dt: Double): EnemyEntity {
        if (dt <= 0 || (!isBurning && !isSlowed)) return this
        var damage = 0.0
        var burner: String? = null
        val remaining = mutableListOf<EnemyBurn>()
        for (b in burns) {
            val t = min(dt, b.secondsLeft)
            damage += b.damagePerSecond * t
            burner = b.byPetId
            if (b.secondsLeft - t > EPSILON) remaining += b.copy(secondsLeft = b.secondsLeft - t)
        }
        val slowLeft = slowSecondsLeft - dt
        return copy(
            hp = hp - damage,
            burns = remaining,
            slowSecondsLeft = if (slowLeft > 0) slowLeft else 0.0,
            slowFactor = if (slowLeft > 0) slowFactor else 1.0,
            lastHitBy = burner ?: lastHitBy,
        )
    }

    companion object {
        const val MIN_LEVEL = 1

        /** Hit points per enemy level: level 1 = 1.2, level 2 = 2.4... */
        const val HP_PER_LEVEL = 1.2

        /** Every defeated enemy is worth this much XP to its killer. */
        const val XP_REWARD = PetProgression.XP_PER_ENEMY

        // 1.2 * n is not exact in floating point.
        private const val EPSILON = 1e-9

        fun maxHpFor(level: Int) = HP_PER_LEVEL * level

        /** A fresh enemy of [level] (1 is the weakest) at full health. */
        fun spawn(level: Int): EnemyEntity {
            val l = maxOf(level, MIN_LEVEL)
            return EnemyEntity(level = l, hp = maxHpFor(l))
        }
    }
}
