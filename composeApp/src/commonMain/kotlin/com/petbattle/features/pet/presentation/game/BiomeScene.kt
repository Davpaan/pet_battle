package com.petbattle.features.pet.presentation.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.core.time.DayPhase
import com.petbattle.resources.pet_egg
import com.petbattle.resources.Res
import com.petbattle.core.time.systemNowMillis
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.background
import com.petbattle.shared_features.biomes.presentation.backgroundLayers
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_species.presentation.spriteScale
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.wellbeing
import org.jetbrains.compose.resources.imageResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Altura del suelo (fracción de la altura del lienzo) sobre la que se posan los pájaros. */
private const val GROUND = 0.93f

/**
 * El bioma con todas sus mascotas a la vez. Cada una descansa un rato y luego vuela
 * (batiendo las alas) hasta otro punto al azar del suelo. En vuelo pueden cruzarse, pero
 * al posarse no pueden quedar una encima de otra: si el sitio está ocupado, sigue de largo
 * o se da la vuelta. Tocar una mascota la selecciona ([onPetTapped]). La mascota activa
 * ([activeId]) se dibuja delante y con su nombre resaltado. Según el momento del día
 * ([dayPhase], hora local): por la mañana la imagen tal cual; por la tarde un filtro de
 * atardecer sutil; de noche la pantalla se oscurece y todas descansan (pose de dormir, sin
 * volar), aunque se puede seguir tocándolas. Fuera de la noche solo descansa la que se haya
 * acostado, sin cambiar la pantalla.
 * [walkStartInset] y [walkEndInset] dejan libre a cada lado el espacio que ocupen los botones.
 */
@Composable
fun BiomeScene(
    biome: Biome,
    pets: List<PetEntity>,
    activeId: String?,
    onPetTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
    dayPhase: DayPhase = DayPhase.morning,
    walkStartInset: Dp = 0.dp,
    walkEndInset: Dp = 0.dp,
) {
    val backdrop = imageResource(biome.background)
    val layers = biome.backgroundLayers?.let {
        BackdropLayers(imageResource(it.sky), imageResource(it.back), imageResource(it.terrain))
    }
    val sheets = PetSpecies.entries.associateWith { imageResource(it.sheet) }
    val eggImage = imageResource(Res.drawable.pet_egg)
    val state = remember(biome) { BiomeSceneState() }
    val currentPets by rememberUpdatedState(pets)
    val currentActive by rememberUpdatedState(activeId)
    val currentPhase by rememberUpdatedState(dayPhase)
    val onTap by rememberUpdatedState(onPetTapped)
    val textMeasurer = rememberTextMeasurer()
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                state.update(min(dt, 0.1), currentPets, currentPhase)
                frame++
            }
        }
    }

    Canvas(
        modifier.pointerInput(state) {
            detectTapGestures { pos ->
                val id = state.petAt(pos, currentActive) ?: return@detectTapGestures
                // Los huevos no se pueden seleccionar ni mimar.
                if (currentPets.firstOrNull { it.id == id }?.isEgg == true) return@detectTapGestures
                state.onTapped(id, currentPets)
                onTap(id)
            }
        },
    ) {
        frame // se lee para que cada frame se redibuje
        if (layers != null) {
            drawLayeredBackdrop(layers, state.time, state.look)
        } else {
            drawBiomeBackdrop(backdrop, state.look)
        }
        state.layout(size, walkStartInset.toPx(), walkEndInset.toPx())
        // La activa al final: queda delante de las demás.
        val ordered = pets.sortedBy { it.id == activeId }
        val now = systemNowMillis()
        for (pet in ordered) {
            val actor = state.actors[pet.id] ?: continue
            if (pet.isEgg) drawEgg(state, actor, pet, eggImage, now)
            else drawActor(state, actor, pet, sheets.getValue(pet.species))
        }
        for (pet in ordered) {
            val actor = state.actors[pet.id] ?: continue
            drawHearts(actor)
            drawZzz(textMeasurer, state, actor)
            val label = if (pet.isEgg) "Huevo · ${timeLeft(pet.hatchesAt!! - now)}" else pet.name
            drawName(textMeasurer, actor, label, active = pet.id == activeId)
        }
    }
}

