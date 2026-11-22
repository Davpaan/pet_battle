package com.petbattle.core.persistence

/**
 * Single app-wide key/value store. Android backs it with SharedPreferences,
 * desktop with a small JSON file in the user's home. Repositories depend on
 * this interface, so tests can use an in-memory one.
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
