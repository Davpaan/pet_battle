package com.petbattle

import com.petbattle.core.audio.MusicTrack
import com.petbattle.core.audio.decodeToPcm
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Todas las pistas existen y el reproductor de escritorio las sabe decodificar. */
class MusicTracksTest {
    private val res = File("src/commonMain/composeResources")

    @Test fun everyTrackDecodesOnDesktop() {
        for (track in MusicTrack.entries) {
            val file = File(res, track.path)
            assertTrue(file.exists(), "Falta ${track.path}")
            val pcm = decodeToPcm(file.readBytes())
            assertTrue(pcm.frameLength > 44_100, "${track.path} dura menos de un segundo")
        }
    }
}
