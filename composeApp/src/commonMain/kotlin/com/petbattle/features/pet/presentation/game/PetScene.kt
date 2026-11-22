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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.sheet
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_status.domain.wellbeing
import org.jetbrains.compose.resources.imageResource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * El pequeño mundo de la mascota: cielo + suelo (de noche mientras duerme) y el pájaro,
 * dibujado desde su spritesheet. La expresividad viene de las animaciones idle/react,
 * la dirección en que mira, la desaturación cuando está mal, la suciedad pixel-art y
 * un rebote de squash & stretch más corazones al tocarla ([onPetTapped]).
 */
@Composable
fun PetScene(pet: PetEntity, onPetTapped: () -> Unit, modifier: Modifier = Modifier) {
    val sheet = imageResource(pet.species.sheet)
    val state = remember { PetSceneState() }
    val currentPet by rememberUpdatedState(pet)
    val onTap by rememberUpdatedState(onPetTapped)
    val textMeasurer = rememberTextMeasurer()
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                state.update(min(dt, 0.1), currentPet)
                frame++
            }
        }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                if (state.petRect.contains(pos)) {
                    state.onTapped(currentPet)
                    onTap()
                }
            }
        },
    ) {
        frame // se lee para que cada frame se redibuje
        drawBackground(state.nightT)
        drawPet(state, currentPet, sheet)
        drawHearts(state)
        if (state.sleepT > 0.5) {
            val side = state.petRect.width
            for (i in 0 until 3) {
                val phase = ((state.time * 0.5 + i / 3.0) % 1.0).toFloat()
                drawText(
                    textMeasurer, "z",
                    topLeft = Offset(
                        state.petRect.left + side * (0.7f + phase * 0.25f),
                        state.petRect.top + side * 0.15f - phase * side * 0.4f,
                    ),
                    style = TextStyle(color = Color.White, fontSize = (side * 0.18f / density).sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

/** Estado de la escena guiado por frames (nunca se persiste). */
private class PetSceneState {
    var time = 0.0
    var nightT = 0.0
    var sleepT = 0.0
    var saturation = 1.0
    var facingRight = false
    var nextTurnIn = 3.0
    var reactT = -1.0 // segundos de reacción al toque, <0 = sin reaccionar
    var bounceT = -1.0
    val hearts = mutableListOf<Heart>()
    var petRect = Rect.Zero

    class Heart(val x: Float, var y: Float, var age: Double = 0.0)

    fun update(dt: Double, pet: PetEntity) {
        time += dt
        val asleep = pet.status.isSleeping
        val ease = (dt * 4).coerceIn(0.0, 1.0)
        nightT += ((if (asleep) 1.0 else 0.0) - nightT) * (dt * 2.5).coerceIn(0.0, 1.0)
        sleepT += ((if (asleep) 1.0 else 0.0) - sleepT) * ease
        val targetSat = 0.3 + 0.7 * pet.status.wellbeing.coerceIn(0.0, 1.0)
        saturation += (targetSat - saturation) * ease
        if (!asleep) {
            nextTurnIn -= dt
            if (nextTurnIn <= 0) {
                nextTurnIn = 2.5 + Random.nextDouble() * 4
                facingRight = !facingRight
            }
        }
        if (reactT >= 0) reactT += dt
        if (reactT > REACT_DURATION || asleep) reactT = -1.0
        if (bounceT >= 0) bounceT += dt
        if (bounceT > BOUNCE_DURATION) bounceT = -1.0
        hearts.forEach { it.age += dt }
        hearts.removeAll { it.age > HEART_LIFE }
    }

    fun onTapped(pet: PetEntity) {
        bounceT = 0.0
        if (!pet.status.isSleeping) {
            reactT = 0.0
            hearts += Heart(petRect.center.x, petRect.top)
        }
    }

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

    companion object {
        const val REACT_DURATION = 0.4
        const val BOUNCE_DURATION = 0.35
        const val HEART_LIFE = 0.9
    }
}

private val DaySky = listOf(Color(0xFF8FD3FF), Color(0xFFE3F6FF))
private val NightSky = listOf(Color(0xFF1B2147), Color(0xFF3B3F78))
private val DayFloor = Color(0xFFF2C894)
private val NightFloor = Color(0xFF6B5A7A)

/** Cielo + suelo dibujados proceduralmente; funde a noche mientras duerme. */
private fun DrawScope.drawBackground(nightT: Double) {
    val t = nightT.toFloat()
    val w = size.width
    val h = size.height
    val groundY = h * 0.78f
    val top = lerp(DaySky[0], NightSky[0], t)
    val bottom = lerp(DaySky[1], NightSky[1], t)
    drawRect(Brush.verticalGradient(listOf(top, bottom), 0f, groundY), size = Size(w, groundY))
    // El sol se transforma en luna.
    val c = Offset(w * 0.8f, h * 0.14f)
    drawCircle(lerp(Color(0xFFFFE066), Color(0xFFF4F1DE), t), w * 0.07f, c)
    if (t > 0.01f) drawCircle(top.copy(alpha = t), w * 0.065f, c + Offset(w * 0.03f, -w * 0.015f))
    val floor = lerp(DayFloor, NightFloor, t)
    drawRect(floor, Offset(0f, groundY), Size(w, h - groundY))
    drawRect(floor.copy(alpha = 0.5f), Offset(0f, groundY), Size(w, 6f))
}

// Salpicaduras de barro en coordenadas de texel del frame de 64px (pose de pie).
private val DirtSplats = listOf(28 to 40, 36 to 37, 22 to 31, 32 to 45, 41 to 47, 24 to 44, 17 to 27, 45 to 53)
private val SplatShape = listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 2, 2 to 2, 3 to 2)
private val SplatCore = listOf(1 to 1, 2 to 1, 2 to 2)

private fun DrawScope.drawPet(state: PetSceneState, pet: PetEntity, sheet: ImageBitmap) {
    val w = size.width
    val h = size.height
    // Un múltiplo entero del frame mantiene nítido el pixel art (sin texels desiguales).
    val raw = min(w * 0.45f, h * 0.32f)
    val scale = max(2, (raw / BirdSheet.FRAME_SIZE).roundToInt())
    val side = (scale * BirdSheet.FRAME_SIZE).toFloat()
    val groundY = h * 0.78f
    val left = w / 2 - side / 2
    val topY = groundY - side
    state.petRect = Rect(left, topY, left + side, groundY)

    // Sombra en el suelo.
    drawOval(Color(0x26000000), Offset(w / 2 - side * 0.3f, groundY - side * 0.04f), Size(side * 0.6f, side * 0.08f))

    val asleep = pet.status.isSleeping
    val (col, row) = when {
        state.reactT >= 0 -> min(3, floor(state.reactT / 0.1).toInt()) to BirdSheet.REACT_ROW
        asleep -> 0 to BirdSheet.IDLE_ROW // dormir congela la pose idle
        else -> (floor(state.time / 0.45).toInt() % 4) to BirdSheet.IDLE_ROW
    }

    // Respiración idle: más lenta y profunda mientras duerme.
    val speed = 2.2 - state.sleepT * 1.2
    val amount = 0.02 + state.sleepT * 0.02
    val b = (sin(state.time * speed) * amount).toFloat()
    val (bx, by) = state.bounceScale()
    val filter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(state.saturation.toFloat()) })
    val pivot = Offset(w / 2, groundY)
    val dirtiness = ((60 - pet.status.hygiene) / 60).coerceIn(0.0, 1.0)

    scale(bx * (1 + b), by * (1 - b), pivot) {
        val bounds = Rect(left, topY, left + side, groundY)
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        drawSpriteFrame(sheet, BirdSheet.FRAME_SIZE, col, row, Offset(left, topY), side, flipX = state.facingRight, colorFilter = filter)
        if (dirtiness > 0) {
            // SrcAtop: la suciedad solo cae sobre los píxeles del propio pájaro.
            val texel = side / BirdSheet.FRAME_SIZE
            val alpha = (0.55 + dirtiness * 0.4).toFloat()
            val count = ceil(dirtiness * DirtSplats.size).toInt()
            fun splat(points: List<Pair<Int, Int>>, color: Color, sx: Int, sy: Int) {
                for ((dx, dy) in points) {
                    var x = (sx + dx) * texel
                    if (state.facingRight) x = side - x - texel
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

private fun DrawScope.drawHearts(state: PetSceneState) {
    for (heart in state.hearts) {
        val k = (heart.age / PetSceneState.HEART_LIFE).toFloat()
        val s = 26f * density / 2
        translate(heart.x - s / 2, heart.y - 70f * density / 2 * k - s / 2) {
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
