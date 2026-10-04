package com.sundown.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.sundown.player.data.db.TrackEntity
import java.io.Closeable

/** Optional FTS5 substring-search accelerator. Callers retain the normal Kotlin fallback. */
class SearchIndex(context: Context) : Closeable {
    private val appContext = context.applicationContext

    private var database: SQLiteDatabase? = null
    private var initializationAttempted = false
    private var supported = false
    private var ready = false
    private var documents: Map<String, String>? = null

    /**
     * Incrementally syncs only changed search documents and removed IDs.
     * The regular content table is authoritative; triggers keep its trigram FTS
     * index transactionally consistent across process restarts.
     */
    @Synchronized
    fun synchronize(tracks: List<TrackEntity>) {
        val db = openDatabase() ?: return
        val previous = documents ?: run {
            ready = false
            return
        }
        val current = HashMap<String, String>(tracks.size)
        val changed = ArrayList<SearchDocument>()

        tracks.forEach { track ->
            val text = track.searchDocumentText()
            current[track.id] = text
            if (previous[track.id] != text) changed += SearchDocument(track.id, text)
        }
        val removed = previous.keys.filterNot { current.containsKey(it) }

        if (changed.isEmpty() && removed.isEmpty()) {
            documents = current
            ready = true
            return
        }

        ready = false
        var transactionStarted = false
        var transactionSuccessful = false
        try {
            db.beginTransaction()
            transactionStarted = true
            val values = ContentValues(2)
            changed.forEach { document ->
                values.clear()
                values.put("text", document.text)
                if (db.update("search_documents", values, "id = ?", arrayOf(document.id)) == 0) {
                    values.put("id", document.id)
                    db.insertOrThrow("search_documents", null, values)
                }
            }
            removed.chunked(DELETE_BATCH_SIZE).forEach { batch ->
                val placeholders = List(batch.size) { "?" }.joinToString(",")
                db.delete("search_documents", "id IN ($placeholders)", batch.toTypedArray())
            }
            db.setTransactionSuccessful()
            transactionSuccessful = true
        } catch (_: Exception) {
            transactionSuccessful = false
        } finally {
            if (transactionStarted) {
                runCatching { db.endTransaction() }
                    .onFailure { transactionSuccessful = false }
            }
        }
        if (transactionSuccessful) {
            documents = current
            ready = true
        }
    }

    /**
     * FTS5 trigram returns a superset of exact case-insensitive substring
     * matches. Short and non-ASCII queries use the caller's regular fallback,
     * because trigram tokenization is not consistently available on older
     * Android SQLite builds or equivalent for every Unicode case-folding rule.
     */
    @Synchronized
    fun search(query: String, tracks: List<TrackEntity>): Set<String>? {
        if (!ready || !canAccelerate(query)) return null
        val db = openDatabase() ?: return null
        if (!supported) return null
        val indexedDocuments = documents ?: return null
        val match = "\"${query.replace("\"", "\"\"")}\""

        val matches = runCatching {
            db.rawQuery(
                "SELECT search_documents.id " +
                    "FROM track_fts JOIN search_documents " +
                    "ON search_documents.rowid = track_fts.rowid " +
                    "WHERE track_fts MATCH ?",
                arrayOf(match),
            ).use { cursor ->
                buildSet {
                    val idColumn = cursor.getColumnIndexOrThrow("id")
                    while (cursor.moveToNext()) add(cursor.getString(idColumn))
                }
            }
        }.getOrNull() ?: return null

        // Room and this optional index observe separate flows. Include tracks
        // whose documents have not reached SQLite yet to avoid transient false
        // negatives during a scan/update race.
        val notYetIndexed = tracks.asSequence()
            .filter { it.matchesPendingSearch(indexedDocuments[it.id], query) }
            .map(TrackEntity::id)
            .toSet()
        return if (notYetIndexed.isEmpty()) matches else matches + notYetIndexed
    }

    @Synchronized
    fun isSupported(): Boolean = openDatabase() != null && supported

    @Synchronized
    override fun close() {
        runCatching { database?.close() }
        database = null
        documents = null
        ready = false
        supported = false
        initializationAttempted = false
    }

    @Synchronized
    private fun openDatabase(): SQLiteDatabase? {
        if (initializationAttempted) return database.takeIf { supported }
        initializationAttempted = true

        val db = runCatching {
            appContext.openOrCreateDatabase(DATABASE_NAME, 0, null)
        }.getOrNull() ?: return null
        database = db

        var transactionStarted = false
        var transactionSuccessful = false
        try {
            db.beginTransaction()
            transactionStarted = true
            if (db.version != DATABASE_VERSION) {
                db.execSQL("DROP TRIGGER IF EXISTS search_documents_ai")
                db.execSQL("DROP TRIGGER IF EXISTS search_documents_ad")
                db.execSQL("DROP TRIGGER IF EXISTS search_documents_au")
                db.execSQL("DROP TABLE IF EXISTS track_fts")
                db.execSQL("DROP TABLE IF EXISTS search_documents")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS search_documents (" +
                    "id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS track_fts USING " +
                    "fts5(text, content='search_documents', content_rowid='rowid', tokenize='trigram')",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS search_documents_ai AFTER INSERT ON search_documents BEGIN " +
                    "INSERT INTO track_fts(rowid, text) VALUES (new.rowid, new.text); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS search_documents_ad AFTER DELETE ON search_documents BEGIN " +
                    "INSERT INTO track_fts(track_fts, rowid, text) VALUES ('delete', old.rowid, old.text); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS search_documents_au AFTER UPDATE ON search_documents BEGIN " +
                    "INSERT INTO track_fts(track_fts, rowid, text) VALUES ('delete', old.rowid, old.text); " +
                    "INSERT INTO track_fts(rowid, text) VALUES (new.rowid, new.text); END",
            )
            db.version = DATABASE_VERSION
            db.setTransactionSuccessful()
            transactionSuccessful = true
        } catch (_: Exception) {
            transactionSuccessful = false
        } finally {
            if (transactionStarted) {
                runCatching { db.endTransaction() }
                    .onFailure { transactionSuccessful = false }
            }
        }
        if (!transactionSuccessful) {
            supported = false
            documents = null
            runCatching { db.close() }
            database = null
            return null
        }

        val loadedDocuments = readDocuments(db)
        if (loadedDocuments == null) {
            supported = false
            runCatching { db.close() }
            database = null
            return null
        }
        supported = true
        documents = loadedDocuments
        return db
    }

    private fun readDocuments(db: SQLiteDatabase): Map<String, String>? =
        runCatching {
            db.rawQuery("SELECT id, text FROM search_documents", null).use { cursor ->
                buildMap(cursor.count) {
                    val idColumn = cursor.getColumnIndexOrThrow("id")
                    val textColumn = cursor.getColumnIndexOrThrow("text")
                    while (cursor.moveToNext()) put(cursor.getString(idColumn), cursor.getString(textColumn))
                }
            }
        }.getOrNull()

    private fun canAccelerate(query: String): Boolean =
        query.length >= MIN_QUERY_LENGTH && query.all { it.code in ASCII_PRINTABLE_RANGE }

    private data class SearchDocument(val id: String, val text: String)

    private companion object {
        const val DATABASE_NAME = "sundown-search.db"
        // Search-document field changes must rebuild both the source table and FTS5 rows.
        const val DATABASE_VERSION = 3
        const val DELETE_BATCH_SIZE = 400
        const val MIN_QUERY_LENGTH = 3
        val ASCII_PRINTABLE_RANGE = 0x20..0x7E
    }
}
