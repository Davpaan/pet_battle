package com.petbattle.shared_features.pet_food.domain

/**
 * Tipos de comida que se le pueden ofrecer a una mascota. Cada especie solo acepta los de su
 * dieta (ver PetSpecies.diet). Los valores se persisten por nombre, así que renombrar uno es
 * una migración.
 */
enum class Food {
    /** Semillas y granos (pinzones, palomas, cardenales...). */
    seeds,

    /** Frutos secos: bellotas, avellanas, piñones (arrendajos). */
    nuts,

    /** Fruta y bayas. */
    fruit,

    /** Insectos y larvas: la base de casi todos los pájaros en cría. */
    insects,

    /** Lombrices y gusanos que se sacan del suelo (petirrojos, zorzales). */
    worms,

    /** Néctar de flores (colibríes). */
    nectar,

    /** Carroña y restos (córvidos). */
    carrion,
}
