package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_skills.domain.entities.PetProgressionEntity
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.biomes
import com.petbattle.shared_features.pet_species.domain.diet
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.random.Random

/**
 * Cómo crece un polluelo. Al comer [MEALS_TO_GROW] veces se convierte en una especie adulta
 * de su bioma cuya dieta incluya su comida favorita (la que más veces ha comido):
 * - si hay empate entre varias comidas o varias especies encajan, se elige al azar entre ellas;
 * - si ninguna especie de ese bioma come eso, se elige al azar cualquiera del bioma.
 */
object GrowthRules {
    const val MEALS_TO_GROW = 10

    fun mealsEaten(pet: PetEntity) = pet.meals.values.sum()

    /** Apunta que el polluelo ha comido [food]. Los adultos no cuentan comidas. */
    fun recordMeal(pet: PetEntity, food: Food): PetEntity =
        if (!pet.species.isChick) pet
        else pet.copy(meals = pet.meals + (food to (pet.meals[food] ?: 0) + 1))

    fun shouldGrow(pet: PetEntity) = pet.species.isChick && mealsEaten(pet) >= MEALS_TO_GROW

    /** Especie adulta en la que se convierte un polluelo de [biome] que ha comido [meals]. */
    fun adultFor(biome: Biome, meals: Map<Food, Int>, random: Random): PetSpecies {
        val inBiome = PetSpecies.adults.filter { biome in it.biomes }
        val top = meals.values.maxOrNull() ?: 0
        val favourites = meals.filterValues { it == top && it > 0 }.keys
        val matching = inBiome.filter { species -> species.diet.any { it in favourites } }
        return matching.ifEmpty { inBiome }.random(random)
    }

    /**
     * El polluelo crece: nueva especie (la destinada si nació de una pareja; si no, la que decide
     * su comida) y sus rasgos de nivel 0. Conserva nombre, estado y bioma.
     */
    fun grow(pet: PetEntity, random: Random): PetEntity {
        // Los hijos de una pareja crecen a la especie de sus padres, coman lo que coman.
        val adult = pet.destinedSpecies ?: adultFor(pet.biome, pet.meals, random)
        return pet.copy(species = adult, progression = PetProgressionEntity.forSpecies(adult), meals = emptyMap(), destinedSpecies = null)
    }
}
