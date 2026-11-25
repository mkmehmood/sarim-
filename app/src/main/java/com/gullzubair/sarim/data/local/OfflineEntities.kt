package com.gullzubair.sarim.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val ownerUid: String?,
    val role: String,
    val rolesJson: String,
    val approved: Boolean,
    val createdBy: String,
    val inviteId: String?,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "kv_store")
data class KvStoreEntity(
    @PrimaryKey val key: String,
    val valueJson: String,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "ndapp_outbox",
    indices = [Index(value = ["ownerUid", "createdAt"])]
)
data class NdappOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerUid: String,
    val action: String, // SET | DELETE | SINGLETON | PHOTO | PENDING | OWNER_PING
    val col: String,
    val docId: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null
)
