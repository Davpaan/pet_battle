package com.petbattle.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petbattle.resources.Res
import com.petbattle.resources.ui_arrow_down
import com.petbattle.resources.ui_biome
import com.petbattle.resources.ui_clean
import com.petbattle.resources.ui_close
import com.petbattle.resources.ui_coin
import com.petbattle.resources.ui_defend
import com.petbattle.resources.ui_egg
import com.petbattle.resources.ui_feed
import com.petbattle.resources.ui_level
import com.petbattle.resources.ui_menu
import com.petbattle.resources.ui_play
import com.petbattle.resources.ui_plus
import com.petbattle.resources.ui_release
import com.petbattle.resources.ui_sleep
import com.petbattle.resources.ui_stat_beak
import com.petbattle.resources.ui_stat_climate
import com.petbattle.resources.ui_stat_digestion
import com.petbattle.resources.ui_stat_energy
import com.petbattle.resources.ui_stat_fun
import com.petbattle.resources.ui_stat_hunger
import com.petbattle.resources.ui_stat_hygiene
import com.petbattle.resources.ui_stat_plumage
import com.petbattle.resources.ui_stat_social
import com.petbattle.resources.ui_stat_confidence
import com.petbattle.resources.ui_info
import com.petbattle.resources.ui_food_carrion
import com.petbattle.resources.ui_food_fruit
import com.petbattle.resources.ui_food_insects
import com.petbattle.resources.ui_food_nectar
import com.petbattle.resources.ui_food_nuts
import com.petbattle.resources.ui_food_seeds
import com.petbattle.resources.ui_food_worms
import com.petbattle.resources.ui_element_air
import com.petbattle.resources.ui_element_earth
import com.petbattle.resources.ui_element_fire
import com.petbattle.resources.ui_element_water
import com.petbattle.resources.ui_type_darkness
import com.petbattle.resources.ui_type_elemental
import com.petbattle.resources.ui_type_light
import com.petbattle.resources.ui_combat_damage
import com.petbattle.resources.ui_combat_speed
import com.petbattle.resources.ui_wake
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Estilo común de la interfaz del juego: paneles y botones de madera con contorno oscuro y
 * texto blanco con sombra, a juego con los iconos cartoon (tools/ui_icons/generate_ui.py).
 */
object GameUi {
    val Outline = Color(0xFF3A2416)
    val WoodLight = Color(0xFFDDA05C)
    val WoodDark = Color(0xFFAF6E35)
    val Wood = Brush.verticalGradient(listOf(WoodLight, WoodDark))
    val Inset = Color(0xCC2E1E14) // fondo oscuro de contadores y barras
    val Gold = Color(0xFFFFC93C)
    val Green = Color(0xFF5DBB46)
    val Purple = Color(0xFF9B62DA)
    val PanelShape = RoundedCornerShape(16.dp)

    /** Texto blanco grueso con sombra, legible sobre cualquier fondo. */
    fun text(size: TextUnit = 16.sp, color: Color = Color.White) = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        shadow = Shadow(Color(0xCC000000), Offset(1.5f, 2f), 2f),
    )
}

/** Iconos de la interfaz (ui_*.png). */
enum class GameIcon(val res: DrawableResource) {
    Coin(Res.drawable.ui_coin), Egg(Res.drawable.ui_egg), Biome(Res.drawable.ui_biome),
    Menu(Res.drawable.ui_menu), Level(Res.drawable.ui_level), Feed(Res.drawable.ui_feed),
    Sleep(Res.drawable.ui_sleep), Wake(Res.drawable.ui_wake), Clean(Res.drawable.ui_clean),
    Defend(Res.drawable.ui_defend), Release(Res.drawable.ui_release), Plus(Res.drawable.ui_plus),
    ArrowDown(Res.drawable.ui_arrow_down), Close(Res.drawable.ui_close), Play(Res.drawable.ui_play),
    Hunger(Res.drawable.ui_stat_hunger), Energy(Res.drawable.ui_stat_energy), Fun(Res.drawable.ui_stat_fun),
    Hygiene(Res.drawable.ui_stat_hygiene), Social(Res.drawable.ui_stat_social), Climate(Res.drawable.ui_stat_climate),
    Plumage(Res.drawable.ui_stat_plumage), Digestion(Res.drawable.ui_stat_digestion), Beak(Res.drawable.ui_stat_beak),
    Confidence(Res.drawable.ui_stat_confidence), Info(Res.drawable.ui_info),
    Damage(Res.drawable.ui_combat_damage), Speed(Res.drawable.ui_combat_speed),
    Fire(Res.drawable.ui_element_fire), Air(Res.drawable.ui_element_air),
    Water(Res.drawable.ui_element_water), Earth(Res.drawable.ui_element_earth),
    Light(Res.drawable.ui_type_light), Darkness(Res.drawable.ui_type_darkness),
    Elemental(Res.drawable.ui_type_elemental),
    Seeds(Res.drawable.ui_food_seeds), Nuts(Res.drawable.ui_food_nuts), Fruit(Res.drawable.ui_food_fruit),
    Insects(Res.drawable.ui_food_insects), Worms(Res.drawable.ui_food_worms), Nectar(Res.drawable.ui_food_nectar),
    Carrion(Res.drawable.ui_food_carrion),
}

@Composable
fun GameIconImage(icon: GameIcon, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painterResource(icon.res), contentDescription, modifier.size(size))
}

/** Panel de madera con contorno oscuro. */
@Composable
fun WoodPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GameUi.PanelShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(GameUi.Wood)
            .border(3.dp, GameUi.Outline, shape)
            .padding(3.dp)
            // Filete claro interior: da el canto de la tabla.
            .border(1.5.dp, Color(0x66FFF1D6), shape),
        content = content,
    )
}

/** Botón redondo de madera con un icono del juego. Sin [onClick] se ve apagado. */
@Composable
fun WoodIconButton(
    icon: GameIcon,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    badge: GameIcon? = null,
) {
    Box(modifier.size(size)) {
        WoodPanel(
            Modifier
                .matchParentSize()
                .alpha(if (onClick == null) 0.45f else 1f)
                .clickable(
                    enabled = onClick != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClick?.invoke() },
                ),
            shape = CircleShape,
        ) {
            GameIconImage(icon, size * 0.62f, Modifier.align(Alignment.Center), contentDescription)
        }
        if (badge != null) GameIconImage(badge, size * 0.38f, Modifier.align(Alignment.BottomEnd))
    }
}

/** Barra de progreso del juego: fondo oscuro con contorno y relleno [color] (0..1). */
@Composable
fun GameBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(GameUi.Inset)
            .border(1.5.dp, GameUi.Outline, shape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 1f), color.darken(0.75f)))),
        )
    }
}

private fun Color.darken(k: Float) = Color(red * k, green * k, blue * k, alpha)

/** Botón rectangular de madera con texto (y un icono opcional). */
@Composable
fun WoodButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: GameIcon? = null,
) {
    WoodPanel(
        modifier
            .alpha(if (onClick == null) 0.5f else 1f)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        shape = RoundedCornerShape(14.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) GameIconImage(icon, 28.dp, Modifier.padding(end = 8.dp))
            Text(text, style = GameUi.text(16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
