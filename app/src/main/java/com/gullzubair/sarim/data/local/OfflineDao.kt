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

    @Query("SELECT * FROM kv_store")
    fun observeAllKv(): Flow<List<KvStoreEntity>>

    @Query("SELECT * FROM kv_store")
    suspend fun getAllKvOnce(): List<KvStoreEntity>

    @Query("SELECT * FROM kv_store WHERE `key` = :key LIMIT 1")
    suspend fun getKv(key: String): KvStoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKv(entity: KvStoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putKvBatch(entities: List<KvStoreEntity>)

    @Query("DELETE FROM kv_store WHERE `key` = :key")
    suspend fun deleteKv(key: String)

    @Query("DELETE FROM kv_store")
    suspend fun clearKv()

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
