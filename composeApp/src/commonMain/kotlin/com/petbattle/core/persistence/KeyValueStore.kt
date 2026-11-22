package com.petbattle.core.persistence

/**
 * Almacén clave/valor único de toda la app. En Android usa SharedPreferences,
 * en escritorio un pequeño archivo JSON en el home del usuario. Los repositorios dependen de
 * esta interfaz, así que los tests pueden usar una en memoria.
 */
interface KeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun setString(key: String, value: String)
    suspend fun remove(key: String)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun getString(key: String) = map[key]
    override suspend fun setString(key: String, value: String) { map[key] = value }
    override suspend fun remove(key: String) { map.remove(key) }
}
