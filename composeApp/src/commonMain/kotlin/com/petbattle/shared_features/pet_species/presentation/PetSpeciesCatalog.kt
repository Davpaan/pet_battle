package com.petbattle.shared_features.pet_species.presentation

import com.petbattle.resources.Res
import com.petbattle.resources.pet_blue_jay
import com.petbattle.resources.pet_cardinal
import com.petbattle.resources.pet_cedar_waxwing
import com.petbattle.resources.pet_chickadee
import com.petbattle.resources.pet_crow
import com.petbattle.resources.pet_house_finch
import com.petbattle.resources.pet_hummingbird
import com.petbattle.resources.pet_magpie
import com.petbattle.resources.pet_red_robin
import com.petbattle.resources.pet_stellers_jay
import com.petbattle.resources.pet_white_dove
import com.petbattle.resources.pet_wood_thrush
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import org.jetbrains.compose.resources.DrawableResource

/** Disposición común a todos los spritesheets de pájaros: cuadrícula 4x4 de frames de 64x64. */
object BirdSheet {
    /** Lado de un frame en texels; los tamaños en pantalla son múltiplos enteros. */
    const val FRAME_SIZE = 64
    const val COLUMNS = 4

    /** Índice de fila de cada animación en la hoja. */
    const val FLY_ROW = 0
    const val REACT_ROW = 1
    const val IDLE_ROW = 2
    const val HOP_ROW = 3
}

/** Datos de presentación de cada especie: nombre visible y spritesheet. */
val PetSpecies.label: String
    get() = when (this) {
        PetSpecies.cardinal -> "Cardenal"
        PetSpecies.blueJay -> "Arrendajo azul"
        PetSpecies.cedarWaxwing -> "Ampelis"
        PetSpecies.chickadee -> "Carbonero"
        PetSpecies.crow -> "Cuervo"
        PetSpecies.houseFinch -> "Pinzón"
        PetSpecies.hummingbird -> "Colibrí"
        PetSpecies.magpie -> "Urraca"
        PetSpecies.redRobin -> "Petirrojo"
        PetSpecies.stellersJay -> "Chara"
        PetSpecies.whiteDove -> "Paloma blanca"
        PetSpecies.woodThrush -> "Zorzal"
    }

val PetSpecies.sheet: DrawableResource
    get() = when (this) {
        PetSpecies.cardinal -> Res.drawable.pet_cardinal
        PetSpecies.blueJay -> Res.drawable.pet_blue_jay
        PetSpecies.cedarWaxwing -> Res.drawable.pet_cedar_waxwing
        PetSpecies.chickadee -> Res.drawable.pet_chickadee
        PetSpecies.crow -> Res.drawable.pet_crow
        PetSpecies.houseFinch -> Res.drawable.pet_house_finch
        PetSpecies.hummingbird -> Res.drawable.pet_hummingbird
        PetSpecies.magpie -> Res.drawable.pet_magpie
        PetSpecies.redRobin -> Res.drawable.pet_red_robin
        PetSpecies.stellersJay -> Res.drawable.pet_stellers_jay
        PetSpecies.whiteDove -> Res.drawable.pet_white_dove
        PetSpecies.woodThrush -> Res.drawable.pet_wood_thrush
    }
