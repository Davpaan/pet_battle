package com.petbattle.shared_features.pet_skills.domain.entities

/**
 * What a skill does to the enemy it hits, on top of (or instead of) the plain
 * damage. Pure data: combat rules (defense feature) interpret it.
 */
sealed interface SkillEffectEntity

/** Plain hit: all the damage lands at once (earth). */
data object DirectDamageEffect : SkillEffectEntity

/** Burn (fire): no instant damage; damage × [damageMultiplier] over [seconds]. */
data class BurnEffect(val damageMultiplier: Double, val seconds: Double) : SkillEffectEntity

/** Slow (water): moves at [speedFactor] of its speed for [seconds]. */
data class SlowEffect(val speedFactor: Double, val seconds: Double) : SkillEffectEntity

/** Knockback (air): pushed [distance] map px back along its path. */
data class KnockbackEffect(val distance: Double) : SkillEffectEntity
