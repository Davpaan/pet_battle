package com.petbattle.shared_features.pet_status.data

import com.petbattle.core.persistence.KeyValueStore
import com.petbattle.shared_features.pet_status.domain.PetRepository
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Guarda todas las mascotas criadas como un único documento JSON versionado en el
 * almacén clave/valor de la app. El plantel es un agregado pequeño que siempre se
 * lee/escribe como una unidad, así que no hace falta una base de datos.
 */
class LocalPetRepository(private val store: KeyValueStore) : PetRepository {

    @Serializable
    private data class Envelope(val v: Int, val roster: PetRosterEntity)

    override suspend fun load(): PetRosterEntity? {
        val raw = store.getString(KEY) ?: return null
        return decode(raw)
    }

    override suspend fun save(roster: PetRosterEntity) = store.setString(KEY, encode(roster))

    override suspend fun clear() = store.remove(KEY)

    companion object {
        private const val KEY = "pet_state"
        private const val SCHEMA_VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun encode(roster: PetRosterEntity): String =
            json.encodeToString(Envelope.serializer(), Envelope(SCHEMA_VERSION, roster))

        /** Lee una partida guardada; `null` si está corrupta o es de una versión desconocida. */
        fun decode(raw: String): PetRosterEntity? = try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.v == SCHEMA_VERSION) envelope.roster else null
        } catch (e: Exception) {
            // Partida corrupta o incompatible: empezar de cero en lugar de fallar.
            println("Discarding unreadable pet save: $e")
            null
        }
    }
}
