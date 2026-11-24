package com.petbattle.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Música en Android con [MediaPlayer], leyendo el OGG directamente de los assets del APK
 * (ahí empaqueta Compose Multiplatform la carpeta composeResources).
 */
class AndroidMusicPlayer(private val context: Context) : MusicPlayer {
    private var player: MediaPlayer? = null
    private var track: MusicTrack? = null

    override fun play(track: MusicTrack) {
        if (this.track != track) {
            release()
            this.track = track
            player = create(track)
        }
        player?.takeIf { !it.isPlaying }?.start()
    }

    override fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    override fun release() {
        player?.release()
        player = null
        track = null
    }

    private fun create(track: MusicTrack): MediaPlayer? = try {
        context.assets.openFd("$ASSETS_ROOT/${track.path}").use { fd ->
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                isLooping = true
                prepare()
            }
        }
    } catch (e: Exception) {
        // Sin música antes que un cierre de la app.
        println("No se pudo cargar la música ${track.path}: $e")
        null
    }

    private companion object {
        // Carpeta de assets donde acaban los recursos del paquete com.petbattle.resources.
        const val ASSETS_ROOT = "composeResources/com.petbattle.resources"
    }
}
