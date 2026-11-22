package com.petbattle.shared_features.biomes.presentation

import com.petbattle.resources.Res
import com.petbattle.resources.biome_coast
import com.petbattle.resources.biome_conifer_forest
import com.petbattle.resources.biome_grassland
import com.petbattle.resources.biome_highlands
import com.petbattle.resources.biome_meadow
import com.petbattle.resources.biome_mountain
import com.petbattle.resources.biome_tundra
import com.petbattle.resources.biome_woodland
import com.petbattle.shared_features.biomes.domain.Biome
import org.jetbrains.compose.resources.DrawableResource

/** Datos de presentación de cada bioma: nombre visible y fondo. */
val Biome.label: String
    get() = when (this) {
        Biome.woodland -> "Bosque"
        Biome.grassland -> "Pradera"
        Biome.mountain -> "Montaña"
        Biome.coniferForest -> "Bosque de coníferas"
        Biome.meadow -> "Prado"
        Biome.tundra -> "Tundra"
        Biome.highlands -> "Tierras altas"
        Biome.coast -> "Costa"
    }

val Biome.background: DrawableResource
    get() = when (this) {
        Biome.woodland -> Res.drawable.biome_woodland
        Biome.grassland -> Res.drawable.biome_grassland
        Biome.mountain -> Res.drawable.biome_mountain
        Biome.coniferForest -> Res.drawable.biome_conifer_forest
        Biome.meadow -> Res.drawable.biome_meadow
        Biome.tundra -> Res.drawable.biome_tundra
        Biome.highlands -> Res.drawable.biome_highlands
        Biome.coast -> Res.drawable.biome_coast
    }
