package com.petbattle.shared_features.pet_skills.domain.entities

/**
 * Un poder especial que una mascota desbloquea al alcanzar [unlockLevel]. Qué mascotas pueden
 * aprenderlo lo decide PetSkillsCatalog. Se compara por [id].
 */
class PetSkillEntity(
    val id: String,
    val name: String,
    val description: String,
    val unlockLevel: Int,
    /** Lo que hace al enemigo al que golpea. */
    val effect: SkillEffectEntity = DirectDamageEffect,
) {
    override fun equals(other: Any?) = other is PetSkillEntity && other.id == id
    override fun hashCode() = id.hashCode()
    override fun toString() = "PetSkillEntity($id)"
}
