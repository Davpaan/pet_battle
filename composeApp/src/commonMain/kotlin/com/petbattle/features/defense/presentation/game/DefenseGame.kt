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
import com.petbattle.shared_features.pet_combat.domain.PetCombatBalance
import com.petbattle.shared_features.pet_combat.domain.PetCombatStats
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max

import kotlin.random.Random

/** Instantánea que dibuja el HUD (dirigida por frames, nunca se persiste). */
data class DefenseHudState(
    /** Mascotas ya en el mapa (cada mascota criada se puede colocar una vez). */
    val placedIds: Set<String> = emptySet(),
    /** Mascota que colocará el próximo toque en el mapa; null si ya están todas colocadas. */
    val selectedId: String? = null,
    /** Mascota ya colocada que se está recolocando (se dibuja semitransparente). */
    val editingId: String? = null,
    /** false = fase de preparación (colocar y mover); true = combate en marcha. */
    val started: Boolean = false,
    /** Se puede pulsar "Empezar": preparación con al menos una mascota colocada. */
    val canStart: Boolean = false,
    val seconds: Int = 0,
    val speedMultiplier: Double = 1.0,
    val kills: Int = 0,
    val lives: Int = 0,
    val gameOver: Boolean = false,
)

/**
 * Simulación de tower defense. Las coordenadas del mundo son píxeles del mapa.
 *
 * Flujo: primero hay una fase de preparación. El jugador elige una de sus mascotas y toca
 * un punto de hierba para colocarla (cada mascota se coloca una vez); tocando una ya
 * colocada la recoge para recolocarla con el siguiente toque ([tapAt]). El combate empieza
 * al pulsar "Empezar" ([start]): ya no se puede mover a nadie, los enemigos aparecen y
 * recorren las rutas, cada vez
 * más rápido cada [DefenseBalance.SPEED_STEP_SECONDS], mientras que la velocidad
 * de ataque, el daño y la precisión de cada mascota dependen de su estado. Cada
 * enemigo derrotado informa de la mascota que lo remató ([onEnemyKilledBy]); cada
 * disparo cansa a su mascota ([onPetFired]). Los enemigos que llegan a la salida
 * cuestan una vida; con 0 vidas termina la partida.
 */
