package com.petbattle

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.petbattle.features.defense.presentation.DefenseScreen
import com.petbattle.features.pet.presentation.PetScreen
import com.petbattle.shared_features.pet_status.application.PetController

private enum class Screen { Pet, Defense }

private val AppColors = lightColorScheme(
    primary = Color(0xFF1F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CB),
    secondaryContainer = Color(0xFFCDE9DA),
    surface = Color(0xFFF5FBF6),
)

/** Root: the pet screen, and the defense mode on top of it. */
@Composable
fun App(controller: PetController) {
    var screen by remember { mutableStateOf(Screen.Pet) }
    MaterialTheme(colorScheme = AppColors) {
        Surface {
            when (screen) {
                Screen.Pet -> PetScreen(controller, onDefend = { screen = Screen.Defense })
                Screen.Defense -> DefenseScreen(controller, onExit = { screen = Screen.Pet })
            }
        }
    }
}
