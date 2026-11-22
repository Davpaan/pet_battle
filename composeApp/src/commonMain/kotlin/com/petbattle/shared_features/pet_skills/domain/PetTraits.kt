package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_species.domain.PetSpecies

/** Classical element of a pet. Drives element-specific skills. */
enum class PetElement { fire, air, water, earth }

/** Alignment of a pet. Drives type-specific skills. */
enum class PetType { light, darkness, elemental }

/**
 * Where a species usually lives. Only used to derive [PetElement], so the
 * classification reads as natural history instead of an arbitrary table.
 */
enum class Habitat(val element: PetElement) {
    /** Hot, dry scrub and desert edges. */
    aridScrub(PetElement.fire),

    /** Tree canopy, open sky: lives mostly in the air. */
    canopy(PetElement.air),

    /** Damp forest floor, streams, wetlands. */
    wetland(PetElement.water),

    /** Forages on the ground: fields, lawns, open country. */
    ground(PetElement.earth),
}

/** Natural traits of each species (see PetSkillsEntity.forSpecies). */
val PetSpecies.habitat: Habitat
    get() = when (this) {
        // Thickets and Sonoran desert scrub.
        PetSpecies.cardinal -> Habitat.aridScrub
        // Originally a bird of the western deserts.
        PetSpecies.houseFinch -> Habitat.aridScrub
        // Damp deciduous forest floor near streams.
        PetSpecies.woodThrush -> Habitat.wetland
        // Ground foragers of fields, lawns and open country.
        PetSpecies.crow, PetSpecies.magpie, PetSpecies.redRobin -> Habitat.ground
        // Canopy dwellers and aerial specialists.
        PetSpecies.blueJay, PetSpecies.cedarWaxwing, PetSpecies.chickadee,
        PetSpecies.hummingbird, PetSpecies.stellersJay, PetSpecies.whiteDove,
        -> Habitat.canopy
    }

/** Element derived from the habitat. */
val PetSpecies.element: PetElement get() = habitat.element

/** Everyone is elemental except the crow (darkness) and the white dove (light). */
val PetSpecies.type: PetType
    get() = when (this) {
        PetSpecies.crow -> PetType.darkness
        PetSpecies.whiteDove -> PetType.light
        else -> PetType.elemental
    }
