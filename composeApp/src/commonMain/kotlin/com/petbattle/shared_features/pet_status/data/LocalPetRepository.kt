package com.petbattle.shared_features.pet_status.data

import com.petbattle.core.persistence.KeyValueStore
import com.petbattle.shared_features.pet_status.domain.PetRepository
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stores every raised pet as one versioned JSON document in the app's
 * key/value store. The roster is a small aggregate always read/written as a
 * unit, so no database is needed.
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

        /** Reads a save; `null` for corrupt saves or unknown versions. */
        fun decode(raw: String): PetRosterEntity? = try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.v == SCHEMA_VERSION) envelope.roster else null
        } catch (e: Exception) {
            // Corrupt or incompatible save: start fresh rather than crash.
            println("Discarding unreadable pet save: $e")
            null
        }
    }
}
