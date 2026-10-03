package com.sundown.player.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("sundown-prefs")

/** User settings, local-source access and playback checkpoints stored in DataStore. */
data class Prefs(
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "off",
    val queue: List<String> = emptyList(),
    val currentId: String? = null,
    val position: Long = 0L,
    val favorites: Set<String> = emptySet(),
    val defaultTab: String = "Albums",
    val autoRescan: Boolean = true,
    val showIndex: Boolean = true,
    val highArt: Boolean = true,
    val keepAwake: Boolean = false,
    val treeUri: String? = null,
    val treeName: String? = null,
)

class SundownPrefs(private val context: Context) {
    private object K {
        val volume = floatPreferencesKey("volume")
        val muted = booleanPreferencesKey("muted")
        val shuffle = booleanPreferencesKey("shuffle")
        val repeat = stringPreferencesKey("repeat")
        val queue = stringPreferencesKey("queue")
        val currentId = stringPreferencesKey("currentId")
        val position = longPreferencesKey("position")
        val favorites = stringSetPreferencesKey("favorites")
        val defaultTab = stringPreferencesKey("defaultTab")
        val autoRescan = booleanPreferencesKey("autoRescan")
        val showIndex = booleanPreferencesKey("showIndex")
        val highArt = booleanPreferencesKey("highArt")
        val keepAwake = booleanPreferencesKey("keepAwake")
        val treeUri = stringPreferencesKey("treeUri")
        val treeName = stringPreferencesKey("treeName")
    }

    val flow: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            volume = p[K.volume] ?: 0.8f,
            muted = p[K.muted] ?: false,
            shuffle = p[K.shuffle] ?: false,
            repeat = p[K.repeat] ?: "off",
            queue = p[K.queue]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            currentId = p[K.currentId],
            position = p[K.position] ?: 0L,
            favorites = p[K.favorites] ?: emptySet(),
            defaultTab = p[K.defaultTab] ?: "Albums",
            autoRescan = p[K.autoRescan] ?: true,
            showIndex = p[K.showIndex] ?: true,
            highArt = p[K.highArt] ?: true,
            keepAwake = p[K.keepAwake] ?: false,
            treeUri = p[K.treeUri],
            treeName = p[K.treeName],
        )
    }

    suspend fun update(block: (Prefs) -> Prefs) {
        context.dataStore.edit { store ->
            val current = Prefs(
                volume = store[K.volume] ?: 0.8f,
                muted = store[K.muted] ?: false,
                shuffle = store[K.shuffle] ?: false,
                repeat = store[K.repeat] ?: "off",
                queue = store[K.queue]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                currentId = store[K.currentId],
                position = store[K.position] ?: 0L,
                favorites = store[K.favorites] ?: emptySet(),
                defaultTab = store[K.defaultTab] ?: "Albums",
                autoRescan = store[K.autoRescan] ?: true,
                showIndex = store[K.showIndex] ?: true,
                highArt = store[K.highArt] ?: true,
                keepAwake = store[K.keepAwake] ?: false,
                treeUri = store[K.treeUri],
                treeName = store[K.treeName],
            )
            val next = block(current)
            store[K.volume] = next.volume
            store[K.muted] = next.muted
            store[K.shuffle] = next.shuffle
            store[K.repeat] = next.repeat
            store[K.queue] = next.queue.joinToString("\n")
            next.currentId?.let { store[K.currentId] = it } ?: store.remove(K.currentId)
            store[K.position] = next.position
            store[K.favorites] = next.favorites
            store[K.defaultTab] = next.defaultTab
            store[K.autoRescan] = next.autoRescan
            store[K.showIndex] = next.showIndex
            store[K.highArt] = next.highArt
            store[K.keepAwake] = next.keepAwake
            next.treeUri?.let { store[K.treeUri] = it } ?: store.remove(K.treeUri)
            next.treeName?.let { store[K.treeName] = it } ?: store.remove(K.treeName)
        }
    }
}
