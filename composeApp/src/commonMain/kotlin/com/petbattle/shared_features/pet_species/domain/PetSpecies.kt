package com.petbattle.shared_features.pet_species.domain

/**
 * Which creature the player has chosen. Purely cosmetic for the status
 * rules; it decides the natural traits (element, type). Values are
 * persisted by name, so renaming one is a migration.
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
