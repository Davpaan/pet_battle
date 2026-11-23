package com.petbattle.features.defense.presentation.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.features.defense.domain.DefenseBalance
import kotlin.math.floor
import kotlin.math.sin

/** Todas las imágenes que necesita la escena de defensa, cargadas una vez por la pantalla. */
class DefenseSprites(
    val map: ImageBitmap,
    val slime: ImageBitmap,
    val skills: Map<PetElement, ImageBitmap>,
    val pets: Map<PetSpecies, ImageBitmap>,
)

/** Disposición de `enemy_slime.png`: frames de 32x32, mirando a la izquierda. */
object SlimeSheet {
    const val FRAME = 32
    const val MOVE_ROW = 0
    const val MOVE_FRAMES = 8
    const val DEATH_ROW = 1

    /** Línea del suelo dentro del frame (donde el slime toca el suelo). */
    const val GROUND_Y = 29f
    const val SCALE = 2f
    const val SIZE = FRAME * SCALE
}

/** Disposición de `skill_<element>.png`: 4x3 frames de 32x32. */
object SkillSheet {
    const val FRAME = 32
    const val PROJECTILE_ROW = 0
    const val IMPACT_ROW = 1
    const val STATUS_ROW = 2
    const val SIZE = FRAME * 2f
}

/** Dibuja texto en píxeles de mapa, cacheando los layouts (las etiquetas rara vez cambian). */
class TextPainter(private val measurer: TextMeasurer) {
    private val cache = HashMap<Pair<String, Long>, TextLayoutResult>()

    private fun layout(text: String, color: Long, sizePx: Float, density: Float): TextLayoutResult {
        if (cache.size > 200) cache.clear()
        return cache.getOrPut(text to (color * 31 + sizePx.toLong())) {
            measurer.measure(
                text,
                TextStyle(
                    color = Color(color),
                    fontSize = (sizePx / density).sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 0f),
                ),
            )
        }
    }

    /** [at] es el centro inferior del texto. */
    fun draw(scope: DrawScope, text: String, at: Offset, color: Long = 0xFFFFFFFF, sizePx: Float = 18f, alpha: Float = 1f) {
        val l = layout(text, color, sizePx, scope.density)
        scope.drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height), alpha = alpha)
    }
}

private val whiteFlash = ColorFilter.tint(Color.White, BlendMode.SrcAtop)

/** Dibuja toda la escena en coordenadas de mapa (quien llama fija la transformación). */
fun DrawScope.drawDefense(game: DefenseGame, sprites: DefenseSprites, text: TextPainter) {
    val map = game.map
    drawImage(
        sprites.map,
        dstSize = IntSize(map.width, map.height),
        filterQuality = FilterQuality.Medium,
    )

    if (game.showPlacement) {
        val a = 0.18f + 0.1f * sin(game.time * 3).toFloat()
        val tint = Color(0xFF7CFF6B).copy(alpha = a)
        for (cell in game.placeableCells) drawRect(tint, cell.topLeft, cell.size)
    }

    // Orden de profundidad: más abajo en el mapa = más al frente.
    val drawables = buildList<Pair<Float, () -> Unit>> {
        game.defenders.values.forEach { d -> add(d.position.y to { drawDefender(d, game, sprites, text) }) }
        game.enemies.forEach { e -> add(e.position.y to { drawEnemy(e, sprites, text) }) }
        game.effects.filter { it.kind == AnimEffect.Kind.SlimeDeath }.forEach { fx ->
            add(fx.position.y to { drawSlimeDeath(fx, sprites) })
        }
    }.sortedBy { it.first }
    drawables.forEach { it.second() }

    for (p in game.projectiles) {
        val image = sprites.skills.getValue(p.element)
        val col = floor(p.age / 0.07).toInt() % 4
        rotate(Math_toDegrees(p.angle), pivot = p.position) {
            drawSpriteFrame(
                image, SkillSheet.FRAME, col, SkillSheet.PROJECTILE_ROW,
                p.position - Offset(SkillSheet.SIZE / 2, SkillSheet.SIZE / 2), SkillSheet.SIZE,
            )
        }
    }
    for (fx in game.effects) {
        if (fx.kind != AnimEffect.Kind.SkillImpact) continue
        drawSpriteFrame(
            sprites.skills.getValue(fx.element!!), SkillSheet.FRAME, fx.frame, SkillSheet.IMPACT_ROW,
            fx.position - Offset(SkillSheet.SIZE / 2, SkillSheet.SIZE / 2), SkillSheet.SIZE,
        )
    }
    for (t in game.texts) {
        text.draw(this, t.text, t.position, t.color, t.sizePx, alpha = (1 - t.age / t.life).toFloat().coerceIn(0f, 1f))
    }
    for (m in game.markers) {
        val a = (m.age / 0.3).let { 1 - (m.age / InvalidMarker.LIFE) }.toFloat().coerceIn(0f, 1f)
        val c = Color(0xFFE53935).copy(alpha = a)
        val p = m.position
        drawLine(c, p + Offset(-12f, -12f), p + Offset(12f, 12f), 6f, StrokeCap.Round)
        drawLine(c, p + Offset(12f, -12f), p + Offset(-12f, 12f), 6f, StrokeCap.Round)
    }
}

