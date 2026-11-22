package com.petbattle.shared_features.pet_skills.domain.entities

/**
 * Lo que hace una habilidad al enemigo al que golpea, además de (o en lugar de) el daño
 * normal. Solo datos: las reglas de combate (feature defense) lo interpretan.
 */
sealed interface SkillEffectEntity

/** Golpe normal: todo el daño se aplica de una vez (tierra). */
data object DirectDamageEffect : SkillEffectEntity

/** Quemadura (fuego): sin daño instantáneo; daño × [damageMultiplier] durante [seconds]. */
data class BurnEffect(val damageMultiplier: Double, val seconds: Double) : SkillEffectEntity

/** Ralentización (agua): se mueve a [speedFactor] de su velocidad durante [seconds]. */
data class SlowEffect(val speedFactor: Double, val seconds: Double) : SkillEffectEntity

/** Empujón (aire): lo empuja [distance] px de mapa hacia atrás por su camino. */
data class KnockbackEffect(val distance: Double) : SkillEffectEntity
