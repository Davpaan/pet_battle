package com.petbattle.shared_features.biomes.presentation

import com.petbattle.resources.Res
import com.petbattle.resources.biome_coast
import com.petbattle.resources.biome_conifer_forest
import com.petbattle.resources.biome_grassland
import com.petbattle.resources.biome_grassland_hills
import com.petbattle.resources.biome_grassland_sky
import com.petbattle.resources.biome_grassland_terrain
import com.petbattle.resources.biome_grassland_wind
import com.petbattle.resources.biome_highlands
import com.petbattle.resources.biome_meadow
import com.petbattle.resources.biome_mountain
import com.petbattle.resources.biome_tundra
import com.petbattle.resources.biome_woodland
import com.petbattle.resources.biome_woodland_back
import com.petbattle.resources.biome_woodland_sky
import com.petbattle.resources.biome_woodland_terrain
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

/**
 * Fondo partido en capas para el efecto parallax, de atrás adelante: [sky] (cielo y nubes),
 * [back] (elementos de fondo, como las colinas) y [terrain] (el suelo, que no se desplaza).
 */
class BiomeLayers(
    val sky: DrawableResource,
    val back: DrawableResource,
    val terrain: DrawableResource,
    /**
     * Mapa de viento del terreno (rojo = árboles, verde = hierba; ver WindPainter): si lo hay,
     * el terreno se dibuja mecido por el viento.
     */
    val wind: DrawableResource? = null,
    /** Capas ilustradas (no pixel art): se dibujan suavizadas al escalarlas. */
    val smooth: Boolean = false,
)

/** Capas del fondo si el bioma las tiene; si no, se dibuja [background] entero. */
val Biome.backgroundLayers: BiomeLayers?
    get() = when (this) {
        Biome.grassland -> BiomeLayers(
            sky = Res.drawable.biome_grassland_sky,
            back = Res.drawable.biome_grassland_hills,
            terrain = Res.drawable.biome_grassland_terrain,
            wind = Res.drawable.biome_grassland_wind,
            smooth = true,
        )
        Biome.woodland -> BiomeLayers(
            sky = Res.drawable.biome_woodland_sky,
            back = Res.drawable.biome_woodland_back,
            terrain = Res.drawable.biome_woodland_terrain,
        )
        else -> null
    }
