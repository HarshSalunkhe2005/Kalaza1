package com.kalazacare.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * One row per cached Supabase record, keyed by (table, id). Storing the same
 * `@Serializable` *Row JSON that SupabaseDataRepositories.kt already produces
 * (rather than a bespoke Room @Entity per domain type) means the cache needs
 * no TypeConverters and no second set of field mappings to keep in sync with
 * the real schema — decoding a cached row reuses the exact same toDomain()
 * extension the online path already uses.
 */
@Entity(tableName = "cached_rows", primaryKeys = ["tableName", "id"])
data class CachedRowEntity(
    val tableName: String,
    val id: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface CachedRowDao {
    @Query("SELECT * FROM cached_rows WHERE tableName = :table")
    suspend fun getAll(table: String): List<CachedRowEntity>

    @Query("SELECT * FROM cached_rows WHERE tableName = :table AND id = :id")
    suspend fun getById(table: String, id: String): CachedRowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CachedRowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CachedRowEntity>)

    @Query("DELETE FROM cached_rows WHERE tableName = :table AND id = :id")
    suspend fun delete(table: String, id: String)

    @Query("DELETE FROM cached_rows WHERE tableName = :table")
    suspend fun clearTable(table: String)
}

/** Status of one queued offline write, replayed by SyncManager once connectivity returns. */
object PendingOpStatus {
    const val PENDING = "PENDING"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
}

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val staffId: String,
    val staffName: String,
    val opType: String,
    val payloadJson: String,
    val status: String,
    val conflictReason: String? = null,
)

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_operations WHERE status = 'CONFLICT' ORDER BY createdAt DESC")
    fun observeConflicts(): Flow<List<PendingOperationEntity>>

    @Insert
    suspend fun insert(op: PendingOperationEntity)

    @Query("UPDATE pending_operations SET status = :status, conflictReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, reason: String?)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [CachedRowEntity::class, PendingOperationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KalazaCacheDb : RoomDatabase() {
    abstract fun cachedRowDao(): CachedRowDao
    abstract fun pendingOperationDao(): PendingOperationDao

    companion object {
        @Volatile private var instance: KalazaCacheDb? = null

        fun get(context: Context): KalazaCacheDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KalazaCacheDb::class.java,
                "kalaza_cache.db",
            ).build().also { instance = it }
        }
    }
}