/** Una mascota en escena: posición, vuelo y animaciones (nunca se persiste). */
private class Actor(var x: Double, val phase: Double) {
    var target = x
    var moving = false
    var lift = 0.0 // 0 = posada, 1 = altura de vuelo
    var facingRight = Random.nextBoolean()
    var restFor = 1.0 + Random.nextDouble() * 4
    var nextTurnIn = 2.0 + Random.nextDouble() * 4
    var sleepT = 0.0
    var saturation = 1.0
    var reactT = -1.0 // segundos de reacción al toque, <0 = sin reaccionar
    var bounceT = -1.0
    val hearts = mutableListOf<Heart>()
    var rect = Rect.Zero

    class Heart(var age: Double = 0.0)

    /** Squash & stretch: (1.18, .82) -> (.9, 1.12) -> (1, 1) con overshoot. */
    fun bounceScale(): Pair<Float, Float> {
        val t = bounceT
        if (t < 0) return 1f to 1f
        fun lerpPair(a: Pair<Float, Float>, b: Pair<Float, Float>, k: Double) =
            (a.first + (b.first - a.first) * k.toFloat()) to (a.second + (b.second - a.second) * k.toFloat())
        val squash = 1.18f to 0.82f
        val stretch = 0.9f to 1.12f
        return when {
            t < 0.07 -> lerpPair(1f to 1f, squash, t / 0.07)
            t < 0.17 -> lerpPair(squash, stretch, (t - 0.07) / 0.1)
            else -> {
                val k = ((t - 0.17) / 0.18).coerceIn(0.0, 1.0)
                // Asentamiento elástico.
                val e = 1 - kotlin.math.cos(k * kotlin.math.PI * 2.5) * (1 - k)
                lerpPair(stretch, 1f to 1f, e)
            }
        }
    }
}

/** Estado de toda la escena, guiado por frames. Las posiciones x van de 0 a 1 en la zona de paseo. */
private class BiomeSceneState {
    var time = 0.0
    val actors = mutableMapOf<String, Actor>()

    // Medidas del último frame dibujado (ver layout).
    var slotSide = 0f // lado del sprite de un adulto
    var walkLeft = 0f
    var walkWidth = 1f
    var groundY = 0f

    /** Distancia mínima (en fracción de la zona de paseo) entre dos mascotas posadas. */
    var gap = 0.15

    fun layout(size: Size, startInset: Float, endInset: Float) {
        val raw = min(size.width * 0.3f, size.height * 0.24f)
        slotSide = raw
        val margin = slotSide * 0.1f
        walkLeft = startInset + margin
        walkWidth = max(1f, size.width - startInset - endInset - 2 * margin - slotSide)
        groundY = size.height * GROUND
        gap = (slotSide * 0.85f / walkWidth).toDouble()
    }

    /** Si es de noche (lo último que dijo la pantalla): descansan todas. */
    var night = false

    // Aspecto del momento del día, con transición suave al cambiar de franja.
    var look = DayLook.Midday

    /** Dormida o de noche: descansa (pose de dormir, sin volar). */
    fun isResting(pet: PetEntity) = pet.needs.isSleeping || night

