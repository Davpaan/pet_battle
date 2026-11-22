package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_species.domain.PetSpecies

/** Elemento clásico de una mascota. Determina las habilidades propias del elemento. */
enum class PetElement { fire, air, water, earth }

/** Tipo (alineamiento) de una mascota. Determina las habilidades propias del tipo. */
enum class PetType { light, darkness, elemental }

/**
 * Dónde suele vivir una especie. Solo se usa para derivar [PetElement], de modo que la
 * clasificación se lea como historia natural y no como una tabla arbitraria.
 */
enum class Habitat(val element: PetElement) {
    /** Matorral cálido y seco y bordes del desierto. */
    aridScrub(PetElement.fire),

    /** Copas de árboles y cielo abierto: vive sobre todo en el aire. */
    canopy(PetElement.air),

    /** Suelo húmedo del bosque, arroyos, humedales. */
    wetland(PetElement.water),

    /** Busca comida en el suelo: campos, céspedes, campo abierto. */
    ground(PetElement.earth),
}

/** Rasgos naturales de cada especie (ver PetSkillsEntity.forSpecies). */
val PetSpecies.habitat: Habitat
    get() = when (this) {
        // Matorrales y maleza del desierto de Sonora.
        PetSpecies.cardinal -> Habitat.aridScrub
        // Originalmente un ave de los desiertos del oeste.
        PetSpecies.houseFinch -> Habitat.aridScrub
        // Suelo húmedo de bosque caducifolio cerca de arroyos.
        PetSpecies.woodThrush -> Habitat.wetland
        // Buscan comida en el suelo de campos, céspedes y campo abierto.
        PetSpecies.crow, PetSpecies.magpie, PetSpecies.redRobin -> Habitat.ground
        // Habitantes de las copas y especialistas del aire.
        PetSpecies.blueJay, PetSpecies.cedarWaxwing, PetSpecies.chickadee,
        PetSpecies.hummingbird, PetSpecies.stellersJay, PetSpecies.whiteDove,
        -> Habitat.canopy
    }

/** Elemento derivado del hábitat. */
val PetSpecies.element: PetElement get() = habitat.element

/** Todos son elementales salvo el cuervo (oscuridad) y la paloma blanca (luz). */
val PetSpecies.type: PetType
    get() = when (this) {
        PetSpecies.crow -> PetType.darkness
        PetSpecies.whiteDove -> PetType.light
        else -> PetType.elemental
    }
