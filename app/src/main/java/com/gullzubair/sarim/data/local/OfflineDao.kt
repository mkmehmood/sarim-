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

    @Query("SELECT * FROM workspace_records WHERE ownerUid = :ownerUid")
    fun observeWorkspaceRecords(ownerUid: String): Flow<List<WorkspaceRecordEntity>>

    @Query("SELECT * FROM workspace_records WHERE ownerUid = :ownerUid")
    suspend fun getWorkspaceRecordsOnce(ownerUid: String): List<WorkspaceRecordEntity>

    @Query("SELECT * FROM workspace_records WHERE ownerUid = :ownerUid AND collection = :collection AND docId = :docId LIMIT 1")
    suspend fun getRecordOnce(ownerUid: String, collection: String, docId: String): WorkspaceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(entity: WorkspaceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(entities: List<WorkspaceRecordEntity>)

    @Query("DELETE FROM workspace_records WHERE ownerUid = :ownerUid AND collection = :collection AND docId = :docId")
    suspend fun deleteRecord(ownerUid: String, collection: String, docId: String)

    @Query(
        "DELETE FROM workspace_records WHERE ownerUid = :ownerUid " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM pending_sync_ops p " +
            "WHERE p.ownerUid = workspace_records.ownerUid " +
            "AND p.collection = workspace_records.collection " +
            "AND p.docId = workspace_records.docId" +
            ")"
    )
    suspend fun deleteCleanRecordsForOwner(ownerUid: String)

    @Query("SELECT collection || ':' || docId FROM pending_sync_ops WHERE ownerUid = :ownerUid")
    suspend fun getPendingDocKeys(ownerUid: String): List<String>

    @Transaction
    suspend fun replaceCleanWorkspaceRecords(ownerUid: String, incoming: List<WorkspaceRecordEntity>) {
        val dirtyKeys = getPendingDocKeys(ownerUid).toHashSet()
        deleteCleanRecordsForOwner(ownerUid)
        val filtered = if (dirtyKeys.isEmpty()) incoming else incoming.filter { "${it.collection}:${it.docId}" !in dirtyKeys }
        if (filtered.isNotEmpty()) {
            upsertRecords(filtered)
        }
    }

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE ownerUid = :ownerUid")
    fun observePendingSyncCount(ownerUid: String): Flow<Int>

    @Query("SELECT * FROM pending_sync_ops WHERE ownerUid = :ownerUid ORDER BY createdAtEpochMs ASC")
    suspend fun getPendingSyncOps(ownerUid: String): List<PendingSyncOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncOp(op: PendingSyncOpEntity)

    @Query("DELETE FROM pending_sync_ops WHERE opId = :opId")
    suspend fun deleteSyncOp(opId: String)

    @Query("DELETE FROM pending_sync_ops WHERE ownerUid = :ownerUid AND collection = :collection AND docId = :docId")
    suspend fun deleteSyncOpsForDoc(ownerUid: String, collection: String, docId: String)

    @Query("UPDATE pending_sync_ops SET attempts = attempts + 1, lastError = :error WHERE opId = :opId")
    suspend fun markSyncOpFailed(opId: String, error: String?)
}
