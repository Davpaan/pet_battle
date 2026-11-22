package com.petbattle.shared_features.pet_species.domain

import com.petbattle.shared_features.biomes.domain.Biome

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

/**
 * Biomas donde vive cada especie (al menos uno por especie, y todo bioma tiene al menos una
 * especie). Aproximado: se afinará más adelante.
 */
val PetSpecies.biomes: List<Biome>
    get() = when (this) {
        PetSpecies.cardinal -> listOf(Biome.woodland, Biome.meadow)
        PetSpecies.blueJay -> listOf(Biome.woodland, Biome.coniferForest)
        PetSpecies.cedarWaxwing -> listOf(Biome.coniferForest, Biome.meadow)
        PetSpecies.chickadee -> listOf(Biome.coniferForest, Biome.woodland, Biome.tundra)
        PetSpecies.crow -> listOf(Biome.grassland, Biome.woodland, Biome.tundra, Biome.coast)
        PetSpecies.houseFinch -> listOf(Biome.grassland, Biome.highlands, Biome.meadow)
        PetSpecies.hummingbird -> listOf(Biome.meadow, Biome.mountain)
        PetSpecies.magpie -> listOf(Biome.grassland, Biome.highlands)
        PetSpecies.redRobin -> listOf(Biome.grassland, Biome.woodland)
        PetSpecies.stellersJay -> listOf(Biome.mountain, Biome.coniferForest)
        PetSpecies.whiteDove -> listOf(Biome.highlands, Biome.coast)
        PetSpecies.woodThrush -> listOf(Biome.woodland)
    }
