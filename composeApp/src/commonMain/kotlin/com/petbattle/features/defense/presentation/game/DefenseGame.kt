package com.petbattle.features.defense.presentation.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.petbattle.features.defense.domain.CombatRules
import com.petbattle.features.defense.domain.DefenseBalance
import com.petbattle.features.defense.domain.DefenseRules
import com.petbattle.features.defense.domain.RoutePath
import com.petbattle.features.defense.domain.entities.DefenseMapEntity
import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.features.defense.domain.entities.MapPoint
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max

import kotlin.random.Random

/** Snapshot the HUD renders (frame-driven, never persisted). */
data class DefenseHudState(
    /** Pets already on the map (each raised pet can be placed once). */
    val placedIds: Set<String> = emptySet(),
    /** Pet the next tap on the map places; null when all are placed. */
    val selectedId: String? = null,
    val seconds: Int = 0,
    val speedMultiplier: Double = 1.0,
    val kills: Int = 0,
    val lives: Int = 0,
    val gameOver: Boolean = false,
)

/**
 * Tower-defense simulation. World coordinates are map pixels.
 *
 * Flow: the player picks one of their raised pets and taps a grass spot to
 * place it; every raised pet can be placed once. The run starts with the
 * first placement — enemies spawn and walk the routes, getting faster every
 * [DefenseBalance.SPEED_STEP_SECONDS], while each pet's attack speed, damage
 * and accuracy follow its status. Each defeated enemy reports the pet that
 * finished it ([onEnemyKilledBy]); every shot tires its pet ([onPetFired]).
 * Enemies reaching the exit cost a life; at 0 lives the run ends.
 */
class DefenseGame(
    val map: DefenseMapEntity,
    private val onEnemyKilledBy: (String) -> Unit,
    private val onPetFired: (String) -> Unit,
    private val random: Random = Random.Default,
) {
    private val routes = map.routes.map(::RoutePath)

    // --- Pets ----------------------------------------------------------------

    /** Raised pets available for this run, by id (latest snapshots). */
    private var pets: Map<String, PetEntity> = emptyMap()
    private var petOrder: List<String> = emptyList()
    var selectedId: String? = null
        private set

    /** Latest snapshots from the controller (levels/energy change as they fight). */
    fun updatePets(value: List<PetEntity>) {
        pets = value.associateBy { it.id }
        petOrder = value.map { it.id }
        defenders.values.forEach { d -> pets[d.pet.id]?.let { d.updatePet(it, this) } }
        if (selectedId == null || selectedId !in pets) selectedId = firstUnplaced()
    }

    /** Chooses which pet the next tap on the map places. */
    fun selectPet(petId: String) {
        if (petId in pets && petId !in defenders) selectedId = petId
    }

    private fun firstUnplaced() = petOrder.firstOrNull { it !in defenders }

    // --- Run state -------------------------------------------------------------

    val defenders = LinkedHashMap<String, Defender>()
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<AnimEffect>()
    val texts = mutableListOf<FloatingText>()
    val markers = mutableListOf<InvalidMarker>()
    var time = 0.0
        private set
    private var elapsed = 0.0
    private var spawnIn = 0.5
    private var nextRoute = 0
    private var kills = 0
    private var lives = DefenseBalance.LIVES
    var gameOver = false
        private set

    val running get() = defenders.isNotEmpty() && !gameOver

    /** Current speed of every enemy on the map (map px/s). */
    val enemySpeed get() = DefenseRules.enemySpeed(elapsed)

    /** The green "you can stand here" tint shows while a pet waits to be placed. */
    val showPlacement get() = !gameOver && selectedId in pets

    /** Grass cells where a pet's footprint fits (for the placement tint). */
    val placeableCells: List<Rect> by lazy {
        val s = map.cellSize.toFloat()
        buildList {
            for (r in 0 until map.rows) for (c in 0 until map.columns) {
                val centre = MapPoint((c + 0.5) * s, (r + 0.5) * s)
                if (map.canPlace(centre, DefenseBalance.PET_FOOTPRINT_RADIUS)) {
                    add(Rect(c * s, r * s, (c + 1) * s, (r + 1) * s))
                }
            }
        }
    }

    val hud: DefenseHudState
        get() = DefenseHudState(
            placedIds = defenders.keys.toSet(),
            selectedId = selectedId,
            seconds = floor(elapsed).toInt(),
            speedMultiplier = DefenseRules.speedMultiplier(elapsed),
            kills = kills,
            lives = lives,
            gameOver = gameOver,
        )

    /** Places the selected pet centred on [p] if that spot is free grass. */
    fun placePetAt(p: Offset) {
        val pet = pets[selectedId] ?: return
        if (gameOver) return
        val free = defenders.values.all { (it.foot - p).getDistance() >= DefenseBalance.PET_FOOTPRINT_RADIUS * 2 }
        if (!free || !map.canPlace(MapPoint(p.x.toDouble(), p.y.toDouble()), DefenseBalance.PET_FOOTPRINT_RADIUS)) {
            markers += InvalidMarker(p)
            return
        }
        defenders[pet.id] = Defender(pet, p)
        selectedId = firstUnplaced()
    }

    fun update(dt: Double) {
        time += dt
        defenders.values.forEach { it.update(dt, this) }
        enemies.toList().forEach { it.update(dt, this) }
        projectiles.toList().forEach { it.update(dt, this) }
        effects.forEach { it.age += dt }
        effects.removeAll { it.done }
        texts.forEach { it.age += dt }
        texts.removeAll { it.age >= it.life }
        markers.forEach { it.age += dt }
        markers.removeAll { it.age >= InvalidMarker.LIFE }
        if (!running) return
        elapsed += dt
        spawnIn -= dt
        if (spawnIn <= 0) {
            spawnIn += DefenseBalance.SPAWN_INTERVAL
            val route = routes[nextRoute]
            nextRoute = (nextRoute + 1) % routes.size
            enemies += Enemy(route, EnemyEntity.spawn(DefenseRules.enemyLevel(elapsed)), enemies.size)
        }
    }

    // --- Events from entities -------------------------------------------------------

    internal fun enemyKilled(enemy: Enemy) {
        enemies.remove(enemy)
        effects += AnimEffect.slimeDeath(enemy.position, enemy.facingRight)
        kills++
        enemy.entity.lastHitBy?.let(onEnemyKilledBy)
    }

    internal fun enemyEscaped(enemy: Enemy) {
        enemies.remove(enemy)
        if (gameOver) return
        lives--
        if (lives <= 0) {
            lives = 0
            gameOver = true
        }
    }

    internal fun petFired(defender: Defender, projectile: Projectile) {
        projectiles += projectile
        onPetFired(defender.pet.id)
    }

    /** Wipes the map and goes back to the placement step. */
    fun restart() {
        defenders.clear()
        enemies.clear()
        projectiles.clear()
        effects.clear()
        texts.clear()
        markers.clear()
        selectedId = firstUnplaced()
        elapsed = 0.0
        spawnIn = 0.5
        nextRoute = 0
        kills = 0
        lives = DefenseBalance.LIVES
        gameOver = false
    }

    internal fun nextRandom() = random.nextDouble()
}

