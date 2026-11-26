package com.gullzubair.sarim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    @Query("SELECT * FROM cached_profiles WHERE uid = :uid LIMIT 1")
    fun observeProfile(uid: String): Flow<CachedProfileEntity?>

    @Query("SELECT * FROM cached_profiles WHERE uid = :uid LIMIT 1")
    suspend fun getProfileOnce(uid: String): CachedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: CachedProfileEntity)

    // NOTE: never `SELECT *` / `valueJson` straight from kv_store. A single row (e.g. the
    // "photos" map of base64 images) can exceed the ~2 MB CursorWindow, which throws
    // SQLiteBlobTooBigException and crash-loops the app. Values are read in bounded chunks.
    @Query("SELECT `key` AS `key`, LENGTH(valueJson) AS valueLength, updatedAtEpochMs FROM kv_store")
    fun observeKvMeta(): Flow<List<KvMeta>>

    @Query("SELECT `key` AS `key`, LENGTH(valueJson) AS valueLength, updatedAtEpochMs FROM kv_store")
    suspend fun getKvMeta(): List<KvMeta>

    @Query("SELECT `key` AS `key`, LENGTH(valueJson) AS valueLength, updatedAtEpochMs FROM kv_store WHERE `key` = :key LIMIT 1")
    suspend fun getKvMetaFor(key: String): KvMeta?

    @Query("SELECT SUBSTR(valueJson, :start, :length) FROM kv_store WHERE `key` = :key")
    suspend fun getKvChunk(key: String, start: Int, length: Int): String?

    /** Consistent (single transaction) snapshot of every kv row, safe for oversized rows. */
    @Transaction
    suspend fun getAllKvOnce(): List<KvStoreEntity> = getKvMeta().map { readKvValue(it) }

    @Transaction
    suspend fun getKv(key: String): KvStoreEntity? = getKvMetaFor(key)?.let { readKvValue(it) }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKv(entity: KvStoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKvBatch(entities: List<KvStoreEntity>)

    @Query("DELETE FROM kv_store WHERE `key` = :key")
    suspend fun deleteKv(key: String)

    @Query("DELETE FROM kv_store")
    suspend fun clearKv()

    @Query("DELETE FROM kv_store WHERE `key` IN (:keys)")
    suspend fun deleteKvKeys(keys: List<String>)

    /** Atomically applies photo row upserts and deletions (chunked to stay under SQLite's variable limit). */
    @Transaction
    suspend fun applyKvChanges(upserts: List<KvStoreEntity>, deleteKeys: List<String>) {
        deleteKeys.chunked(500).forEach { deleteKvKeys(it) }
        upserts.chunked(20).forEach { putKvBatch(it) }
    }

    @Query("SELECT COUNT(*) FROM ndapp_outbox WHERE ownerUid = :ownerUid")
    fun observeOutboxCount(ownerUid: String): Flow<Int>

    @Query("SELECT * FROM ndapp_outbox WHERE ownerUid = :ownerUid ORDER BY createdAt ASC, id ASC")
    suspend fun getOutboxOnce(ownerUid: String): List<NdappOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOutbox(op: NdappOutboxEntity): Long

    @Query("DELETE FROM ndapp_outbox WHERE id = :id")
    suspend fun deleteOutboxById(id: Long)

    @Query("DELETE FROM ndapp_outbox WHERE ownerUid = :ownerUid AND col = :col AND docId = :docId")
    suspend fun deleteOutboxForDoc(ownerUid: String, col: String, docId: String)

    @Query("DELETE FROM ndapp_outbox WHERE ownerUid = :ownerUid")
    suspend fun clearOutboxForOwner(ownerUid: String)

    @Query("UPDATE ndapp_outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markOutboxFailed(id: Long, error: String?)

    @Transaction
    suspend fun replaceKvKeys(updates: List<KvStoreEntity>) {
        if (updates.isNotEmpty()) putKvBatch(updates)
    }
}

/** Lightweight kv_store row descriptor (no payload) used to read big values in chunks. */
data class KvMeta(
    val key: String,
    val valueLength: Long,
    val updatedAtEpochMs: Long
)

/** Max characters fetched per query; at <= 4 bytes/char this stays well under the 2 MB CursorWindow. */
const val KV_CHUNK_CHARS = 250_000

internal suspend fun OfflineDao.readKvValue(meta: KvMeta): KvStoreEntity {
    val total = meta.valueLength
    if (total <= KV_CHUNK_CHARS) {
        val whole = getKvChunk(meta.key, 1, KV_CHUNK_CHARS).orEmpty()
        return KvStoreEntity(meta.key, whole, meta.updatedAtEpochMs)
    }
    val sb = StringBuilder(total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    var start = 1
    while (start <= total) {
        val chunk = getKvChunk(meta.key, start, KV_CHUNK_CHARS) ?: break
        if (chunk.isEmpty()) break
        sb.append(chunk)
        start += KV_CHUNK_CHARS
    }
    return KvStoreEntity(meta.key, sb.toString(), meta.updatedAtEpochMs)
}
