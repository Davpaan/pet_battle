package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_skills.domain.entities.PetProgressionEntity
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect
import com.petbattle.shared_features.pet_species.domain.PetSpecies

/**
 * Qué habilidades puede aprender cada mascota. Las habilidades provienen de tres fuentes: su
 * elemento, su tipo, y una especie concreta *con* un tipo concreto (así un tipo puede
 * desarrollar habilidades distintas según la especie).
 *
 * Toda mascota conoce la habilidad de su elemento desde el nivel 0: es su ataque básico
 * ([basicAttackFor]). Las tablas de tipo y de especie+tipo siguen vacías.
 */
object PetSkillsCatalog {
    /** Fuego: 1,2x el daño del golpe, aplicado como quemadura durante 3 segundos. */
    val fireBurn = PetSkillEntity(
        id = "fire_burn",
        name = "Llamarada",
        description = "Quema al enemigo: 120% del daño repartido en 3 s.",
        unlockLevel = 0,
        effect = BurnEffect(damageMultiplier = 1.2, seconds = 3.0),
    )

    /** Agua: ralentiza al objetivo. */
    val waterSlow = PetSkillEntity(
        id = "water_slow",
        name = "Marea",
        description = "Empapa al enemigo: va a la mitad de velocidad 2 s.",
        unlockLevel = 0,
        effect = SlowEffect(speedFactor = 0.5, seconds = 2.0),
    )

    /** Aire: empuja al objetivo un poco hacia atrás por su camino. */
    val airGust = PetSkillEntity(
        id = "air_gust",
        name = "Ráfaga",
        description = "Empuja al enemigo un poco hacia atrás.",
        unlockLevel = 0,
        effect = KnockbackEffect(distance = 30.0),
    )

    /** Tierra: una roca simple y honesta. */
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

    /** Habilidades que un tipo solo desarrolla en una especie dada. */
    val bySpeciesType: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = emptyMap()

    /** Todo lo que una mascota podría llegar a aprender, desbloqueado o no. */
    fun allFor(
        species: PetSpecies,
        skills: PetProgressionEntity,
        elementTable: Map<PetElement, List<PetSkillEntity>> = byElement,
        typeTable: Map<PetType, List<PetSkillEntity>> = byType,
        speciesTypeTable: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = bySpeciesType,
    ): List<PetSkillEntity> = linkedSetOf<PetSkillEntity>().apply {
        addAll(elementTable[skills.element].orEmpty())
        addAll(typeTable[skills.type].orEmpty())
        addAll(speciesTypeTable[species to skills.type].orEmpty())
    }.toList()

    /** Habilidades ya disponibles en el nivel actual de la mascota. */
    fun unlockedFor(
        species: PetSpecies,
        skills: PetProgressionEntity,
        elementTable: Map<PetElement, List<PetSkillEntity>> = byElement,
        typeTable: Map<PetType, List<PetSkillEntity>> = byType,
        speciesTypeTable: Map<Pair<PetSpecies, PetType>, List<PetSkillEntity>> = bySpeciesType,
    ) = allFor(species, skills, elementTable, typeTable, speciesTypeTable)
        .filter { skills.level >= it.unlockLevel }

    /** La habilidad con la que ataca una mascota: la de su elemento. */
    fun basicAttackFor(skills: PetProgressionEntity): PetSkillEntity =
        byElement.getValue(skills.element).first()
}