class DefenseGame(
    val map: DefenseMapEntity,
    private val onEnemyKilledBy: (String) -> Unit,
    private val onPetFired: (String) -> Unit,
    private val random: Random = Random.Default,
) {
    private val routes = map.routes.map(::RoutePath)

    // --- Mascotas ----------------------------------------------------------------

    /** Mascotas criadas disponibles en esta partida, por id (últimas instantáneas). */
    private var pets: Map<String, PetEntity> = emptyMap()
    private var petOrder: List<String> = emptyList()
    var selectedId: String? = null
        private set

    /** Mascota colocada que se está recolocando; el próximo toque en hierba la mueve. */
    var editingId: String? = null
        private set

    /** Socialización de cada mascota (depende de su bioma; la necesita la confianza). */
    private var socialization: Map<String, Double> = emptyMap()

    /** Stats de combate de [pet] ahora mismo. */
    fun statsOf(pet: PetEntity) = PetCombatStatsRules.statsFor(pet, socialization[pet.id] ?: 0.0)

    /**
     * Últimas instantáneas del controlador (nivel y necesidades cambian al combatir) y la
     * socialización de cada una.
     */
    fun updatePets(value: List<PetEntity>, socialization: Map<String, Double> = emptyMap()) {
        this.socialization = socialization
        pets = value.associateBy { it.id }
        petOrder = value.map { it.id }
        defenders.values.forEach { d -> pets[d.pet.id]?.let { d.updatePet(it, this) } }
        if (!started && (selectedId == null || selectedId !in pets)) selectedId = firstUnplaced()
    }

    /**
     * Elige en la barra qué mascota coloca el próximo toque en el mapa. Si ya está colocada,
     * la recoge para recolocarla. Solo en la fase de preparación.
     */
    fun selectPet(petId: String) {
        if (started || petId !in pets) return
        if (petId in defenders) {
            editingId = if (editingId == petId) null else petId
        } else {
            selectedId = petId
            editingId = null
        }
    }

    private fun firstUnplaced() = petOrder.firstOrNull { it !in defenders }

    // --- Estado de la partida -------------------------------------------------------------

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

    /** Se ha pulsado "Empezar": se acabó la preparación. */
    var started = false
        private set

    val running get() = started && !gameOver

    val canStart get() = !started && defenders.isNotEmpty()

    /** Velocidad actual de cada enemigo en el mapa (px de mapa/s). */
    val enemySpeed get() = DefenseRules.enemySpeed(elapsed)

    /** El tinte verde de "aquí puedes colocarla": en preparación, con una mascota por colocar o recolocar. */
    val showPlacement get() = !started && !gameOver && (editingId != null || selectedId in pets)

    /** Celdas de hierba donde cabe la huella de una mascota (para el tinte de colocación). */
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
            editingId = editingId,
            started = started,
            canStart = canStart,
            seconds = floor(elapsed).toInt(),
            speedMultiplier = DefenseRules.speedMultiplier(elapsed),
            kills = kills,
            lives = lives,
            gameOver = gameOver,
        )

    /**
     * Un toque en el mapa durante la preparación: sobre una mascota colocada la recoge (o la
     * suelta si ya estaba recogida); en hierba mueve la recogida o coloca la seleccionada.
     */
    fun tapAt(p: Offset) {
        if (started || gameOver) return
        val touched = defenders.values.lastOrNull { it.contains(p) }
        when {
            touched != null -> editingId = if (editingId == touched.pet.id) null else touched.pet.id
            editingId != null -> moveEditingTo(p)
            else -> placePetAt(p)
        }
    }

    /** Coloca la mascota seleccionada centrada en [p] si ese punto es hierba libre. */
    fun placePetAt(p: Offset) {
        val pet = pets[selectedId] ?: return
        if (started || gameOver) return
        if (!isFreeGrass(p, exceptId = null)) {
            markers += InvalidMarker(p)
            return
        }
        defenders[pet.id] = Defender(pet, p)
        selectedId = firstUnplaced()
    }

    private fun moveEditingTo(p: Offset) {
        val d = defenders[editingId] ?: run { editingId = null; return }
        if (!isFreeGrass(p, exceptId = d.pet.id)) {
            markers += InvalidMarker(p)
            return
        }
        defenders[d.pet.id] = Defender(d.pet, p)
        editingId = null
    }

    private fun isFreeGrass(p: Offset, exceptId: String?): Boolean {
        val free = defenders.values.all { it.pet.id == exceptId || (it.foot - p).getDistance() >= DefenseBalance.PET_FOOTPRINT_RADIUS * 2 }
        return free && map.canPlace(MapPoint(p.x.toDouble(), p.y.toDouble()), DefenseBalance.PET_FOOTPRINT_RADIUS)
    }

    /** Termina la preparación y empieza el combate (hace falta al menos una mascota colocada). */
    fun start() {
        if (!canStart) return
        started = true
        editingId = null
        selectedId = null
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

    // --- Eventos de las entidades -------------------------------------------------------

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

    /** Limpia el mapa y vuelve a la preparación, con las mascotas donde estaban. */
    fun restart() {
        val placed = defenders.values.map { it.pet to it.foot }
        defenders.clear()
        placed.forEach { (pet, foot) -> defenders[pet.id] = Defender(pets[pet.id] ?: pet, foot) }
        enemies.clear()
        projectiles.clear()
        effects.clear()
        texts.clear()
        markers.clear()
        selectedId = firstUnplaced()
        editingId = null
        started = false
        elapsed = 0.0
        spawnIn = 0.5
        nextRoute = 0
        kills = 0
        lives = DefenseBalance.LIVES
        gameOver = false
    }

    internal fun nextRandom() = random.nextDouble()
}

/** Una de las mascotas del jugador en el mapa. */
class Defender(pet: PetEntity, val foot: Offset) {
    var pet: PetEntity = pet
        private set

    /** Centro inferior del sprite (los pies), algo por debajo del centro de la huella. */
    val position = foot + Offset(0f, (DefenseBalance.PET_FOOTPRINT_RADIUS * 0.6).toFloat())
    var facingRight = false
        private set
    private var cooldown = 0.0
    var age = 0.0
        private set
    var shotAge = 1.0
        private set

    /** Si [p] cae sobre el sprite (para recogerla con un toque). */
    fun contains(p: Offset) =
        p.x in (position.x - SIZE / 2)..(position.x + SIZE / 2) && p.y in (position.y - SIZE)..(position.y + 8f)