/** One of the player's pets standing on the map. */
class Defender(pet: PetEntity, val foot: Offset) {
    var pet: PetEntity = pet
        private set

    /** Bottom-centre of the sprite (feet), slightly below the footprint centre. */
    val position = foot + Offset(0f, (DefenseBalance.PET_FOOTPRINT_RADIUS * 0.6).toFloat())
    var facingRight = false
        private set
    private var cooldown = 0.0
    var age = 0.0
        private set
    var shotAge = 1.0
        private set

    /** Where shots leave from (the bird's chest). */
    val muzzle get() = position - Offset(0f, SIZE * 0.45f)

    val exhausted get() = DefenseRules.fireInterval(pet) == null

    fun updatePet(value: PetEntity, game: DefenseGame) {
        if (value.skills.level > pet.skills.level) {
            game.texts += FloatingText(
                "¡Nivel ${value.skills.level}!", position - Offset(0f, SIZE), life = 1.6, rise = 30.0,
                color = 0xFFFFD54F, sizePx = 26f,
            )
        }
        pet = value
    }

    fun update(dt: Double, game: DefenseGame) {
        age += dt
        shotAge += dt
        if (!game.running) return
        cooldown -= dt
        if (cooldown > 0) return
        val interval = DefenseRules.fireInterval(pet) ?: return // exhausted: needs to sleep
        val target = pickTarget(game) ?: return
        cooldown = interval
        facingRight = target.position.x > position.x
        shotAge = 0.0
        game.petFired(
            this,
            Projectile(
                position = muzzle,
                target = target,
                element = pet.skills.element,
                skill = PetSkillsCatalog.basicAttackFor(pet.skills),
                ownerId = pet.id,
                speed = DefenseBalance.PROJECTILE_SPEED,
                damage = DefenseRules.shotDamage(pet),
                hits = DefenseRules.rollHit(pet, game.nextRandom()),
            ),
        )
    }

    /** The enemy closest to the exit, within range. */
    private fun pickTarget(game: DefenseGame): Enemy? {
        val range2 = DefenseBalance.PET_RANGE * DefenseBalance.PET_RANGE
        val from = muzzle
        return game.enemies
            .filter { it.alive && (it.position - from).getDistanceSquared() <= range2 }
            .maxByOrNull { it.progress }
    }

    companion object {
        /** On-map size: 2x the 64px frame keeps the pixel art crisp. */
        const val SIZE = 128f
    }
}

/** A slime crawling along a route; game state lives in its [entity]. */
class Enemy(val route: RoutePath, entity: EnemyEntity, spawnIndex: Int) {
    var entity: EnemyEntity = entity
        private set
    private var travelled = 0.0
    var position: Offset = route.positionAt(0.0).toOffset()
        private set
    var facingRight = false
        private set
    var flash = 0.0
        private set
    var gust = 0.0
        private set

    /** Animation clock: runs faster the faster it walks. */
    var animT = (spawnIndex * 3 % 8) * 0.1
        private set
    var effectT = 0.0
        private set

    val alive get() = !entity.isDefeated && !removed
    private var removed = false

