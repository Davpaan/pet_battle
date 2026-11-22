package com.petbattle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.petbattle.core.time.SystemClock
import com.petbattle.shared_features.pet_status.application.PetController
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: PetController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        controller = PetController(LocalPetRepository(AndroidKeyValueStore(applicationContext)), SystemClock, lifecycleScope)
        lifecycleScope.launch { controller.load() }
        setContent { App(controller) }
    }

    override fun onPause() {
        controller.flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.tick() // catch up the time spent in background
    }
}