    /** De dónde salen los disparos (el pecho del pájaro). */
    val muzzle get() = position - Offset(0f, SIZE * 0.45f)

    /** Stats de combate con los que se dibuja su etiqueta (último cálculo). */
    var stats: PetCombatStats? = null
        private set

    val exhausted get() = stats?.exhausted == true

    fun updatePet(value: PetEntity, game: DefenseGame) {
        if (value.progression.level > pet.progression.level) {
            game.texts += FloatingText(
                "¡Nivel ${value.progression.level}!", position - Offset(0f, SIZE), life = 1.6, rise = 30.0,
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
        val current = game.statsOf(pet).also { stats = it }
        val interval = current.fireInterval ?: return // agotada: necesita dormir
        val target = pickTarget(game) ?: return
        cooldown = interval
        facingRight = target.position.x > position.x
        shotAge = 0.0
        game.petFired(
            this,
            Projectile(
                position = muzzle,
                target = target,
                element = pet.progression.element,
                skill = PetSkillsCatalog.basicAttackFor(pet.progression),
                ownerId = pet.id,
                speed = DefenseBalance.PROJECTILE_SPEED,
                damage = current.damage,
                hits = DefenseRules.rollHit(current, game.nextRandom()),
            ),
        )
    }

    /** Alcance de su especie (px de mapa). */
    val range get() = PetCombatBalance.range(pet.species)

    /** El enemigo más cercano a la salida, dentro de su alcance. */
    private fun pickTarget(game: DefenseGame): Enemy? {
        val range2 = range * range
        val from = muzzle
        return game.enemies
            .filter { it.alive && (it.position - from).getDistanceSquared() <= range2 }
            .maxByOrNull { it.progress }
    }

    companion object {
        /** Tamaño en el mapa: 2x el frame de 64px mantiene nítido el pixel art. */
        const val SIZE = 128f
    }
}

/** Un slime arrastrándose por una ruta; su estado de juego vive en su [entity]. */
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

    /** Reloj de animación: corre más rápido cuanto más rápido camina. */
    var animT = (spawnIndex * 3 % 8) * 0.1
        private set
    var effectT = 0.0
        private set

    val alive get() = !entity.isDefeated && !removed
    private var removed = false

    /** Cuánto ha avanzado por su ruta; las mascotas apuntan al más avanzado. */
    val progress get() = travelled / route.length

    /** Centro del cuerpo (a lo que apuntan los proyectiles). */
    val bodyCenter get() = position - Offset(0f, (RADIUS * 0.75).toFloat())

    fun update(dt: Double, game: DefenseGame) {
        if (flash > 0) flash -= dt
        if (gust > 0) gust -= dt
        effectT += dt
        if (!game.running || removed) return
        // Las quemaduras hacen tick y las ralentizaciones se disipan; una quemadura puede rematarlo.
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

    /** El disparo de una mascota ha acertado: su habilidad decide qué ocurre. */
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
        /** Radio aproximado del cuerpo en px de mapa (pruebas de impacto, choque de proyectiles). */
        const val RADIUS = 22.0
    }
}

/**
 * Ataque de una mascota: la habilidad de su elemento volando hacia un enemigo. Un
 * acierto se dirige al blanco y deja actuar el efecto de la habilidad; un fallo pasa
 * de largo y muestra "¡Fallo!".
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
        // El sprite se dibuja viajando hacia la derecha: se gira hacia su rumbo.
        angle = atan2(delta.y, delta.x)
        position += delta / dist * step
    }
}

/** Animación de sprite de una sola vez (impactos, muertes de slime). */
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
            AnimEffect(Kind.SlimeDeath, null, at, flip, frames = SlimeSheet.DEATH_FRAMES, stepTime = SlimeSheet.DEATH_STEP)
    }
}

/** Texto que flota hacia arriba y se desvanece ("¡Fallo!", "¡Nivel 3!"). */
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

/** Cruz roja efímera donde el jugador tocó un punto prohibido. */
class InvalidMarker(val position: Offset) {
    var age = 0.0

    companion object {
        const val LIFE = 0.6
    }
}

private fun MapPoint.toOffset() = Offset(x.toFloat(), y.toFloat())
