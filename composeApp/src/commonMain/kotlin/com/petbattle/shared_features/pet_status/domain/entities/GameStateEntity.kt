package com.petbattle.shared_features.pet_status.domain.entities

import com.petbattle.shared_features.biomes.domain.Biome
import kotlinx.serialization.Serializable

/**
 * La partida: todas las mascotas criadas, repartidas por biomas, el bioma que se está
 * visitando, la mascota que se cuida en la pantalla principal (siempre de ese bioma), el oro
 * y los biomas comprados.
 */
@Serializable
data class GameStateEntity(
    val pets: List<PetEntity> = emptyList(),
    /** `null` si el bioma actual está vacío. */
    val activeId: String? = null,
    /** `null` solo en partidas antiguas sin migrar (ver GameRules.migrate). */
    val currentBiome: Biome? = null,
    /** Monedas de oro: se ganan derrotando enemigos y se gastan en huevos y biomas. */
    val gold: Int = 0,
    /** Biomas comprados (el resto están bloqueados). */
    val unlockedBiomes: List<Biome> = emptyList(),
    /** El primer huevo es gratis; después cada uno cuesta. */
    val freeEggUsed: Boolean = false,
) {
    val active: PetEntity? get() = pets.firstOrNull { it.id == activeId }

    fun byId(id: String) = pets.firstOrNull { it.id == id }

    fun petsIn(biome: Biome) = pets.filter { it.biome == biome }

    fun isUnlocked(biome: Biome) = biome in unlockedBiomes

    /** Mascotas del bioma que se está visitando. */
    val biomePets: List<PetEntity> get() = currentBiome?.let(::petsIn).orEmpty()

    /** Misma partida con la mascota que tiene el id de [pet] sustituida por [pet]. */
    fun replace(pet: PetEntity) = copy(pets = pets.map { if (it.id == pet.id) pet else it })

    companion object {
        /** Partida vacía sin bioma (solo como punto de partida de las migraciones y los tests). */
        val empty = GameStateEntity()

        /** Partida nueva: en la pradera, que viene gratis, con el primer huevo gratis. */
        val newGame = GameStateEntity(currentBiome = Biome.grassland, unlockedBiomes = listOf(Biome.grassland))
    }
}
