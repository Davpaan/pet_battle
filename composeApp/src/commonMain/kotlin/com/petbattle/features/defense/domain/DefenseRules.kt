package com.petbattle.features.defense.domain

import com.petbattle.shared_features.pet_status.domain.PetCombatEffects
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.floor

/** Tunable numbers for the tower-defense mode. Map pixels and seconds. */
object DefenseBalance {
    // Enemies
    const val BASE_ENEMY_SPEED = 36.0
    const val SPEED_STEP_SECONDS = 10.0

    /** Each step adds this fraction of the base speed (linear growth). */
    const val SPEED_STEP_INCREASE = 0.2
    const val SPAWN_INTERVAL = 2.2

    /** Enemies spawn at level 1 and one level higher every this often. */
    const val ENEMY_LEVEL_STEP_SECONDS = 20.0
    const val LIVES = 10

    // Pets. Range is constant; fire rate, damage and accuracy are scaled by
    // the pet's status (see PetCombatEffects).
    /** Seconds between shots of a fully rested pet. */
    const val PET_FIRE_INTERVAL = 0.7
    const val PET_RANGE = 300.0
    const val PROJECTILE_SPEED = 650.0

    /** Damage per pet level; equal to enemy HP per level (N one-shots N). */
    const val DAMAGE_PER_PET_LEVEL = 1.2

    /** Damage of a level-0 pet. */
    const val LEVEL_ZERO_DAMAGE = 1.0

    /** Ground footprint that must lie entirely on grass. */
    const val PET_FOOTPRINT_RADIUS = 28.0
}

/** Pure rules of a defense run, kept out of the UI so they're unit-testable. */
object DefenseRules {
    /** 0 during the first [DefenseBalance.SPEED_STEP_SECONDS], then +1 per step. */
    fun speedLevel(elapsed: Double) =
        if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.SPEED_STEP_SECONDS).toInt()

    /** Current enemy speed (map px/s). Applies to every enemy on the map. */
    fun enemySpeed(elapsed: Double) =
        DefenseBalance.BASE_ENEMY_SPEED * (1 + DefenseBalance.SPEED_STEP_INCREASE * speedLevel(elapsed))

    /** Level of the enemies spawning at [elapsed]: 1, then +1 every step. */
    fun enemyLevel(elapsed: Double) =
        1 + if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.ENEMY_LEVEL_STEP_SECONDS).toInt()

    /** Base damage of a pet of [petLevel], before its status is applied. */
    fun petDamage(petLevel: Int) =
        if (petLevel <= 0) DefenseBalance.LEVEL_ZERO_DAMAGE else DefenseBalance.DAMAGE_PER_PET_LEVEL * petLevel

    /** Speed multiplier shown to the player (1.0, 1.2, 1.4...). */
    fun speedMultiplier(elapsed: Double) = enemySpeed(elapsed) / DefenseBalance.BASE_ENEMY_SPEED

    /** Damage one hit of [pet] deals: level-based damage scaled by hunger. */
    fun shotDamage(pet: PetEntity) = petDamage(pet.skills.level) * PetCombatEffects.damage(pet.status)

    /** Seconds until [pet] can shoot again; null when it's too tired. */
    fun fireInterval(pet: PetEntity) =
        PetCombatEffects.fireInterval(pet.status, DefenseBalance.PET_FIRE_INTERVAL)

    /** Whether a shot hits, given a uniform random [roll] in 0..1. */
    fun rollHit(pet: PetEntity, roll: Double) = roll < PetCombatEffects.hitChance(pet.status)
}
