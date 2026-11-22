package com.petbattle.shared_features.pet_species.domain

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food

/**
 * Qué criatura es la mascota. El jugador nunca la elige: toda mascota nace como [chick]
 * (polluelo) y al crecer se convierte en una especie adulta según su bioma y lo que ha
 * comido (ver GrowthRules). Decide los rasgos naturales (elemento, tipo). Los valores se
 * persisten por nombre, así que renombrar uno es una migración.
 */
enum class PetSpecies {
    /** Polluelo genérico: come de todo, vive en cualquier bioma y no combate. */
    chick,
    cardinal,
    blueJay,
    cedarWaxwing,
    chickadee,
    crow,
    houseFinch,
    hummingbird,
    magpie,
    redRobin,
    /** Abejaruco. Sustituye a la antigua chara (`stellersJay`, ver LocalPetRepository). */
    beeEater,
    whiteDove,
    woodThrush,

    /** Cernícalo: pequeña rapaz de campo abierto. */
    kestrel,

    /** Chucao: pájaro del sotobosque que lleva la cola levantada. */
    chucao;

    companion object {
        val fallback = cardinal

        /** Especies en las que puede convertirse un polluelo. */
        val adults: List<PetSpecies> get() = entries.filter { it != chick }
    }
}

val PetSpecies.isChick: Boolean get() = this == PetSpecies.chick

/**
 * Biomas donde vive cada especie (al menos uno por especie, y todo bioma tiene al menos una
 * especie). Aproximado: se afinará más adelante.
 */
val PetSpecies.biomes: List<Biome>
    get() = when (this) {
        PetSpecies.chick -> Biome.entries.toList()
        PetSpecies.cardinal -> listOf(Biome.woodland, Biome.meadow)
        PetSpecies.blueJay -> listOf(Biome.woodland, Biome.coniferForest)
        PetSpecies.cedarWaxwing -> listOf(Biome.coniferForest, Biome.meadow)
        PetSpecies.chickadee -> listOf(Biome.coniferForest, Biome.woodland, Biome.tundra)
        PetSpecies.crow -> listOf(Biome.grassland, Biome.woodland, Biome.tundra, Biome.coast)
        PetSpecies.houseFinch -> listOf(Biome.grassland, Biome.highlands, Biome.meadow)
        PetSpecies.hummingbird -> listOf(Biome.meadow, Biome.mountain)
        PetSpecies.magpie -> listOf(Biome.grassland, Biome.highlands)
        PetSpecies.redRobin -> listOf(Biome.grassland, Biome.woodland)
        PetSpecies.beeEater -> listOf(Biome.meadow, Biome.grassland, Biome.mountain)
        PetSpecies.whiteDove -> listOf(Biome.highlands, Biome.coast)
        PetSpecies.woodThrush -> listOf(Biome.woodland)
        PetSpecies.kestrel -> listOf(Biome.grassland, Biome.highlands, Biome.mountain, Biome.coast)
        PetSpecies.chucao -> listOf(Biome.woodland, Biome.coniferForest)
    }

/**
 * Comidas que acepta cada especie; cualquier otra la rechaza. Toda comida la acepta al menos
 * una especie.
 */
val PetSpecies.diet: List<Food>
    get() = when (this) {
        PetSpecies.chick -> Food.entries.toList()
        PetSpecies.cardinal -> listOf(Food.seeds, Food.fruit, Food.insects)
        PetSpecies.blueJay -> listOf(Food.nuts, Food.seeds, Food.insects)
        PetSpecies.cedarWaxwing -> listOf(Food.fruit, Food.insects)
        PetSpecies.chickadee -> listOf(Food.seeds, Food.insects)
        PetSpecies.crow -> listOf(Food.carrion, Food.seeds, Food.insects, Food.worms, Food.nuts)
        PetSpecies.houseFinch -> listOf(Food.seeds, Food.fruit)
        PetSpecies.hummingbird -> listOf(Food.nectar, Food.insects)
        PetSpecies.magpie -> listOf(Food.carrion, Food.insects, Food.fruit)
        PetSpecies.redRobin -> listOf(Food.worms, Food.insects, Food.fruit)
        PetSpecies.beeEater -> listOf(Food.insects)
        PetSpecies.whiteDove -> listOf(Food.seeds)
        PetSpecies.woodThrush -> listOf(Food.insects, Food.worms, Food.fruit)
        PetSpecies.kestrel -> listOf(Food.carrion, Food.insects)
        PetSpecies.chucao -> listOf(Food.insects, Food.worms, Food.seeds)
    }
