package com.sundown.player.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Persisted metadata and source fingerprints for one locally indexed audio track. */
@Entity(
    tableName = "tracks",
    indices = [Index("albumKey"), Index("artistKey"), Index("genre"), Index("source"), Index("artId")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val docUri: String,
    val path: String,
    val name: String,
    val size: Long,
    val mtime: Long,
    /** Opaque MediaStore version for this row; null for SAF and one-off files. */
    val sourceVersion: String?,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val trackNo: Int,
    val discNo: Int,
    val year: Int,
    val duration: Int,
    val artId: String?,
    val albumKey: String,
    val artistKey: String,
    /** "folder" (persisted tree) or "file" (one-off picks). */
    val source: String,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Newline-joined track ids; ordering is user-defined so a list is correct. */
    val trackIds: String,
    val custom: Boolean,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT id, docUri, path, size, mtime, sourceVersion, artId FROM tracks WHERE source = :source")
    suspend fun fingerprints(source: String): List<FingerprintRow>

    @Query("SELECT DISTINCT artId FROM tracks WHERE artId IS NOT NULL")
    suspend fun referencedArtworkIds(): List<String>

    @Query("SELECT * FROM tracks WHERE artId = :artId LIMIT 1")
    suspend fun trackForArtwork(artId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("UPDATE tracks SET artId = (SELECT t2.artId FROM tracks t2 WHERE t2.albumKey = tracks.albumKey AND t2.artId IS NOT NULL LIMIT 1) WHERE artId IS NULL AND albumKey IN (:albumKeys)")
    suspend fun fillMissingArtwork(albumKeys: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    @Query("UPDATE tracks SET duration = :seconds WHERE id = :id")
    suspend fun updateDuration(id: String, seconds: Int)

    @Query("SELECT * FROM playlists")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()
}

data class FingerprintRow(
    val id: String,
    val docUri: String,
    val path: String,
    val size: Long,
    val mtime: Long,
    val sourceVersion: String?,
    val artId: String?,
)

@Database(entities = [TrackEntity::class, PlaylistEntity::class], version = 3, exportSchema = false)
abstract class SundownDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: SundownDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_source` ON `tracks` (`source`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_artId` ON `tracks` (`artId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `tracks` ADD COLUMN `sourceVersion` TEXT")
            }
        }

        fun get(context: android.content.Context): SundownDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SundownDatabase::class.java,
                    "sundown-music.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
