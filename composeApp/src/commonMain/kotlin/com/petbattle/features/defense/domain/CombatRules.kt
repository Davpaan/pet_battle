package com.petbattle.features.defense.domain

import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.DirectDamageEffect
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.SkillEffectEntity
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect

/** What a hit did: the enemy afterwards and how far it was pushed back. */
data class HitOutcome(val enemy: EnemyEntity, val knockback: Double = 0.0)

/** How a skill's effect turns a hit into changes on the enemy. */
object CombatRules {
    /** [damage] already includes level and status (DefenseRules.shotDamage). */
    fun applyHit(enemy: EnemyEntity, damage: Double, effect: SkillEffectEntity, petId: String) =
        when (effect) {
            DirectDamageEffect -> HitOutcome(enemy.hit(damage, petId))
            is BurnEffect -> HitOutcome(enemy.burn(damage * effect.damageMultiplier, effect.seconds, petId))
            is SlowEffect -> HitOutcome(enemy.hit(damage, petId).slow(effect.speedFactor, effect.seconds))
            is KnockbackEffect -> HitOutcome(enemy.hit(damage, petId), knockback = effect.distance)
        }
}
