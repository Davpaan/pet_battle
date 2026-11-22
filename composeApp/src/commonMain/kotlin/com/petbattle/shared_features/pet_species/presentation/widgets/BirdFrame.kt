package com.petbattle.shared_features.pet_species.presentation.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.petbattle.core.ui.drawSpriteFrame
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.BirdSheet
import com.petbattle.shared_features.pet_species.presentation.sheet
import org.jetbrains.compose.resources.imageResource

/** One crisp, upscaled frame of a bird spritesheet (idle pose by default). */
@Composable
fun BirdFrame(
    species: PetSpecies,
    size: Dp,
    modifier: Modifier = Modifier,
    row: Int = BirdSheet.IDLE_ROW,
    column: Int = 0,
) {
    val image = imageResource(species.sheet)
    Canvas(modifier.size(size)) {
        drawSpriteFrame(image, BirdSheet.FRAME_SIZE, column, row, Offset.Zero, this.size.width)
    }
}