    fun update(dt: Double, pets: List<PetEntity>, phase: DayPhase) {
        time += dt
        night = phase == DayPhase.night
        val ids = pets.map { it.id }.toSet()
        actors.keys.retainAll(ids)
        for (pet in pets) if (pet.id !in actors) actors[pet.id] = Actor(spawnSpot(), Random.nextDouble() * 10)

        look = lerp(look, DayLook.of(phase), (dt * 2.5).coerceIn(0.0, 1.0).toFloat())

        val ease = (dt * 4).coerceIn(0.0, 1.0)
        for (pet in pets) {
            val a = actors.getValue(pet.id)
            val asleep = isResting(pet)
            a.sleepT += ((if (asleep) 1.0 else 0.0) - a.sleepT) * ease
            val targetSat = 0.3 + 0.7 * pet.needs.wellbeing.coerceIn(0.0, 1.0)
            a.saturation += (targetSat - a.saturation) * ease
            a.lift += ((if (a.moving) 1.0 else 0.0) - a.lift) * (dt * 5).coerceIn(0.0, 1.0)

            if (pet.isEgg) a.moving = false // los huevos no se mueven
            else if (a.moving) fly(a, dt)
            else if (!asleep && a.reactT < 0) {
                a.restFor -= dt
                a.nextTurnIn -= dt
                if (a.nextTurnIn <= 0) {
                    a.nextTurnIn = 2.5 + Random.nextDouble() * 4
                    a.facingRight = !a.facingRight
                }
                if (a.restFor <= 0) takeOff(a)
            }

            if (a.reactT >= 0) a.reactT += dt
            if (a.reactT > REACT_DURATION || asleep) a.reactT = -1.0
            if (a.bounceT >= 0) a.bounceT += dt
            if (a.bounceT > BOUNCE_DURATION) a.bounceT = -1.0
            a.hearts.forEach { it.age += dt }
            a.hearts.removeAll { it.age > HEART_LIFE }
        }
    }

    /** Elige un destino al azar (suficientemente lejos) y despega. */
    private fun takeOff(a: Actor) {
        var target = Random.nextDouble()
        repeat(5) { if (abs(target - a.x) < gap) target = Random.nextDouble() }
        a.target = target
        a.moving = true
        a.facingRight = target > a.x
    }

    private fun fly(a: Actor, dt: Double) {
        val step = SPEED * dt
        val dir = sign(a.target - a.x)
        a.facingRight = dir > 0
        if (abs(a.target - a.x) > step) {
            a.x += dir * step
            return
        }
        a.x = a.target
        // Al posarse no puede quedar encima de otra: sigue de largo o se da la vuelta.
        if (!isFree(a.x, a)) {
            val spot = freeSpotFrom(a.x, if (dir == 0.0) 1.0 else dir, a)
            if (spot != null) {
                a.target = spot
                return
            }
        }
        a.moving = false
        a.restFor = 3.0 + Random.nextDouble() * 5
    }

    private fun isFree(x: Double, self: Actor?) =
        actors.values.none { it !== self && !it.moving && abs(it.x - x) < gap }

    /** Primer hueco libre avanzando en [dir]; si no hay, el primero en sentido contrario. */
    private fun freeSpotFrom(from: Double, dir: Double, self: Actor?): Double? {
        for (d in listOf(dir, -dir)) {
            var k = 1
            while (true) {
                val c = from + d * k * gap * 0.5
                if (c < 0.0 || c > 1.0) break
                if (isFree(c, self)) return c
                k++
            }
        }
        return null
    }

    private fun spawnSpot(): Double {
        repeat(30) {
            val c = Random.nextDouble()
            if (isFree(c, null)) return c
        }
        return Random.nextDouble()
    }

    /** Mascota bajo [pos]; la activa tiene prioridad porque se dibuja delante. */
    fun petAt(pos: Offset, activeId: String?): String? {
        activeId?.let { id -> if (actors[id]?.rect?.contains(pos) == true) return id }
        return actors.entries.lastOrNull { it.value.rect.contains(pos) }?.key
    }

    fun onTapped(id: String, pets: List<PetEntity>) {
        val a = actors[id] ?: return
        a.bounceT = 0.0
        val pet = pets.firstOrNull { it.id == id } ?: return
        // Dormida no se entera; de noche descansando sí recibe el mimo (sin cambiar de pose).
        if (!pet.needs.isSleeping) a.hearts += Actor.Heart()
        if (!isResting(pet)) a.reactT = 0.0
    }

    companion object {
        const val SPEED = 0.16 // fracción de la zona de paseo por segundo
        const val REACT_DURATION = 0.4
        const val BOUNCE_DURATION = 0.35
        const val HEART_LIFE = 0.9
    }
}

