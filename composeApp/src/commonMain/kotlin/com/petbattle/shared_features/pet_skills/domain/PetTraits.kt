package com.petbattle.shared_features.pet_skills.domain

import com.petbattle.shared_features.pet_species.domain.PetSpecies

/** Elemento clásico de una mascota. Determina las habilidades propias del elemento. */
enum class PetElement { fire, air, water, earth }

/** Tipo (alineamiento) de una mascota. Determina las habilidades propias del tipo. */
enum class PetType { light, darkness, elemental }

/**
 * Elemento de cada especie (determina sus habilidades de elemento). El del polluelo no se usa:
 * no combate, y al crecer toma el de su especie adulta.
 */
val PetSpecies.element: PetElement
    get() = when (this) {
        PetSpecies.chick -> PetElement.earth
        PetSpecies.cardinal, PetSpecies.houseFinch, PetSpecies.kestrel -> PetElement.fire
        PetSpecies.woodThrush -> PetElement.water
        PetSpecies.crow, PetSpecies.magpie, PetSpecies.redRobin -> PetElement.earth
        PetSpecies.blueJay, PetSpecies.cedarWaxwing, PetSpecies.chickadee,
        PetSpecies.hummingbird, PetSpecies.beeEater, PetSpecies.whiteDove, PetSpecies.chucao,
        -> PetElement.air
    }

/** Todos son elementales salvo el cuervo (oscuridad) y la paloma blanca (luz). */
val PetSpecies.type: PetType
    get() = when (this) {
        PetSpecies.crow -> PetType.darkness
        PetSpecies.whiteDove -> PetType.light
        else -> PetType.elemental
    }
