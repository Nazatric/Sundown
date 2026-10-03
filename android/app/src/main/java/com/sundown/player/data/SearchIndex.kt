package com.sundown.player.data

import android.content.Context
import java.io.Closeable
import com.sundown.player.data.db.TrackEntity

/** Optional SQLite FTS5 accelerator with a safe in-memory fallback in callers. */
class SearchIndex(context: Context) : Closeable {
    private val db = context.applicationContext.openOrCreateDatabase("sundown-search.db", 0, null)
    @Volatile private var indexedCount: Int = -1
    private val supported: Boolean = runCatching {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS track_fts USING fts5(id UNINDEXED, title, artist, album, genre)")
        true
    }.getOrDefault(false)

    @Synchronized
    fun rebuild(tracks: List<TrackEntity>) {
        if (!supported) return
        runCatching {
            db.beginTransaction()
            db.execSQL("DELETE FROM track_fts")
            val stmt = db.compileStatement("INSERT INTO track_fts(id,title,artist,album,genre) VALUES(?,?,?,?,?)")
            tracks.forEach { track ->
                stmt.clearBindings()
                stmt.bindString(1, track.id)
                stmt.bindString(2, track.title)
                stmt.bindString(3, track.artist)
                stmt.bindString(4, track.album)
                stmt.bindString(5, track.genre)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
            db.endTransaction()
            indexedCount = tracks.size
        }.onFailure {
            runCatching { db.endTransaction() }
            indexedCount = -1
        }
    }

    @Synchronized
    fun search(query: String): Set<String>? {
        if (!supported || query.isBlank() || indexedCount < 0) return null
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank).map {
            "\"${it.replace("\"", "\"\"")}\""
        }
        if (terms.isEmpty()) return emptySet()
        val match = terms.joinToString(" AND ")
        return runCatching {
            db.rawQuery("SELECT id FROM track_fts WHERE track_fts MATCH ?", arrayOf(match)).use { c ->
                buildSet {
                    val index = c.getColumnIndexOrThrow("id")
                    while (c.moveToNext()) add(c.getString(index))
                }
            }
        }.getOrNull()
    }

    fun isSupported(): Boolean = supported

    override fun close() {
        runCatching { db.close() }
    }
}