    /** How far along its route it is; pets target the most advanced one. */
    val progress get() = travelled / route.length

    /** Middle of the body (what projectiles aim at). */
    val bodyCenter get() = position - Offset(0f, (RADIUS * 0.75).toFloat())

    fun update(dt: Double, game: DefenseGame) {
        if (flash > 0) flash -= dt
        if (gust > 0) gust -= dt
        effectT += dt
        if (!game.running || removed) return
        // Burns tick and slows wear off; a burn can finish it.
        entity = entity.tick(dt)
        if (entity.isDefeated) {
            die(game)
            return
        }
        val speed = game.enemySpeed * entity.speedFactor
        animT += dt * speed / DefenseBalance.BASE_ENEMY_SPEED
        travelled += speed * dt
        if (travelled >= route.length) {
            removed = true
            game.enemyEscaped(this)
            return
        }
        moveTo(route.positionAt(travelled))
    }

    private fun moveTo(p: MapPoint) {
        if (kotlin.math.abs(p.x - position.x) > 0.01) facingRight = p.x > position.x
        position = p.toOffset()
    }

    /** A pet's shot landed: its skill decides what happens. */
    fun takeHit(damage: Double, skill: PetSkillEntity, byPetId: String, game: DefenseGame) {
        if (!alive) return
        val outcome = CombatRules.applyHit(entity, damage, skill.effect, byPetId)
        entity = outcome.enemy
        flash = 0.12
        if (outcome.knockback > 0) {
            travelled = max(0.0, travelled - outcome.knockback)
            moveTo(route.positionAt(travelled))
            gust = 0.35
        }
        if (entity.isDefeated) die(game)
    }

    private fun die(game: DefenseGame) {
        if (removed) return
        removed = true
        game.enemyKilled(this)
    }

    companion object {
        /** Rough body radius in map px (hit tests, projectile impact). */
        const val RADIUS = 22.0
    }
}

/**
 * A pet's attack: the skill of its element flying at an enemy. A hit homes
 * in and lets the skill's effect act; a miss flies past and shows "¡Fallo!".
 */
class Projectile(
    position: Offset,
    val target: Enemy,
    val element: PetElement,
    val skill: PetSkillEntity,
    val ownerId: String,
    val speed: Double,
    val damage: Double,
    val hits: Boolean,
) {
    var position = position
        private set
    var angle = 0f
        private set
    var age = 0.0
        private set
    private var lastTarget = target.bodyCenter
    private val missOffset = Offset(
        ((if (position.x < target.position.x) 1 else -1) * Enemy.RADIUS * 1.6).toFloat(),
        (-Enemy.RADIUS * 0.9).toFloat(),
    )

    fun update(dt: Double, game: DefenseGame) {
        age += dt
        if (target.alive) {
            lastTarget = target.bodyCenter
            if (!hits) lastTarget += missOffset
        }
        val delta = lastTarget - position
        val dist = delta.getDistance()
        val step = (speed * dt).toFloat()
        val hitDistance = if (target.alive && hits) (Enemy.RADIUS * 0.7).toFloat() else step
        if (dist <= hitDistance || dist <= step) {
            if (!hits) {
                game.texts += FloatingText("¡Fallo!", lastTarget, life = 0.7, rise = 25.0, color = 0xFFFFFFFF, sizePx = 16f)
            } else {
                game.effects += AnimEffect.skillImpact(element, lastTarget)
                if (target.alive) target.takeHit(damage, skill, ownerId, game)
            }
            game.projectiles.remove(this)
            return
        }
        // The sprite is drawn travelling right: turn it to its heading.
        angle = atan2(delta.y, delta.x)
        position += delta / dist * step
    }
}

/** One-shot sprite animation (impacts, slime deaths). */
class AnimEffect(
    val kind: Kind,
    val element: PetElement?,
    val position: Offset,
    val flipX: Boolean,
    val frames: Int,
    val stepTime: Double,
) {
    enum class Kind { SkillImpact, SlimeDeath }

    var age = 0.0
    val frame get() = minOf(frames - 1, floor(age / stepTime).toInt())
    val done get() = age >= frames * stepTime

    companion object {
        fun skillImpact(element: PetElement, at: Offset) =
            AnimEffect(Kind.SkillImpact, element, at, false, frames = 4, stepTime = 0.06)

        fun slimeDeath(at: Offset, flip: Boolean) =
            AnimEffect(Kind.SlimeDeath, null, at, flip, frames = 6, stepTime = 0.07)
    }
}

/** Text floating up and fading ("¡Fallo!", "¡Nivel 3!"). */
class FloatingText(
    val text: String,
    val origin: Offset,
    val life: Double,
    val rise: Double,
    val color: Long,
    val sizePx: Float,
) {
    var age = 0.0
    val position get() = origin - Offset(0f, (rise * age).toFloat())
}

/** Short-lived red cross where the player tapped a forbidden spot. */
class InvalidMarker(val position: Offset) {
    var age = 0.0

    companion object {
        const val LIFE = 0.6
    }
}

private fun MapPoint.toOffset() = Offset(x.toFloat(), y.toFloat())
