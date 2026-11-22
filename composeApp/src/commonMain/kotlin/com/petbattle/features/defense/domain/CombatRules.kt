package com.petbattle.features.defense.domain

import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.DirectDamageEffect
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.SkillEffectEntity
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect

/** Lo que hizo un golpe: el enemigo resultante y cuánto fue empujado hacia atrás. */
data class HitOutcome(val enemy: EnemyEntity, val knockback: Double = 0.0)

/** Cómo el efecto de una habilidad convierte un golpe en cambios sobre el enemigo. */
object CombatRules {
    /** [damage] ya incluye nivel y estado (DefenseRules.shotDamage). */
    fun applyHit(enemy: EnemyEntity, damage: Double, effect: SkillEffectEntity, petId: String) =
        when (effect) {
            DirectDamageEffect -> HitOutcome(enemy.hit(damage, petId))
            is BurnEffect -> HitOutcome(enemy.burn(damage * effect.damageMultiplier, effect.seconds, petId))
            is SlowEffect -> HitOutcome(enemy.hit(damage, petId).slow(effect.speedFactor, effect.seconds))
            is KnockbackEffect -> HitOutcome(enemy.hit(damage, petId), knockback = effect.distance)
        }
}
