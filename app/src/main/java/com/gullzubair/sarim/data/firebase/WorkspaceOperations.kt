package com.gullzubair.sarim.data.firebase

import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.gullzubair.sarim.data.local.OfflineSyncRepository
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.domain.InputGuards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID

class WorkspaceOperations(private val repo: AppRepository) {
    private val db: FirebaseFirestore get() = repo.db
    private val offline: OfflineSyncRepository? get() = repo.offlineRepo

    companion object {
        const val TOMBSTONE_EXPIRY_MS = 90L * 24L * 60L * 60L * 1000L
        val GATED_COLLECTIONS = setOf(
            "production", "sales", "rep", "clients", "customers", "calculator",
            "inventory", "factory", "entities", "transactions", "expenses", "returns"
        )
    }

    private fun workspaceCol(ownerUid: String, collection: String) =
        db.collection("users").document(ownerUid).collection(collection)

    fun newId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

    private fun isOfflineFirestoreException(e: Throwable): Boolean {
        val code = (e as? FirebaseFirestoreException)?.code ?: return false
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private suspend fun executeRemoteOrKeepQueued(
        outboxId: Long,
        remoteCall: suspend () -> Unit
    ) {
        if (!repo.isOnline()) return
        val synced = withTimeoutOrNull(2500L) {
            remoteCall()
            true
        }
        if (synced == true && outboxId > 0L) {
            offline?.markOutboxSynced(outboxId)
        }
    }

    private suspend fun pingWorkspaceOwner(ownerUid: String, profile: UserProfile) {
        if (profile.uid == ownerUid) return
        runCatching {
            db.collection("users").document(ownerUid).update("lastWrite", System.currentTimeMillis()).await()
        }
    }

    suspend fun submitOrExecuteWrite(
        profile: UserProfile,
        targetCollection: String,
        docId: String?,
        summaryTitle: String,
        summaryDetail: String,
        payload: Map<String, Any?>,
        isEdit: Boolean = false
    ): Result<String> {
        val ownerUid = profile.effectiveOwnerUid
        val resolvedId = docId?.takeIf { it.isNotBlank() } ?: newId(targetCollection.take(4))
        val path = "users/$ownerUid/$targetCollection/$resolvedId"
        var outboxId = 0L
        return try {
            val uid = repo.requireUserId()
            val cleanPayload = payload.filterValues { it != null }.mapValues { (_, v) -> v as Any }
            val nowMs = System.currentTimeMillis()
            val docMap = cleanPayload.toMutableMap().apply {
                put("id", resolvedId)
                if (!containsKey("timestamp")) put("timestamp", nowMs)
                if (!containsKey("createdAt")) put("createdAt", nowMs)
            }

            if (profile.isAdmin || targetCollection !in GATED_COLLECTIONS) {
                offline?.upsertLocalCollectionDoc(ownerUid, targetCollection, resolvedId, docMap)
                outboxId = offline?.enqueueOutbox(ownerUid, "SET", targetCollection, resolvedId, docMap) ?: 0L

                val remoteMap = docMap.toMutableMap().apply {
                    put("updatedAt", FieldValue.serverTimestamp())
                    put("syncedAt", Date().toInstant().toString())
                }
                executeRemoteOrKeepQueued(outboxId) {
                    workspaceCol(ownerUid, targetCollection).document(resolvedId)
                        .set(remoteMap, SetOptions.merge()).await()
                    pingWorkspaceOwner(ownerUid, profile)
                }
                Result.success(resolvedId)
            } else {
                val pendingId = "${targetCollection}__${resolvedId.replace("/", "_")}"
                val pendingObj = PendingApproval(
                    id = pendingId,
                    collection = targetCollection,
                    docId = resolvedId,
                    action = "set",
                    dataMap = docMap,
                    isEdit = isEdit,
                    status = "pending",
                    submittedByUid = uid,
                    submittedByName = profile.displayName.ifBlank { profile.email },
                    submittedByEmail = profile.email,
                    submittedAt = nowMs,
                    summaryTitle = summaryTitle.take(120),
                    summaryBody = summaryDetail.take(500)
                )
                val pendingMap = WorkspaceJsonCodec.pendingToMap(pendingObj)
                offline?.upsertLocalCollectionDoc(ownerUid, "pending", pendingId, pendingMap)
                outboxId = offline?.enqueueOutbox(ownerUid, "PENDING", "pending", pendingId, pendingMap) ?: 0L
                executeRemoteOrKeepQueued(outboxId) {
                    workspaceCol(ownerUid, "pending").document(pendingId).set(pendingMap, SetOptions.merge()).await()
                    pingWorkspaceOwner(ownerUid, profile)
                }
                Result.success("PENDING:$pendingId")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(if (profile.isAdmin) resolvedId else "PENDING:offline")
            } else {
                offline?.discardOutboxOp(outboxId)
                handleFirestoreError(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun softDeleteToRecycleBin(
        profile: UserProfile,
        collection: String,
        docId: String,
        displayName: String,
        displayDetail: String,
        displayAmount: String,
        snapshotMap: Map<String, Any>
    ): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val path = "users/$ownerUid/$collection/$docId"
        var outboxId = 0L
        return try {
            val uid = repo.requireUserId()
            val nowMs = System.currentTimeMillis()
            if (profile.isAdmin) {
                val tombstone = DeletionTombstone(
                    id = docId,
                    recordId = docId,
                    collection = collection,
                    recordType = collection,
                    deletedAt = nowMs,
                    expiresAt = nowMs + TOMBSTONE_EXPIRY_MS,
                    displayName = displayName,
                    displayDetail = displayDetail,
                    displayAmount = displayAmount,
                    snapshotJson = WorkspaceJsonCodec.mapToJson(snapshotMap),
                    deletedBy = profile.email.ifBlank { uid },
                    syncedToCloud = false
                )
                offline?.removeLocalCollectionDoc(ownerUid, collection, docId)
                offline?.upsertLocalDeletion(ownerUid, tombstone)

                val remoteTombstone = mapOf<String, Any>(
                    "id" to docId,
                    "recordId" to docId,
                    "collection" to collection,
                    "recordType" to collection,
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to Timestamp(Date(nowMs + TOMBSTONE_EXPIRY_MS)),
                    "displayName" to displayName,
                    "displayDetail" to displayDetail,
                    "displayAmount" to displayAmount,
                    "snapshot" to snapshotMap,
                    "deleted_by" to profile.email.ifBlank { uid }
                )
                val queuePayload = WorkspaceJsonCodec.deletionToLocalMap(tombstone)
                outboxId = offline?.enqueueOutbox(ownerUid, "DELETE", collection, docId, queuePayload) ?: 0L

                executeRemoteOrKeepQueued(outboxId) {
                    val batch = db.batch()
                    batch.delete(workspaceCol(ownerUid, collection).document(docId))
                    batch.set(workspaceCol(ownerUid, "deletions").document(docId), remoteTombstone, SetOptions.merge())
                    batch.commit().await()
                }
            } else {
                val pendingId = "${collection}__${docId.replace("/", "_")}"
                val pendingObj = PendingApproval(
                    id = pendingId,
                    collection = collection,
                    docId = docId,
                    action = "delete",
                    dataMap = emptyMap(),
                    isEdit = true,
                    status = "pending",
                    submittedByUid = uid,
                    submittedByName = profile.displayName.ifBlank { profile.email },
                    submittedByEmail = profile.email,
                    submittedAt = nowMs,
                    summaryTitle = "Delete $collection",
                    summaryBody = "$displayName ($displayDetail)".take(400)
                )
                val pendingMap = WorkspaceJsonCodec.pendingToMap(pendingObj)
                offline?.upsertLocalCollectionDoc(ownerUid, "pending", pendingId, pendingMap)
                outboxId = offline?.enqueueOutbox(ownerUid, "PENDING", "pending", pendingId, pendingMap) ?: 0L
                executeRemoteOrKeepQueued(outboxId) {
                    workspaceCol(ownerUid, "pending").document(pendingId).set(pendingMap, SetOptions.merge()).await()
                    pingWorkspaceOwner(ownerUid, profile)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) Result.success(Unit)
            else {
                offline?.discardOutboxOp(outboxId)
                handleFirestoreError(e, OperationType.DELETE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun restoreFromRecycleBin(profile: UserProfile, tombstone: DeletionTombstone): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val path = "users/$ownerUid/${tombstone.collection}/${tombstone.recordId}"
        return try {
            repo.requireUserId()
            val restoredMap = WorkspaceJsonCodec.jsonToMap(tombstone.snapshotJson).toMutableMap().apply {
                put("id", tombstone.recordId.ifBlank { tombstone.id })
                remove("deletedAt")
            }
            val col = tombstone.collection
            val docId = tombstone.recordId.ifBlank { tombstone.id }
            offline?.upsertLocalCollectionDoc(ownerUid, col, docId, restoredMap)
            offline?.removeLocalDeletionAndOptionallyErase(ownerUid, docId, erasePermanently = false)

            if (repo.isOnline()) {
                withTimeoutOrNull(2500L) {
                    val batch = db.batch()
                    batch.set(
                        workspaceCol(ownerUid, col).document(docId),
                        restoredMap + ("updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    )
                    batch.delete(workspaceCol(ownerUid, "deletions").document(docId))
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun purgeDeletedForever(profile: UserProfile, tombstone: DeletionTombstone): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val docId = tombstone.recordId.ifBlank { tombstone.id }
        val path = "users/$ownerUid/deletions/$docId"
        return try {
            repo.requireUserId()
            offline?.removeLocalDeletionAndOptionallyErase(ownerUid, docId, erasePermanently = true)
            if (repo.isOnline()) {
                withTimeoutOrNull(2500L) {
                    db.batch().apply {
                        delete(workspaceCol(ownerUid, "deletions").document(docId))
                        delete(workspaceCol(ownerUid, tombstone.collection).document(docId))
                    }.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun reviewPendingApproval(
        profile: UserProfile,
        item: PendingApproval,
        approve: Boolean
    ): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val path = "users/$ownerUid/pending/${item.id}"
        return try {
            val uid = repo.requireUserId()
            val nowMs = System.currentTimeMillis()
            if (approve) {
                if (item.action == "delete") {
                    softDeleteToRecycleBin(
                        profile = profile,
                        collection = item.collection,
                        docId = item.docId,
                        displayName = item.summaryTitle,
                        displayDetail = item.summaryBody,
                        displayAmount = "",
                        snapshotMap = item.dataMap
                    )
                } else {
                    val targetMap = item.dataMap.toMutableMap().apply {
                        put("id", item.docId)
                    }
                    offline?.upsertLocalCollectionDoc(ownerUid, item.collection, item.docId, targetMap)
                    if (repo.isOnline()) {
                        workspaceCol(ownerUid, item.collection).document(item.docId)
                            .set(targetMap + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                            .await()
                    } else {
                        offline?.enqueueOutbox(ownerUid, "SET", item.collection, item.docId, targetMap)
                    }
                }
            }

            val nextStatus = if (approve) "approved" else "rejected"
            val updatedPending = WorkspaceJsonCodec.pendingToMap(
                item.copy(
                    status = nextStatus,
                    decidedByUid = uid,
                    decidedByName = profile.displayName.ifBlank { profile.email },
                    decidedAt = nowMs
                )
            )
            offline?.upsertLocalCollectionDoc(ownerUid, "pending", item.id, updatedPending)
            if (repo.isOnline()) {
                workspaceCol(ownerUid, "pending").document(item.id).set(updatedPending, SetOptions.merge()).await()
            } else {
                offline?.enqueueOutbox(ownerUid, "PENDING", "pending", item.id, updatedPending)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun savePhoto(profile: UserProfile, photoKey: String, dataUri: String?): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val deleted = dataUri.isNullOrBlank()
        val encodedId = Base64.encodeToString(
            photoKey.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return try {
            repo.requireUserId()
            offline?.upsertLocalPhoto(ownerUid, photoKey, dataUri, deleted)
            val payload = buildMap<String, Any> {
                put("key", photoKey)
                if (deleted) {
                    put("deleted", true)
                } else {
                    put("data", dataUri!!)
                    put("deleted", false)
                }
            }
            val outboxId = offline?.enqueueOutbox(ownerUid, "PHOTO", "photos", photoKey, payload) ?: 0L
            executeRemoteOrKeepQueued(outboxId) {
                val remote = payload.toMutableMap().apply {
                    if (deleted) put("data", null as Any? ?: "")
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                val docMap = buildMap<String, Any?> {
                    put("key", photoKey)
                    put("data", if (deleted) null else dataUri)
                    put("deleted", deleted)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                workspaceCol(ownerUid, "photos").document(encodedId).set(docMap, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$ownerUid/photos/$encodedId")
            Result.failure(e)
        }
    }

    suspend fun inviteTeamMember(
        profile: UserProfile,
        memberEmail: String,
        displayName: String,
        roles: List<String>
    ): Result<Unit> {
        val ownerUid = profile.effectiveOwnerUid
        val inviteId = newId("inv")
        return try {
            repo.requireUserId()
            val remoteMap = mapOf(
                "ownerUid" to ownerUid,
                "ownerName" to InputGuards.sanitizeName(profile.displayName.ifBlank { "Admin" }),
                "memberEmail" to memberEmail.trim().lowercase().take(160),
                "displayName" to InputGuards.sanitizeName(displayName),
                "roles" to roles.take(10),
                "approved" to true,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspace_invites").document(inviteId).set(remoteMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "workspace_invites/$inviteId")
            Result.failure(e)
        }
    }

    suspend fun revokeInvite(inviteId: String): Result<Unit> = try {
        repo.requireUserId()
        db.collection("workspace_invites").document(inviteId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "workspace_invites/$inviteId")
        Result.failure(e)
    }

    suspend fun updateMemberRoles(
        memberUid: String,
        roles: List<String>,
        approved: Boolean
    ): Result<Unit> = try {
        repo.requireUserId()
        val cleanRoles = roles.take(10)
        val nextRole = if ("admin" in cleanRoles) "admin" else "user"
        db.collection("users").document(memberUid).update(
            mapOf(
                "role" to nextRole,
                "roles" to cleanRoles,
                "approved" to approved,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$memberUid")
        Result.failure(e)
    }
}
