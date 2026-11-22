package com.petbattle.shared_features.pet_status.application

import com.petbattle.core.config.AppConfig
import com.petbattle.core.time.Clock
import com.petbattle.shared_features.pet_species.domain.PetSpecies
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

/**
 * Orchestrates every raised pet: loads the roster, applies offline decay to
 * the active pet, runs its live tick, translates player intents into
 * [PetStatusRules] / [RosterRules] calls and auto-saves every change.
 *
 * [roster] is `null` while loading.
 */
class PetController(
    private val repository: PetRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val autoTick: Boolean = true,
) {
    private val _roster = MutableStateFlow<PetRosterEntity?>(null)
    val roster: StateFlow<PetRosterEntity?> = _roster.asStateFlow()

    private var ticker: Job? = null
    private var pendingSave: Job? = null

    /** Loads the save (or an empty roster) and starts the live tick. */
    suspend fun load() {
        val now = clock.nowMillis()
        val saved = repository.load()
        if (saved == null) {
            // Nothing raised yet: the UI asks for a first species and a name.
            _roster.value = PetRosterEntity.empty
        } else {
            // Offline catch-up (active pet only; resting pets are frozen).
            val roster = RosterRules.updateActiveStatus(saved) { PetStatusRules.advanceTo(it, now) }
            repository.save(roster)
            _roster.value = roster
        }
        if (autoTick) startTicker()
    }

    // --- Player intents ---------------------------------------------------------

    fun feed() = applyToActive(PetStatusRules::feed)
    fun toggleSleep() = applyToActive(PetStatusRules::toggleSleep, saveNow = true)
    fun clean() = applyToActive(PetStatusRules::clean)
    fun cuddle() = applyToActive(PetStatusRules::cuddle)

    /** Raises a new level-0 pet named [name]; returns why the name was rejected. */
    fun adopt(species: PetSpecies, name: String): PetNameError? {
        val roster = _roster.value ?: return null
        if (!RosterRules.canAdopt(roster, species)) return null
        RosterRules.validateName(roster, name)?.let { return it }
        val now = clock.nowMillis()
        set(RosterRules.adopt(advanceActive(roster, now), species, name, now), saveNow = true)
        return null
    }

    /** Goes back to an already raised pet; the current one starts resting. */
    fun switchTo(petId: String) {
        val roster = _roster.value ?: return
        val now = clock.nowMillis()
        set(RosterRules.switchTo(advanceActive(roster, now), petId, now), saveNow = true)
    }

    /** Experience earned by pet [petId] (e.g. enemies it defeated). */
    fun awardXp(petId: String, amount: Int) {
        val roster = _roster.value ?: return
        set(RosterRules.awardXp(roster, petId, amount))
    }

    /** Pet [petId] got tired by fighting (e.g. one shot fired). */
    fun spendEnergy(petId: String, amount: Double) {
        val roster = _roster.value ?: return
        set(RosterRules.spendEnergy(roster, petId, amount))
    }

    /** Debug helper: wipe the save (every raised pet). */
    fun reset() {
        scope.launch {
            repository.clear()
            _roster.value = PetRosterEntity.empty
        }
    }

    /** Writes any pending change now (app paused / window closing). */
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

    // --- Internals ----------------------------------------------------------------

    /** Brings the active pet's status up to "now" first, then applies [action]. */
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

    /** Ticks catch the active pet up to "now" once per second. */
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

    /** Throttled save: at most one write every [AppConfig.SAVE_THROTTLE_MS]. */
    private fun scheduleSave() {
        if (pendingSave != null) return
        pendingSave = scope.launch {
            delay(AppConfig.SAVE_THROTTLE_MS)
            pendingSave = null
            _roster.value?.let { repository.save(it) }
        }
    }
}
