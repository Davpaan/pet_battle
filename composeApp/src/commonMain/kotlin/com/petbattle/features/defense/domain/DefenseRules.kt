package com.petbattle.features.defense.domain

import com.petbattle.shared_features.pet_status.domain.PetCombatEffects
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.floor

/** Números ajustables del modo tower defense. Píxeles de mapa y segundos. */
object DefenseBalance {
    // Enemigos
    const val BASE_ENEMY_SPEED = 36.0
    const val SPEED_STEP_SECONDS = 10.0

    /** Cada paso suma esta fracción de la velocidad base (crecimiento lineal). */
    const val SPEED_STEP_INCREASE = 0.2
    const val SPAWN_INTERVAL = 2.2

    /** Los enemigos aparecen en nivel 1 y suben un nivel cada este tiempo. */
    const val ENEMY_LEVEL_STEP_SECONDS = 20.0
    const val LIVES = 10

    // Mascotas. El alcance es constante; la cadencia, el daño y la precisión se
    // escalan según el estado de la mascota (ver PetCombatEffects).
    /** Segundos entre disparos de una mascota completamente descansada. */
    const val PET_FIRE_INTERVAL = 0.7
    const val PET_RANGE = 300.0
    const val PROJECTILE_SPEED = 650.0

    /** Daño por nivel de mascota; igual a los PV del enemigo por nivel (N mata a N de un disparo). */
    const val DAMAGE_PER_PET_LEVEL = 1.2

    /** Daño de una mascota de nivel 0. */
    const val LEVEL_ZERO_DAMAGE = 1.0

    /** Huella en el suelo que debe estar íntegramente sobre hierba. */
    const val PET_FOOTPRINT_RADIUS = 28.0
}

/** Reglas puras de una partida de defensa, fuera de la UI para poder testearlas. */
object DefenseRules {
    /** 0 durante los primeros [DefenseBalance.SPEED_STEP_SECONDS], luego +1 por paso. */
    fun speedLevel(elapsed: Double) =
        if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.SPEED_STEP_SECONDS).toInt()

    /** Velocidad actual del enemigo (px de mapa/s). Se aplica a todos los enemigos del mapa. */
    fun enemySpeed(elapsed: Double) =
        DefenseBalance.BASE_ENEMY_SPEED * (1 + DefenseBalance.SPEED_STEP_INCREASE * speedLevel(elapsed))

    /** Nivel de los enemigos que aparecen en [elapsed]: 1, luego +1 por paso. */
    fun enemyLevel(elapsed: Double) =
        1 + if (elapsed <= 0) 0 else floor(elapsed / DefenseBalance.ENEMY_LEVEL_STEP_SECONDS).toInt()

    /** Daño base de una mascota de nivel [petLevel], antes de aplicar su estado. */
    fun petDamage(petLevel: Int) =
        if (petLevel <= 0) DefenseBalance.LEVEL_ZERO_DAMAGE else DefenseBalance.DAMAGE_PER_PET_LEVEL * petLevel

    /** Multiplicador de velocidad mostrado al jugador (1.0, 1.2, 1.4...). */
    fun speedMultiplier(elapsed: Double) = enemySpeed(elapsed) / DefenseBalance.BASE_ENEMY_SPEED

    /** Daño de un golpe de [pet]: daño según nivel escalado por el hambre. */
    fun shotDamage(pet: PetEntity) = petDamage(pet.skills.level) * PetCombatEffects.damage(pet.status)

    /** Segundos hasta que [pet] pueda volver a disparar; null si está demasiado cansada. */
    fun fireInterval(pet: PetEntity) =
        PetCombatEffects.fireInterval(pet.status, DefenseBalance.PET_FIRE_INTERVAL)

    /** Si un disparo acierta, dado un [roll] aleatorio uniforme en 0..1. */
    fun rollHit(pet: PetEntity, roll: Double) = roll < PetCombatEffects.hitChance(pet.status)
}
