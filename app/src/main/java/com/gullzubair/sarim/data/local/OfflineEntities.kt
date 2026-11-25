package com.gullzubair.sarim.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val workspaceOwnerUid: String,
    val role: String,
    val rolesJson: String,
    val approved: Boolean,
    val inviteId: String?,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "workspace_records",
    primaryKeys = ["ownerUid", "collection", "docId"],
    indices = [Index(value = ["ownerUid", "collection"])]
)
data class WorkspaceRecordEntity(
    val ownerUid: String,
    val collection: String,
    val docId: String,
    val payloadJson: String,
    val sortDate: String,
    val recycled: Boolean,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "pending_sync_ops",
    indices = [Index(value = ["ownerUid", "createdAtEpochMs"])]
)
data class PendingSyncOpEntity(
    @PrimaryKey val opId: String,
    val ownerUid: String,
    val collection: String,
    val docId: String,
    val opType: String, // SET | UPDATE | DELETE | SETTINGS | INVITE | MEMBER_ROLE
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attempts: Int = 0,
    val lastError: String? = null
)
