package com.petbattle.shared_features.biomes.domain

/**
 * Ecosistema donde puede vivir una especie. Cada bioma tiene su fondo (ver BiomeCatalog).
 * Los valores se persisten por nombre, así que renombrar uno es una migración.
 */
enum class Biome {
    /** Bosque caducifolio con árboles grandes. */
    woodland,

    /** Pradera abierta de colinas suaves. */
    grassland,

    /** Alta montaña con cumbres nevadas. */
    mountain,

    /** Bosque de coníferas. */
    coniferForest,

    /** Prado con flores y árboles dispersos. */
    meadow,

    /** Tundra helada bajo la aurora. */
    tundra,

    /** Tierras altas rocosas. */
    highlands,

    /** Costa de acantilados. */
    coast,
}
