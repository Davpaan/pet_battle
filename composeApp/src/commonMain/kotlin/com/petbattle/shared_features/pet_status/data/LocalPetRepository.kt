package com.petbattle.shared_features.pet_status.data

import com.petbattle.core.persistence.KeyValueStore
import com.petbattle.shared_features.pet_status.domain.PetRepository
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Guarda la partida (mascotas, oro, biomas...) como un único documento JSON versionado en el
 * almacén clave/valor de la app. La partida es un agregado pequeño que siempre se
 * lee/escribe como una unidad, así que no hace falta una base de datos.
 */
class LocalPetRepository(private val store: KeyValueStore) : PetRepository {

    @Serializable
    private data class Envelope(
        val v: Int,
        // La clave JSON sigue siendo "roster" (nombre antiguo) para no romper partidas guardadas.
        @SerialName("roster") val gameState: GameStateEntity,
    )

    override suspend fun load(): GameStateEntity? {
        val raw = store.getString(KEY) ?: return null
        return decode(raw)
    }

    override suspend fun save(gameState: GameStateEntity) = store.setString(KEY, encode(gameState))

    override suspend fun clear() = store.remove(KEY)

    companion object {
        private const val KEY = "pet_state"
        private const val SCHEMA_VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun encode(gameState: GameStateEntity): String =
            json.encodeToString(Envelope.serializer(), Envelope(SCHEMA_VERSION, gameState))

        /**
         * Especies retiradas -> especie que las sustituye. Se reescriben antes de decodificar
         * para no descartar partidas antiguas (las especies se guardan por nombre). Sólo se
         * cambia el valor JSON exacto entre comillas, no los ids que empiezan por ese nombre.
         */
        private val RENAMED_SPECIES = mapOf("stellersJay" to "beeEater")

        private fun migrate(raw: String): String =
            RENAMED_SPECIES.entries.fold(raw) { acc, (old, new) -> acc.replace("\"$old\"", "\"$new\"") }

        /** Lee una partida guardada; `null` si está corrupta o es de una versión desconocida. */
        fun decode(raw: String): GameStateEntity? = try {
            val envelope = json.decodeFromString(Envelope.serializer(), migrate(raw))
            if (envelope.v == SCHEMA_VERSION) envelope.gameState else null
        } catch (e: Exception) {
            // Partida corrupta o incompatible: empezar de cero en lugar de fallar.
            println("Discarding unreadable pet save: $e")
            null
        }
    }
}