private fun Math_toDegrees(rad: Float) = rad * 180f / kotlin.math.PI.toFloat()

private fun DrawScope.drawDefender(d: Defender, game: DefenseGame, sprites: DefenseSprites, text: TextPainter) {
    val size = Defender.SIZE
    val feet = d.position
    val chest = d.muzzle
    // Alcance.
    // Alcance de su especie.
    drawCircle(Color(0x14FFFFFF), d.range.toFloat(), chest)
    drawCircle(Color(0x55FFFFFF), d.range.toFloat(), chest, style = Stroke(2f))
    // Sombra.
    drawOval(Color(0x40000000), Offset(feet.x - size * 0.25f, feet.y - 6f), Size(size * 0.5f, 12f))

    // Efecto de aparición + aplastamiento al disparar.
    val pop = if (d.age < 0.3) 1 + 0.15f * sin(d.age / 0.3 * kotlin.math.PI).toFloat() else 1f
    val squash = if (d.shotAge < 0.16) sin(d.shotAge / 0.16 * kotlin.math.PI).toFloat() else 0f
    val col = floor(game.time / 0.45).toInt() % 4
    // Recogida para recolocarla: semitransparente.
    val alpha = if (game.editingId == d.pet.id) 0.45f else 1f
    scale(pop * (1 + 0.08f * squash), pop * (1 - 0.08f * squash), pivot = feet) {
        drawSpriteFrame(
            sprites.pets.getValue(d.pet.species), BirdSheet.FRAME_SIZE, col, BirdSheet.IDLE_ROW,
            Offset(feet.x - size / 2, feet.y - size), size, flipX = d.facingRight,
            alpha = alpha, filterQuality = BirdSheet.FILTER,
        )
    }
    // Nombre · nivel (o agotada) y barra de energía.
    val top = feet.y - size
    val label = if (d.exhausted) "${d.pet.name} · ¡Agotado!" else "${d.pet.name} · Nv.${d.pet.progression.level}"
    text.draw(this, label, Offset(feet.x, top + size * 0.16f))
    val energy = (d.pet.needs.energy / PetNeedsBalance.MAX_STAT).coerceIn(0.0, 1.0).toFloat()
    val w = 56f
    val bar = Offset(feet.x - w / 2, top + size * 0.16f + 2)
    drawRect(Color(0xAA000000), bar, Size(w, 6f))
    drawRect(lerp(Color(0xFFE53935), Color(0xFFFFD54F), energy), bar + Offset(1f, 1f), Size((w - 2) * energy, 4f))
}

private fun DrawScope.drawEnemy(e: Enemy, sprites: DefenseSprites, text: TextPainter) {
    val size = SlimeSheet.SIZE
    val topLeft = Offset(e.position.x - size / 2, e.position.y - SlimeSheet.GROUND_Y * SlimeSheet.SCALE)
    drawOval(Color(0x40000000), Offset(e.position.x - size * 0.31f, e.position.y - 5f), Size(size * 0.62f, 10f))
    val col = floor(e.animT / 0.1).toInt().mod(SlimeSheet.MOVE_FRAMES)
    drawSpriteFrame(
        sprites.slime, SlimeSheet.FRAME, col, SlimeSheet.MOVE_ROW, topLeft, size,
        flipX = e.facingRight, colorFilter = if (e.flash > 0) whiteFlash else null,
    )
    // Las capas de efectos comparten el frame de 32px del slime, así que encajan.
    fun overlay(element: PetElement, step: Double) = drawSpriteFrame(
        sprites.skills.getValue(element), SkillSheet.FRAME, floor(e.effectT / step).toInt() % 4,
        SkillSheet.STATUS_ROW, topLeft, size, flipX = e.facingRight,
    )
    if (e.entity.isSlowed) overlay(PetElement.water, 0.15)
    if (e.entity.isBurning) overlay(PetElement.fire, 0.1)
    if (e.gust > 0) overlay(PetElement.air, 0.08)

    // Nivel + barra de salud sobre la cabeza.
    val barW = 40f
    val bar = Offset(e.position.x - barW / 2, topLeft.y + 6)
    drawRect(Color(0xAA000000), bar, Size(barW, 6f))
    drawRect(Color(0xFF6CE07A), bar + Offset(1f, 1f), Size((barW - 2) * e.entity.healthFraction.toFloat(), 4f))
    text.draw(this, "Nv.${e.entity.level}", Offset(e.position.x, bar.y - 1), sizePx = 13f)
}

private fun DrawScope.drawSlimeDeath(fx: AnimEffect, sprites: DefenseSprites) {
    val size = SlimeSheet.SIZE
    val topLeft = Offset(fx.position.x - size / 2, fx.position.y - SlimeSheet.GROUND_Y * SlimeSheet.SCALE)
    drawSpriteFrame(sprites.slime, SlimeSheet.FRAME, fx.frame, SlimeSheet.DEATH_ROW, topLeft, size, flipX = fx.flipX)
}
