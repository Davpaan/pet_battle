package com.petbattle.shared_features.pet_status.application

import com.petbattle.core.config.AppConfig
import com.petbattle.core.time.Clock
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.PetNameError
import com.petbattle.shared_features.pet_status.domain.PetRepository
import com.petbattle.shared_features.pet_status.domain.PetStatusRules
import com.petbattle.shared_features.pet_status.domain.RosterRules
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Orquesta todas las mascotas criadas: carga el plantel, aplica la pérdida offline a
 * la mascota activa, ejecuta su tick en vivo, traduce las intenciones del jugador en
 * llamadas a [PetStatusRules] / [RosterRules] y guarda automáticamente cada cambio.
 *
 * [roster] es `null` mientras se carga.
 */
class PetController(
    private val repository: PetRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val autoTick: Boolean = true,
    private val random: Random = Random.Default,
) {
    private val _roster = MutableStateFlow<PetRosterEntity?>(null)
    val roster: StateFlow<PetRosterEntity?> = _roster.asStateFlow()

    private var ticker: Job? = null
    private var pendingSave: Job? = null

    /** Carga la partida (o un plantel vacío) e inicia el tick en vivo. */
    suspend fun load() {
        val now = clock.nowMillis()
        val saved = repository.load()
        if (saved == null) {
            // Aún no hay nada criado: la UI pide una primera especie y un nombre.
            _roster.value = PetRosterEntity.empty
        } else {
            // Puesta al día offline (solo la mascota activa; las que descansan están congeladas).
            val roster = RosterRules.updateActiveStatus(RosterRules.migrate(saved)) { PetStatusRules.advanceTo(it, now) }
            repository.save(roster)
            _roster.value = roster
        }
        if (autoTick) startTicker()
    }

    // --- Intenciones del jugador ----------------------------------------------------

    /** Ofrece [food] a la mascota activa; `null` si ahora no se le puede dar de comer. */
    fun feed(food: Food): FeedResult? {
        val roster = _roster.value ?: return null
        val (next, result) = RosterRules.feedActive(advanceActive(roster, clock.nowMillis()), food, random)
        if (result != null) set(next, saveNow = result == FeedResult.grew)
        return result
    }
    fun toggleSleep() = applyToActive(PetStatusRules::toggleSleep, saveNow = true)
    fun clean() = applyToActive(PetStatusRules::clean)
    fun cuddle() = applyToActive(PetStatusRules::cuddle)

    /** Cría un polluelo llamado [name] en el bioma actual; devuelve por qué se rechazó el nombre. */
    fun hatch(name: String): PetNameError? {
        val roster = _roster.value ?: return null
        if (!RosterRules.canHatch(roster)) return null
        RosterRules.validateName(roster, name)?.let { return it }
        val now = clock.nowMillis()
        set(RosterRules.hatch(advanceActive(roster, now), name, now), saveNow = true)
        return null
    }

    /** Libera para siempre a la mascota [petId]. */
    fun release(petId: String) {
        val roster = _roster.value ?: return
        val now = clock.nowMillis()
        set(RosterRules.release(advanceActive(roster, now), petId, now), saveNow = true)
    }

    /** Viaja a [biome] (vacío si nunca has criado allí). */
    fun enterBiome(biome: Biome) {
        val roster = _roster.value ?: return
        val now = clock.nowMillis()
        set(RosterRules.enterBiome(advanceActive(roster, now), biome, now), saveNow = true)
    }

    /** Vuelve a una mascota ya criada; la actual empieza a descansar. */
    fun switchTo(petId: String) {
        val roster = _roster.value ?: return
        val now = clock.nowMillis()
        set(RosterRules.switchTo(advanceActive(roster, now), petId, now), saveNow = true)
    }

    /** Experiencia ganada por la mascota [petId] (p. ej. enemigos derrotados). */
    fun awardXp(petId: String, amount: Int) {
        val roster = _roster.value ?: return
        set(RosterRules.awardXp(roster, petId, amount))
    }

    /** La mascota [petId] se cansó luchando (p. ej. un disparo efectuado). */
    fun spendEnergy(petId: String, amount: Double) {
        val roster = _roster.value ?: return
        set(RosterRules.spendEnergy(roster, petId, amount))
    }

    /** Ayuda de depuración: borra la partida (todas las mascotas criadas). */
    fun reset() {
        scope.launch {
            repository.clear()
            _roster.value = PetRosterEntity.empty
        }
    }

    /** Escribe ahora cualquier cambio pendiente (app en pausa / ventana cerrándose). */
    fun flush() {
        pendingSave?.cancel()
        pendingSave = null
        val roster = _roster.value ?: return
        scope.launch { repository.save(roster) }
    }

    fun dispose() {
        ticker?.cancel()
        flush()
    }

    // --- Internos -------------------------------------------------------------------

    /** Primero actualiza el estado de la mascota activa hasta "ahora" y luego aplica [action]. */
    private fun applyToActive(action: (PetStatusEntity) -> PetStatusEntity, saveNow: Boolean = false) {
        val roster = _roster.value ?: return
        val now = clock.nowMillis()
        set(RosterRules.updateActiveStatus(roster) { action(PetStatusRules.advanceTo(it, now)) }, saveNow)
    }

    private fun advanceActive(roster: PetRosterEntity, now: Long) =
        RosterRules.updateActiveStatus(roster) { PetStatusRules.advanceTo(it, now) }

    private fun set(next: PetRosterEntity, saveNow: Boolean = false) {
        if (next == _roster.value) return
        _roster.value = next
        if (saveNow) flush() else scheduleSave()
    }

    /** Los ticks ponen al día a la mascota activa hasta "ahora" una vez por segundo. */
    fun tick() = applyToActive({ it })

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                delay(AppConfig.TICK_INTERVAL_MS)
                tick()
            }
        }
    }

    /** Guardado limitado: como máximo una escritura cada [AppConfig.SAVE_THROTTLE_MS]. */
    private fun scheduleSave() {
        if (pendingSave != null) return
        pendingSave = scope.launch {
            delay(AppConfig.SAVE_THROTTLE_MS)
            pendingSave = null
            _roster.value?.let { repository.save(it) }
        }
    }
}
