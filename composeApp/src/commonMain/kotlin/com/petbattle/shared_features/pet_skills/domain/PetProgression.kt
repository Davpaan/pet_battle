package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity

/**
 * Experiencia y niveles. 1 enemigo derrotado = 1 XP. Subir del nivel n al n+1 cuesta
 * `10 * (n + 1)` XP (10, 20, 30...), y el contador de XP vuelve a 0 en cada subida de nivel.
 * El nivel 10 es el máximo.
 */
object PetProgression {
    const val MAX_LEVEL = 10
    const val XP_PER_ENEMY = 1

    /** XP necesarios para pasar de [level] al siguiente; 0 en el máximo. */
    fun xpToNextLevel(level: Int) = if (level >= MAX_LEVEL) 0 else 10 * (level + 1)

    fun isMaxLevel(skills: PetSkillsEntity) = skills.level >= MAX_LEVEL

    /** Progreso hacia el siguiente nivel, de 0 a 1 (1 en el máximo). */
    fun levelProgress(skills: PetSkillsEntity): Double {
        val need = xpToNextLevel(skills.level)
        return if (need == 0) 1.0 else skills.xp.toDouble() / need
    }

    /** Suma [amount] XP; el sobrante tras una subida de nivel se descarta. */
    fun gainXp(skills: PetSkillsEntity, amount: Int): PetSkillsEntity {
        if (amount <= 0 || isMaxLevel(skills)) return skills
        val xp = skills.xp + amount
        val need = xpToNextLevel(skills.level)
        return if (xp < need) skills.copy(xp = xp)
        else skills.copy(level = skills.level + 1, xp = 0)
    }
}