// Salpicaduras de barro en la rejilla de diseño de 64 unidades del frame (pose de pie).
private val DirtSplats = listOf(28 to 40, 36 to 37, 22 to 31, 32 to 45, 41 to 47, 24 to 44, 17 to 27, 45 to 53)
private val SplatShape = listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2)
private val SplatCore = listOf(1 to 1, 2 to 1, 2 to 2)

/** Tiempo que falta para eclosionar, en horas y minutos ("3 h 20 min", "12 min"). */
private fun timeLeft(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0) + 59_999) / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Un huevo en su nido: quieto, y en su última hora se balancea porque va a eclosionar. */
private fun DrawScope.drawEgg(state: BiomeSceneState, a: Actor, pet: PetEntity, image: ImageBitmap, now: Long) {
    val side = state.slotSide * 0.55f
    val slotLeft = state.walkLeft + a.x.toFloat() * state.walkWidth
    val left = slotLeft + (state.slotSide - side) / 2
    val topY = state.groundY - side
    a.rect = Rect(left, topY, left + side, topY + side)
    val remaining = pet.hatchesAt!! - now
    val wobble = if (remaining < 3_600_000L) sin((state.time + a.phase) * 9).toFloat() * 7f else 0f
    rotate(wobble, pivot = Offset(left + side / 2, state.groundY)) {
        drawImage(
            image,
            dstOffset = IntOffset(left.roundToInt(), topY.roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            colorFilter = state.look.colorFilter(PET_LOOK_SHARE),
        )
    }
}

private fun DrawScope.drawActor(state: BiomeSceneState, a: Actor, pet: PetEntity, sheet: ImageBitmap) {
    // Los sprites son de alta resolución: se escalan libremente con suavizado.
    val side = state.slotSide * pet.species.spriteScale
    val slotLeft = state.walkLeft + a.x.toFloat() * state.walkWidth
    val left = slotLeft + (state.slotSide - side) / 2
    val groundY = state.groundY
    val flap = if (a.lift > 0.01) sin((state.time + a.phase) * 9).toFloat() * side * 0.03f else 0f
    val liftPx = (a.lift.toFloat() * state.slotSide * 0.35f) + flap * a.lift.toFloat()
    val topY = groundY - side - liftPx
    a.rect = Rect(left, topY, left + side, topY + side)

    // Sombra en el suelo (más pequeña cuanto más alto vuela).
    val shadow = side * (0.6f - 0.25f * a.lift.toFloat())
    drawOval(Color(0x26000000), Offset(left + side / 2 - shadow / 2, groundY - side * 0.04f), Size(shadow, side * 0.08f))

    val asleep = state.isResting(pet)
    val t = state.time + a.phase
    val (col, row) = when {
        a.lift > 0.05 -> (floor(t / 0.08).toInt() % 4) to BirdSheet.FLY_ROW
        a.reactT >= 0 -> min(3, floor(a.reactT / 0.1).toInt()) to BirdSheet.REACT_ROW
        asleep -> 0 to BirdSheet.IDLE_ROW // dormir congela la pose idle
        else -> (floor(t / 0.45).toInt() % 4) to BirdSheet.IDLE_ROW
    }

    // Respiración idle: más lenta y profunda mientras duerme.
    val speed = 2.2 - a.sleepT * 1.2
    val amount = (0.02 + a.sleepT * 0.02) * (1 - a.lift)
    val b = (sin(t * speed) * amount).toFloat()
    val (bx, by) = a.bounceScale()
    // Desaturado si está mal, y con el filtro del momento del día (más suave que el fondo).
    val matrix = ColorMatrix().apply { setToSaturation(a.saturation.toFloat()) }
    matrix.timesAssign(state.look.colorMatrix(PET_LOOK_SHARE))
    val filter = ColorFilter.colorMatrix(matrix)
    val pivot = Offset(left + side / 2, topY + side)
    val dirtiness = ((60 - pet.needs.hygiene) / 60).coerceIn(0.0, 1.0)

    scale(bx * (1 + b), by * (1 - b), pivot) {
        val bounds = Rect(left, topY, left + side, topY + side)
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        drawSpriteFrame(sheet, BirdSheet.FRAME_SIZE, col, row, Offset(left, topY), side, flipX = a.facingRight, colorFilter = filter, filterQuality = BirdSheet.FILTER)
        if (dirtiness > 0) {
            // SrcAtop: la suciedad solo cae sobre los píxeles del propio pájaro.
            val texel = side / BirdSheet.DESIGN_GRID
            val alpha = (0.55 + dirtiness * 0.4).toFloat()
            val count = ceil(dirtiness * DirtSplats.size).toInt()
            fun splat(points: List<Pair<Int, Int>>, color: Color, sx: Int, sy: Int) {
                for ((dx, dy) in points) {
                    var x = (sx + dx) * texel
                    if (a.facingRight) x = side - x - texel
                    drawRect(color.copy(alpha = alpha), Offset(left + x, topY + (sy + dy) * texel), Size(texel, texel), blendMode = BlendMode.SrcAtop)
                }
            }
            for (i in 0 until count) {
                val (sx, sy) = DirtSplats[i]
                splat(SplatShape, Color(0xFF8A5E34), sx, sy)
                splat(SplatCore, Color(0xFF5A3A1E), sx, sy)
            }
        }
        drawIntoCanvas { it.restore() }
    }
}

private fun DrawScope.drawHearts(a: Actor) {
    for (heart in a.hearts) {
        val k = (heart.age / BiomeSceneState.HEART_LIFE).toFloat()
        val s = 26f * density / 2
        translate(a.rect.center.x - s / 2, a.rect.top - 70f * density / 2 * k - s / 2) {
            val path = Path().apply {
                moveTo(s / 2, s * 0.9f)
                cubicTo(-s * 0.1f, s * 0.45f, s * 0.2f, -s * 0.1f, s / 2, s * 0.28f)
                cubicTo(s * 0.8f, -s * 0.1f, s * 1.1f, s * 0.45f, s / 2, s * 0.9f)
                close()
            }
            drawPath(path, Color(0xFFFF4F79).copy(alpha = 1 - k))
        }
    }
}

private fun DrawScope.drawZzz(textMeasurer: TextMeasurer, state: BiomeSceneState, a: Actor) {
    if (a.sleepT <= 0.5) return
    val side = a.rect.width
    for (i in 0 until 3) {
        val phase = ((state.time * 0.5 + i / 3.0) % 1.0).toFloat()
        drawText(
            textMeasurer, "z",
            topLeft = Offset(a.rect.left + side * (0.7f + phase * 0.25f), a.rect.top + side * 0.15f - phase * side * 0.4f),
            style = TextStyle(color = Color.White, fontSize = (side * 0.18f / density).sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** Nombre sobre cada mascota; el de la activa, resaltado y con una flecha. */
private fun DrawScope.drawName(textMeasurer: TextMeasurer, a: Actor, name: String, active: Boolean) {
    val style = TextStyle(
        color = if (active) Color(0xFFFFE066) else Color.White,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
    )
    val layout = textMeasurer.measure(name, style)
    val x = a.rect.center.x - layout.size.width / 2
    // El polluelo es pequeño: la etiqueta va pegada a su cabeza, no en lo alto del frame.
    val y = a.rect.top + a.rect.height * 0.15f - layout.size.height - 6.dp.toPx()
    drawText(textMeasurer, name, Offset(x + 1.5f, y + 1.5f), style.copy(color = Color(0xAA000000)))
    drawText(textMeasurer, name, Offset(x, y), style)
    if (active) {
        val s = 6.dp.toPx()
        val cx = a.rect.center.x
        val ty = y - s - 3.dp.toPx()
        drawPath(Path().apply { moveTo(cx - s, ty); lineTo(cx + s, ty); lineTo(cx, ty + s); close() }, Color(0xFFFFE066))
    }
}

/** Qué parte del filtro del momento del día reciben los pájaros (respecto al fondo). */
private const val PET_LOOK_SHARE = 0.6f

/**
 * Dibuja [image] cubriendo todo el lienzo (recortando lo que sobre, centrado) sin suavizar los
 * píxeles, con el aspecto [look] del momento del día (matriz de color + degradado de cielo).
 */
fun DrawScope.drawBiomeBackdrop(image: ImageBitmap, look: DayLook = DayLook.Midday) {
    drawBackdropLayer(image, look)
    drawDayGradient(look)
}

/** Imágenes de un fondo por capas (ver BiomeLayers), de atrás adelante. */
class BackdropLayers(val sky: ImageBitmap, val back: ImageBitmap, val terrain: ImageBitmap)

/** Cuánto más grande se dibuja cada capa móvil, para tener margen al desplazarse sin ver bordes. */
private const val SKY_ZOOM = 1.06f
private const val BACK_ZOOM = 1.02f

/** Parte del margen que recorre cada capa: el cielo se mueve más que las colinas (parallax). */
private const val SKY_DRIFT = 1f
private const val BACK_DRIFT = 0.6f

/** Segundos que tarda el vaivén lento de las capas del fondo en ir y volver. */
private const val DRIFT_PERIOD_S = 80.0

/** Altura (fracción de la imagen) que se mantiene fija al ampliar una capa: la línea del horizonte. */
private const val HORIZON_ANCHOR = 0.70f

/**
 * Dibuja un fondo por capas: el cielo y los elementos de fondo se desplazan despacio a
 * distinta velocidad según [time] (segundos de escena), y el terreno queda fijo delante.
 * Cada capa recibe la matriz de color de [look] y el degradado del cielo va una sola vez
 * encima de todas, como en [drawBiomeBackdrop].
 */
fun DrawScope.drawLayeredBackdrop(
    layers: BackdropLayers,
    time: Double,
    look: DayLook = DayLook.Midday,
) {
    val drift = sin(time * 2 * kotlin.math.PI / DRIFT_PERIOD_S).toFloat()
    drawBackdropLayer(layers.sky, look, zoom = SKY_ZOOM, shift = drift * SKY_DRIFT)
    drawBackdropLayer(layers.back, look, zoom = BACK_ZOOM, shift = drift * BACK_DRIFT)
    drawBackdropLayer(layers.terrain, look)
    drawDayGradient(look)
}

/**
 * Dibuja [image] cubriendo el lienzo sin suavizar los píxeles. Con [zoom] > 1 se amplía
 * (manteniendo el horizonte a la misma altura que una capa sin ampliar) y [shift], de -1 a 1,
 * la desplaza en horizontal dentro del margen que deja la ampliación. Aplica la matriz de
 * color de [look] (el degradado del cielo lo añade quien llama, una vez por fondo).
 */
private fun DrawScope.drawBackdropLayer(
    image: ImageBitmap,
    look: DayLook,
    zoom: Float = 1f,
    shift: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val k0 = max(w / image.width, h / image.height)
    val k = k0 * zoom
    val srcW = (w / k).roundToInt().coerceAtMost(image.width)
    val srcH = (h / k).roundToInt().coerceAtMost(image.height)
    // Altura en pantalla del horizonte con el recorte centrado sin ampliar.
    val baseTop = (image.height - h / k0) / 2
    val horizonY = image.height * HORIZON_ANCHOR
    val screenHorizon = (horizonY - baseTop) * k0
    val top = (horizonY - screenHorizon / k).roundToInt().coerceIn(0, image.height - srcH)
    val slack = (image.width - srcW) / 2
    val left = (slack + shift.coerceIn(-1f, 1f) * slack).roundToInt().coerceIn(0, image.width - srcW)
    drawImage(
        image,
        srcOffset = IntOffset(left, top),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.None,
        colorFilter = if (look.isNeutral) null else look.colorFilter(),
    )
}
