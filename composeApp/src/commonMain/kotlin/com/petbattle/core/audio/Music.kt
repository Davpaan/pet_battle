package com.petbattle.core.audio

import com.petbattle.core.persistence.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pistas de música del juego; [path] es la ruta dentro de composeResources. */
enum class MusicTrack(val path: String) {
    /** Pantalla principal (la mascota en su bioma). */
    MainTheme("files/sounds/main_theme.ogg"),

    /** Modo defensa: cada partida suena una de las tres al azar ([randomBattle]). */
    Battle1("files/sounds/battle_1.ogg"),
    Battle2("files/sounds/battle_2.ogg"),
    Battle3("files/sounds/battle_3.ogg");

    companion object {
        private val battleTracks = listOf(Battle1, Battle2, Battle3)

        fun randomBattle(): MusicTrack = battleTracks.random()
    }
}

/**
 * Reproductor de música de cada plataforma. Las pistas siempre suenan en bucle.
 * Todas las llamadas llegan desde el hilo principal.
 */
interface MusicPlayer {
    /** Pone [track] en marcha; si ya era la pista actual, la reanuda donde se quedó. */
    fun play(track: MusicTrack)

    /** Detiene la música conservando la posición. */
    fun pause()

    /** Libera los recursos de audio (al cerrar la app). */
    fun release()
}

/** Reproductor mudo, para tests o plataformas sin audio. */
object SilentMusicPlayer : MusicPlayer {
    override fun play(track: MusicTrack) = Unit
    override fun pause() = Unit
    override fun release() = Unit
}

/**
 * Decide qué suena y cuándo: la pista de la pantalla actual, solo si el sonido está activado
 * (opción guardada en [store]) y la app está en primer plano.
 */
class MusicController(
    private val player: MusicPlayer,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
) {
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private var track: MusicTrack? = null
    private var inForeground = true
    // Hasta leer la opción guardada no suena nada (podría estar desactivada).
    private var loaded = false

    suspend fun load() {
        _soundEnabled.value = store.getString(KEY_SOUND) != "false"
        loaded = true
        apply()
    }

    /** Cambia la pista según la pantalla visible. */
    fun setTrack(track: MusicTrack) {
        this.track = track
        apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        apply()
        scope.launch { store.setString(KEY_SOUND, enabled.toString()) }
    }

    /** La app pasa a segundo plano (true) o vuelve (false). */
    fun setInBackground(background: Boolean) {
        inForeground = !background
        apply()
    }

    fun release() = player.release()

    private fun apply() {
        val current = track
        if (loaded && inForeground && _soundEnabled.value && current != null) player.play(current) else player.pause()
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
    }
}
