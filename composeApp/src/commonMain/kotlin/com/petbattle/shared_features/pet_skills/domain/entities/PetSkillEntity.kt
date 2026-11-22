package com.petbattle.shared_features.pet_skills.domain.entities

/**
 * A special power a pet unlocks when it reaches [unlockLevel]. Which pets can
 * learn it is decided by PetSkillsCatalog. Compared by [id].
 */
class PetSkillEntity(
    val id: String,
    val name: String,
    val description: String,
    val unlockLevel: Int,
    /** What it does to the enemy it hits. */
    val effect: SkillEffectEntity = DirectDamageEffect,
) {
    override fun equals(other: Any?) = other is PetSkillEntity && other.id == id
    override fun hashCode() = id.hashCode()
    override fun toString() = "PetSkillEntity($id)"
}
