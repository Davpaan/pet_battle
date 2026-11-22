package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect
import com.petbattle.shared_features.pet_species.domain.PetSpecies

/**
 * Which skills each pet can learn. Skills come from three sources: its
 * element, its type, and a specific species *with* a specific type (so a type
 * can develop different skills depending on the species).
 *
 * Every pet knows the skill of its element from level 0: it's its basic
 * attack ([basicAttackFor]). Type and species+type tables are still empty.
 */
object PetSkillsCatalog {
    /** Fire: 1.2x the hit's damage, dealt as a burn over 3 seconds. */
    val fireBurn = PetSkillEntity(
        id = "fire_burn",
        name = "Llamarada",
        description = "Quema al enemigo: 120% del daño repartido en 3 s.",
        unlockLevel = 0,
        effect = BurnEffect(damageMultiplier = 1.2, seconds = 3.0),
    )

    /** Water: slows the target down. */
    val waterSlow = PetSkillEntity(
        id = "water_slow",
        name = "Marea",
        description = "Empapa al enemigo: va a la mitad de velocidad 2 s.",
        unlockLevel = 0,
        effect = SlowEffect(speedFactor = 0.5, seconds = 2.0),
    )

    /** Air: pushes the target a little back along its path. */
    val airGust = PetSkillEntity(
        id = "air_gust",
        name = "Ráfaga",
        description = "Empuja al enemigo un poco hacia atrás.",
        unlockLevel = 0,
        effect = KnockbackEffect(distance = 30.0),
    )

    /** Earth: a plain, honest rock. */
    val earthRock = PetSkillEntity(
        id = "earth_rock",
        name = "Roca",
        description = "Golpe normal, sin efectos.",
        unlockLevel = 0,
    )

    val byElement: Map<PetElement, List<PetSkillEntity>> = mapOf(
        PetElement.fire to listOf(fireBurn),
        PetElement.water to listOf(waterSlow),
        PetElement.air to listOf(airGust),
        PetElement.earth to listOf(earthRock),
    )

    val byType: Map<PetType, List<PetSkillEntity>> = emptyMap()

    /** Skills a type only develops in a given species. */
    val bySpeciesType: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = emptyMap()

    /** Everything a pet could ever learn, unlocked or not. */
    fun allFor(
        species: PetSpecies,
        skills: PetSkillsEntity,
        elementTable: Map<PetElement, List<PetSkillEntity>> = byElement,
        typeTable: Map<PetType, List<PetSkillEntity>> = byType,
        speciesTypeTable: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = bySpeciesType,
    ): List<PetSkillEntity> = linkedSetOf<PetSkillEntity>().apply {
        addAll(elementTable[skills.element].orEmpty())
        addAll(typeTable[skills.type].orEmpty())
        addAll(speciesTypeTable[species to skills.type].orEmpty())
    }.toList()

    /** Skills already available at the pet's current level. */
    fun unlockedFor(
        species: PetSpecies,
        skills: PetSkillsEntity,
        elementTable: Map<PetElement, List<PetSkillEntity>> = byElement,
        typeTable: Map<PetType, List<PetSkillEntity>> = byType,
        speciesTypeTable: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = bySpeciesType,
    ) = allFor(species, skills, elementTable, typeTable, speciesTypeTable)
        .filter { skills.level >= it.unlockLevel }

    /** The skill a pet attacks with: the one of its element. */
    fun basicAttackFor(skills: PetSkillsEntity): PetSkillEntity =
        byElement.getValue(skills.element).first()
}
