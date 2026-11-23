package com.petbattle.features.defense.domain.entities

import com.petbattle.shared_features.pet_skills.domain.PetProgression
import kotlin.math.min

/** Un efecto de daño con el tiempo que quema a un enemigo. */
data class EnemyBurn(
    val damagePerSecond: Double,
    val secondsLeft: Double,
    /** Mascota que lo prendió fuego: se le acredita si la quemadura remata al enemigo. */
    val byPetId: String,
)

/** Tipo de enemigo: cambia cómo se ve y se mueve, no sus reglas de combate. */
enum class EnemyKind {
    /** Se arrastra por el camino. */
    worm,

    /** Vuela sobre el camino; aparece cuando los enemigos llegan a nivel alto. */
    butterfly,
}

/**
 * Un enemigo de una partida de defensa: su tipo, su nivel, sus puntos de vida restantes y
 * los efectos que tiene activos (quemaduras, ralentización). Inmutable: cada cambio
 * devuelve un enemigo nuevo.
 */
data class EnemyEntity(
    val level: Int,
    val hp: Double,
    val kind: EnemyKind = EnemyKind.worm,
    val burns: List<EnemyBurn> = emptyList(),
    /** Multiplicador de velocidad mientras está ralentizado (1 = velocidad normal). */
    val slowFactor: Double = 1.0,
    val slowSecondsLeft: Double = 0.0,
    /** Mascota cuyo daño impactó en último lugar: la que se lleva el mérito de la baja. */
    val lastHitBy: String? = null,
) {
    val maxHp get() = maxHpFor(level)
    val isDefeated get() = hp <= EPSILON
    val isBurning get() = burns.isNotEmpty()
    val isSlowed get() = slowSecondsLeft > 0

    /** Multiplicador a aplicar ahora a la velocidad de marcha. */
    val speedFactor get() = if (isSlowed) slowFactor else 1.0

    /** Salud restante, 0..1. */
    val healthFraction get() = (hp / maxHp).coerceIn(0.0, 1.0)

    /** Daño instantáneo. */
    fun hit(damage: Double, byPetId: String? = null) =
        if (damage <= 0) this else copy(hp = hp - damage, lastHitBy = byPetId ?: lastHitBy)

    /** Lo prende fuego: [totalDamage] repartido uniformemente durante [seconds]. Las quemaduras se acumulan. */
    fun burn(totalDamage: Double, seconds: Double, byPetId: String): EnemyEntity {
        if (totalDamage <= 0 || seconds <= 0) return this
        return copy(burns = burns + EnemyBurn(totalDamage / seconds, seconds, byPetId))
    }

    /** Las ralentizaciones no se acumulan: gana el factor más fuerte y el tiempo más largo. */
    fun slow(factor: Double, seconds: Double): EnemyEntity {
        if (seconds <= 0 || factor >= 1) return this
        return copy(
            slowFactor = if (isSlowed && slowFactor < factor) slowFactor else factor,
            slowSecondsLeft = maxOf(slowSecondsLeft, seconds),
        )
    }

    /** Deja pasar [dt] segundos: las quemaduras hacen su daño y los efectos se disipan. */
    fun tick(dt: Double): EnemyEntity {
        if (dt <= 0 || (!isBurning && !isSlowed)) return this
        var damage = 0.0
        var burner: String? = null
        val remaining = mutableListOf<EnemyBurn>()
        for (b in burns) {
            val t = min(dt, b.secondsLeft)
            damage += b.damagePerSecond * t
            burner = b.byPetId
            if (b.secondsLeft - t > EPSILON) remaining += b.copy(secondsLeft = b.secondsLeft - t)
        }
        val slowLeft = slowSecondsLeft - dt
        return copy(
            hp = hp - damage,
            burns = remaining,
            slowSecondsLeft = if (slowLeft > 0) slowLeft else 0.0,
            slowFactor = if (slowLeft > 0) slowFactor else 1.0,
            lastHitBy = burner ?: lastHitBy,
        )
    }

    companion object {
        const val MIN_LEVEL = 1

        /** Puntos de vida por nivel de enemigo: nivel 1 = 1.2, nivel 2 = 2.4... */
        const val HP_PER_LEVEL = 1.2

        /** Cada enemigo derrotado vale esta cantidad de XP para quien lo mata. */
        const val XP_REWARD = PetProgression.XP_PER_ENEMY

        // 1.2 * n no es exacto en coma flotante.
        private const val EPSILON = 1e-9

        fun maxHpFor(level: Int) = HP_PER_LEVEL * level

        /** Un enemigo nuevo de nivel [level] (1 es el más débil) con la salud completa. */
        fun spawn(level: Int, kind: EnemyKind = EnemyKind.worm): EnemyEntity {
            val l = maxOf(level, MIN_LEVEL)
            return EnemyEntity(level = l, hp = maxHpFor(l), kind = kind)
        }
    }
}
