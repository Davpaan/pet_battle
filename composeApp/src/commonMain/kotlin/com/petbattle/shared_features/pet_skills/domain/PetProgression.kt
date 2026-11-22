package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity

/**
 * Experience and levels. 1 defeated enemy = 1 XP. Reaching level n+1 from
 * level n costs `10 * (n + 1)` XP (10, 20, 30...), and the XP counter resets
 * to 0 on every level-up. Level 10 is the cap.
 */
object PetProgression {
    const val MAX_LEVEL = 10
    const val XP_PER_ENEMY = 1

    /** XP needed to go from [level] to the next one; 0 at the cap. */
    fun xpToNextLevel(level: Int) = if (level >= MAX_LEVEL) 0 else 10 * (level + 1)

    fun isMaxLevel(skills: PetSkillsEntity) = skills.level >= MAX_LEVEL

    /** Progress towards the next level, 0..1 (1 at the cap). */
    fun levelProgress(skills: PetSkillsEntity): Double {
        val need = xpToNextLevel(skills.level)
        return if (need == 0) 1.0 else skills.xp.toDouble() / need
    }

    /** Adds [amount] XP; leftover beyond a level-up is discarded. */
    fun gainXp(skills: PetSkillsEntity, amount: Int): PetSkillsEntity {
        if (amount <= 0 || isMaxLevel(skills)) return skills
        val xp = skills.xp + amount
        val need = xpToNextLevel(skills.level)
        return if (xp < need) skills.copy(xp = xp)
        else skills.copy(level = skills.level + 1, xp = 0)
    }
}
