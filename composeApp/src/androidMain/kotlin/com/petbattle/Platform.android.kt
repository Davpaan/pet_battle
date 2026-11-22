package com.petbattle

import android.content.Context
import com.petbattle.core.persistence.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SharedPreferences-backed store (one small JSON string per key). */
class AndroidKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("pet_battle", Context.MODE_PRIVATE)
    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }
    override suspend fun setString(key: String, value: String) =
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
    override suspend fun remove(key: String) =
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
}
