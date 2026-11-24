package com.petbattle.core.audio

import com.petbattle.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * Música en escritorio con un [Clip] de javax.sound (OGG gracias a vorbisspi). La pista se carga entera en
 * memoria en segundo plano; mientras carga, [play]/[pause] solo anotan lo que se quiere.
 */
class DesktopMusicPlayer : MusicPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clip: Clip? = null
    private var track: MusicTrack? = null
    private var loading: Job? = null
    private var wantPlaying = false

    override fun play(track: MusicTrack) {
        wantPlaying = true
        if (this.track != track) {
            closeClip()
            this.track = track
            loading = scope.launch {
                val loaded = withContext(Dispatchers.IO) { load(track) } ?: return@launch
                clip = loaded
                if (wantPlaying) loaded.loop(Clip.LOOP_CONTINUOUSLY)
            }
        } else {
            clip?.takeIf { !it.isRunning }?.loop(Clip.LOOP_CONTINUOUSLY)
        }
    }

    override fun pause() {
        wantPlaying = false
        clip?.stop()
    }

    override fun release() {
        closeClip()
        track = null
    }

    private fun closeClip() {
        loading?.cancel()
        loading = null
        clip?.close()
        clip = null
    }

    private suspend fun load(track: MusicTrack): Clip? = try {
        val bytes = Res.readBytes(track.path)
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { encoded ->
            // El OGG se decodifica (vorbisspi) a PCM de 16 bits; el Clip necesita la longitud
            // exacta, así que se decodifica entero en memoria antes de abrirlo.
            val src = encoded.format
            val pcm = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, src.sampleRate, 16, src.channels,
                src.channels * 2, src.sampleRate, false,
            )
            val decoded = AudioSystem.getAudioInputStream(pcm, encoded).use { it.readBytes() }
            val frames = decoded.size.toLong() / pcm.frameSize
            AudioSystem.getClip().apply {
                open(AudioInputStream(ByteArrayInputStream(decoded), pcm, frames))
            }
        }
    } catch (e: Exception) {
        // Equipos sin dispositivo de audio: el juego sigue sin música.
        println("No se pudo cargar la música ${track.path}: $e")
        null
    }
}
