package com.petbattle

import com.petbattle.core.persistence.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Desktop store: one JSON object in ~/.local/share/pet_battle/store.json
 * (XDG data dir on Linux). Writes go to a temp file first, then replace.
 */
class FileKeyValueStore(private val file: File = defaultFile()) : KeyValueStore {
    private fun read(): Map<String, String> = try {
        if (!file.exists()) emptyMap()
        else Json.parseToJsonElement(file.readText()).let { it as JsonObject }
            .mapValues { it.value.jsonPrimitive.content }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun write(map: Map<String, String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) { read()[key] }
    override suspend fun setString(key: String, value: String) =
        withContext(Dispatchers.IO) { write(read() + (key to value)) }
    override suspend fun remove(key: String) = withContext(Dispatchers.IO) { write(read() - key) }

    companion object {
        fun defaultFile(): File {
            val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.local/share")
            return File(base, "pet_battle/store.json")
        }
    }
}
