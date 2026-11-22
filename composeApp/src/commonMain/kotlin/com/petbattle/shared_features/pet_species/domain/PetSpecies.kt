package com.petbattle.shared_features.pet_species.domain

/**
 * Qué criatura ha elegido el jugador. Puramente cosmética para las reglas de estado; decide
 * los rasgos naturales (elemento, tipo). Los valores se persisten por nombre, así que
 * renombrar uno es una migración.
 */
enum class PetSpecies {
    cardinal,
    blueJay,
    cedarWaxwing,
    chickadee,
    crow,
    houseFinch,
    hummingbird,
    magpie,
    redRobin,
    stellersJay,
    whiteDove,
    woodThrush;

    companion object {
        val fallback = cardinal
    }
}
