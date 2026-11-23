package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import kotlin.random.Random

/** Algo que ha pasado en la vida de las mascotas y conviene contar al jugador. */
sealed interface LifeEvent {
    /** Una pareja ha puesto un huevo. */
    data class EggLaid(val parents: Pair<String, String>, val eggName: String) : LifeEvent

    /** Un huevo ha eclosionado. */
    data class Hatched(val name: String) : LifeEvent
}

/**
 * Cría. Dos adultos de la misma especie que viven en el mismo bioma forman pareja (si hay más,
 * se emparejan de dos en dos). Una vez al día (fecha local), la primera vez que **los dos**
 * tienen la satisfacción global (PetDerivedNeeds.satisfaction) por encima de
 * [SATISFACTION_TO_BREED], tienen un [BREED_CHANCE] de poner un huevo. El huevo eclosiona a las
 * [EGG_HATCH_MS] en un polluelo destinado a la especie de sus padres, con un tier que hereda de
 * ellos (ver TierRules.child).
 */
object BreedingRules {
    const val SATISFACTION_TO_BREED = 90.0
    const val BREED_CHANCE = 0.5
    const val EGG_HATCH_MS = 8L * 3_600_000

    /** Prefijo de los nombres automáticos de los huevos ("Cría 1", "Cría 2"...). */
    private const val EGG_NAME_PREFIX = "Cría"

    /**
     * Un paso de la vida de la partida en [now]: eclosionan los huevos a los que les toca y las
     * parejas que pueden intentan criar ([today] = día local). Devuelve la partida y lo ocurrido.
     */
    fun step(gameState: GameStateEntity, now: Long, today: Long, random: Random): Pair<GameStateEntity, List<LifeEvent>> {
        val events = mutableListOf<LifeEvent>()
        var state = hatchDueEggs(gameState, now, events)
        state = breed(state, now, today, random, events)
        return state to events
    }

    private fun hatchDueEggs(gameState: GameStateEntity, now: Long, events: MutableList<LifeEvent>): GameStateEntity {
        var state = gameState
        for (egg in gameState.pets.filter { it.isEgg && it.hatchesAt!! <= now }) {
            state = state.replace(egg.copy(hatchesAt = null, needs = PetNeedsEntity.initial(now)))
            // Si nace en el bioma que se está viendo y no hay ninguna seleccionada, se selecciona.
            if (state.activeId == null && state.currentBiome == egg.biome) state = state.copy(activeId = egg.id)
            events += LifeEvent.Hatched(egg.name)
        }
        return state
    }

    private fun breed(gameState: GameStateEntity, now: Long, today: Long, random: Random, events: MutableList<LifeEvent>): GameStateEntity {
        var state = gameState
        val adults = gameState.pets.filter { !it.isEgg && !it.species.isChick }
        val pairs = adults.groupBy { it.biome to it.species }.values
            .flatMap { group -> group.sortedBy { it.id }.chunked(2).filter { it.size == 2 } }
        for ((a, b) in pairs) {
            if (!canBreed(a, b, state, today)) continue
            // Una tirada al día por pareja, salga o no el huevo.
            state = state.replace(a.copy(lastBreedingDay = today)).replace(b.copy(lastBreedingDay = today))
            if (random.nextDouble() >= BREED_CHANCE) continue
            val name = nextEggName(state)
            val egg = PetEntity.egg(
                name = name, parents = a.species, biome = a.biome, now = now,
                hatchesAt = now + EGG_HATCH_MS, id = "egg-$now-${state.pets.size}",
                tier = TierRules.child(a.tier, b.tier, random),
            )
            state = state.copy(pets = state.pets + egg)
            events += LifeEvent.EggLaid(a.name to b.name, name)
        }
        return state
    }

    /** La pareja aún no lo ha intentado hoy y los dos están satisfechos. */
    fun canBreed(a: PetEntity, b: PetEntity, gameState: GameStateEntity, today: Long) =
        a.lastBreedingDay != today && b.lastBreedingDay != today &&
            PetDerivedNeeds.satisfaction(a, gameState) > SATISFACTION_TO_BREED &&
            PetDerivedNeeds.satisfaction(b, gameState) > SATISFACTION_TO_BREED

    /** El primer "Cría N" que no use ya otra mascota. */
    private fun nextEggName(gameState: GameStateEntity): String {
        var n = 1
        while (gameState.pets.any { it.name.equals("$EGG_NAME_PREFIX $n", ignoreCase = true) }) n++
        return "$EGG_NAME_PREFIX $n"
    }
}
