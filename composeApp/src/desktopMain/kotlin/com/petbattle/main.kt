package com.petbattle

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.petbattle.core.time.SystemClock
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val repository = LocalPetRepository(FileKeyValueStore())
    val controller = PetController(repository, SystemClock, scope)
    scope.launch { controller.load() }

    application {
        Window(
            onCloseRequest = {
                // Asegura que los últimos cambios lleguen al disco antes de salir.
                controller.gameState.value?.let { runBlocking { repository.save(it) } }
                exitApplication()
            },
            title = "Pet Battle",
            state = rememberWindowState(width = 1280.dp, height = 720.dp),
        ) {
            App(controller)
        }
    }
}
