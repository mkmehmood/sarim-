package com.gullzubair.sarim.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.gullzubair.sarim.data.local.OfflineSyncRepository
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class WorkspaceOperations(private val repo: AppRepository) {
    private val db: FirebaseFirestore get() = repo.db
    private val offline: OfflineSyncRepository? get() = repo.offlineRepo

    private fun workspaceCol(ownerUid: String, collection: String) =
        db.collection("users").document(ownerUid).collection(collection)

    private fun isAdmin(profile: UserProfile): Boolean =
        profile.role == "admin" || profile.uid == profile.workspaceOwnerUid

    private fun newId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

    private fun isOfflineFirestoreException(e: Throwable): Boolean {
        val code = (e as? FirebaseFirestoreException)?.code ?: return false
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private suspend fun executeRemoteOrKeepQueued(
        opId: String?,
        remoteCall: suspend () -> Unit
    ) {
        if (!repo.isOnline()) return
        val synced = withTimeoutOrNull(1800L) {
            remoteCall()
            true
        }
        if (synced == true && opId != null) {
            offline?.markOpSynced(opId)
        }
    }

    suspend fun submitOrExecuteWrite(
        profile: UserProfile,
        memberUids: List<String>,
        targetCollection: String,
        docId: String?,
        summaryTitle: String,
        summaryDetail: String,
        recordDate: String,
        payload: Map<String, Any?>,
        isUpdate: Boolean = false
    ): Result<String> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val resolvedId = docId?.takeIf { it.isNotBlank() } ?: newId(targetCollection.take(4))
        val path = "users/$ownerUid/$targetCollection/$resolvedId"
        var opId: String? = null
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            if (isAdmin(profile)) {
                val nowMs = System.currentTimeMillis()
                val queueAndLocalMap = payload.toMutableMap().apply {
                    put("id", resolvedId)
                    put("ownerUid", ownerUid)
                    put("memberUids", cleanMembers)
                    put("updatedAt", nowMs)
                    if (!isUpdate) {
                        put("createdByUid", uid)
                        put("createdAt", nowMs)
                    }
                }.filterValues { it != null }

                val firestoreMap = payload.toMutableMap().apply {
                    put("memberUids", cleanMembers)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (!isUpdate) {
                        put("ownerUid", ownerUid)
                        put("createdByUid", uid)
                        put("createdAt", FieldValue.serverTimestamp())
                    } else {
                        remove("createdAt")
                        remove("createdByUid")
                        remove("ownerUid")
                    }
                }.filterValues { it != null } as Map<String, Any>

                offline?.applyLocalMutation(ownerUid, targetCollection, resolvedId, queueAndLocalMap, isUpdate)
                opId = offline?.enqueueSyncOp(
                    ownerUid = ownerUid,
                    collection = targetCollection,
                    docId = resolvedId,
                    opType = if (isUpdate) "UPDATE" else "SET",
                    payload = queueAndLocalMap - "id"
                )

                val docRef = workspaceCol(ownerUid, targetCollection).document(resolvedId)
                executeRemoteOrKeepQueued(opId) {
                    if (isUpdate) docRef.update(firestoreMap).await() else docRef.set(firestoreMap).await()
                }
                Result.success(resolvedId)
            } else {
                val pendingId = newId("pend")
                val safeData = payload.filterValues { it != null }.mapValues { (_, v) -> v as Any }
                val nowMs = System.currentTimeMillis()
                val localPending = mapOf<String, Any?>(
                    "id" to pendingId,
                    "ownerUid" to ownerUid,
                    "memberUids" to (cleanMembers + uid).distinct().take(20),
                    "submittedByUid" to uid,
                    "submittedByName" to profile.displayName.ifBlank { profile.email },
                    "submittedByEmail" to profile.email,
                    "action" to "set",
                    "targetCollection" to targetCollection,
                    "targetDocId" to resolvedId,
                    "summaryTitle" to summaryTitle.take(120),
                    "summaryDetail" to summaryDetail.take(500),
                    "recordDate" to recordDate.take(20),
                    "payloadData" to safeData,
                    "status" to "pending",
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
                val pendingPayload = localPending.toMutableMap().apply {
                    remove("id")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                } as Map<String, Any>

                offline?.applyLocalMutation(ownerUid, "pending", pendingId, localPending, isUpdate = false)
                opId = offline?.enqueueSyncOp(ownerUid, "pending", pendingId, "SET", localPending - "id")

                executeRemoteOrKeepQueued(opId) {
                    workspaceCol(ownerUid, "pending").document(pendingId).set(pendingPayload).await()
                }
                Result.success("PENDING:$pendingId")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(if (isAdmin(profile)) resolvedId else "PENDING:offline")
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun updateFieldsOfflineSafe(
        ownerUid: String,
        collection: String,
        docId: String,
        fields: Map<String, Any?>
    ): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        var opId: String? = null
        return try {
            repo.requireUserId()
            val cleanFields = fields.filterValues { it != null }
            val localMap = cleanFields.toMutableMap().apply {
                put("updatedAt", System.currentTimeMillis())
            }
            val remoteMap = cleanFields.toMutableMap().apply {
                remove("ownerUid")
                remove("createdAt")
                remove("createdByUid")
                put("updatedAt", FieldValue.serverTimestamp())
            } as Map<String, Any>

            offline?.applyLocalMutation(ownerUid, collection, docId, localMap, isUpdate = true)
            opId = offline?.enqueueSyncOp(ownerUid, collection, docId, "UPDATE", localMap)

            executeRemoteOrKeepQueued(opId) {
                workspaceCol(ownerUid, collection).document(docId).update(remoteMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.UPDATE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun softRecycleRecord(
        profile: UserProfile,
        memberUids: List<String>,
        collection: String,
        docId: String,
        summaryTitle: String
    ): Result<Unit> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val path = "users/$ownerUid/$collection/$docId"
        var opId: String? = null
        return try {
            val uid = repo.requireUserId()
            val nowMs = System.currentTimeMillis()
            if (isAdmin(profile)) {
                val localPatch = mapOf<String, Any?>(
                    "recycled" to true,
                    "recycledAtEpoch" to nowMs,
                    "updatedAt" to nowMs
                )
                offline?.applyLocalMutation(ownerUid, collection, docId, localPatch, isUpdate = true)
                opId = offline?.enqueueSyncOp(ownerUid, collection, docId, "UPDATE", localPatch)

                executeRemoteOrKeepQueued(opId) {
                    workspaceCol(ownerUid, collection).document(docId).update(
                        mapOf(
                            "recycled" to true,
                            "recycledAtEpoch" to nowMs,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val pendingId = newId("pend")
                val localPending = mapOf<String, Any?>(
                    "id" to pendingId,
                    "ownerUid" to ownerUid,
                    "memberUids" to (memberUids + ownerUid + uid).distinct().take(20),
                    "submittedByUid" to uid,
                    "submittedByName" to profile.displayName.ifBlank { profile.email },
                    "submittedByEmail" to profile.email,
                    "action" to "delete",
                    "targetCollection" to collection,
                    "targetDocId" to docId,
                    "summaryTitle" to "Recycle: $summaryTitle".take(120),
                    "summaryDetail" to "Requested move to Recycle Bin",
                    "recordDate" to DateUtils.todayIso(),
                    "payloadData" to mapOf("recycled" to true),
                    "status" to "pending",
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
                offline?.applyLocalMutation(ownerUid, "pending", pendingId, localPending, isUpdate = false)
                opId = offline?.enqueueSyncOp(ownerUid, "pending", pendingId, "SET", localPending - "id")

                val remotePayload = localPending.toMutableMap().apply {
                    remove("id")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                } as Map<String, Any>
                executeRemoteOrKeepQueued(opId) {
                    workspaceCol(ownerUid, "pending").document(pendingId).set(remotePayload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.UPDATE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun restoreFromRecycleBin(ownerUid: String, collection: String, docId: String): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        var opId: String? = null
        return try {
            repo.requireUserId()
            val localPatch = mapOf<String, Any?>(
                "recycled" to false,
                "recycledAtEpoch" to 0L,
                "updatedAt" to System.currentTimeMillis()
            )
            offline?.applyLocalMutation(ownerUid, collection, docId, localPatch, isUpdate = true)
            opId = offline?.enqueueSyncOp(ownerUid, collection, docId, "UPDATE", localPatch)

            executeRemoteOrKeepQueued(opId) {
                workspaceCol(ownerUid, collection).document(docId).update(
                    mapOf(
                        "recycled" to false,
                        "recycledAtEpoch" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.UPDATE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun permanentDelete(ownerUid: String, collection: String, docId: String): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        var opId: String? = null
        return try {
            repo.requireUserId()
            offline?.applyLocalDelete(ownerUid, collection, docId)
            opId = offline?.enqueueSyncOp(ownerUid, collection, docId, "DELETE")

            executeRemoteOrKeepQueued(opId) {
                workspaceCol(ownerUid, collection).document(docId).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.DELETE, path)
                Result.failure(e)
            }
        }
    }

    suspend fun reviewPendingApproval(
        profile: UserProfile,
        memberUids: List<String>,
        item: PendingApproval,
        approve: Boolean
    ): Result<Unit> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val pendingPath = "users/$ownerUid/pending/${item.id}"
        var targetOpId: String? = null
        var pendingOpId: String? = null
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            val nowMs = System.currentTimeMillis()
            if (approve) {
                val targetRef = workspaceCol(ownerUid, item.targetCollection).document(item.targetDocId)
                if (item.action == "delete") {
                    val recyclePatch = mapOf<String, Any?>(
                        "recycled" to true,
                        "recycledAtEpoch" to nowMs,
                        "updatedAt" to nowMs
                    )
                    offline?.applyLocalMutation(ownerUid, item.targetCollection, item.targetDocId, recyclePatch, isUpdate = true)
                    targetOpId = offline?.enqueueSyncOp(ownerUid, item.targetCollection, item.targetDocId, "UPDATE", recyclePatch)
                    executeRemoteOrKeepQueued(targetOpId) {
                        targetRef.update(
                            mapOf(
                                "recycled" to true,
                                "recycledAtEpoch" to nowMs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                } else {
                    val localTarget = item.payloadData.toMutableMap().apply {
                        put("id", item.targetDocId)
                        put("ownerUid", ownerUid)
                        put("memberUids", cleanMembers)
                        put("createdByUid", item.submittedByUid)
                        put("createdAt", nowMs)
                        put("updatedAt", nowMs)
                    }
                    offline?.applyLocalMutation(ownerUid, item.targetCollection, item.targetDocId, localTarget, isUpdate = false)
                    targetOpId = offline?.enqueueSyncOp(ownerUid, item.targetCollection, item.targetDocId, "SET", localTarget - "id")

                    val merged = item.payloadData.toMutableMap().apply {
                        put("ownerUid", ownerUid)
                        put("memberUids", cleanMembers)
                        put("createdByUid", item.submittedByUid)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    executeRemoteOrKeepQueued(targetOpId) {
                        targetRef.set(merged).await()
                    }
                }
            }
            val nextStatus = if (approve) "approved" else "rejected"
            val pendingPatch = mapOf<String, Any?>(
                "status" to nextStatus,
                "reviewedByUid" to uid,
                "updatedAt" to nowMs
            )
            offline?.applyLocalMutation(ownerUid, "pending", item.id, pendingPatch, isUpdate = true)
            pendingOpId = offline?.enqueueSyncOp(ownerUid, "pending", item.id, "UPDATE", pendingPatch)

            executeRemoteOrKeepQueued(pendingOpId) {
                workspaceCol(ownerUid, "pending").document(item.id).update(
                    mapOf(
                        "status" to nextStatus,
                        "reviewedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                targetOpId?.let { offline?.discardSyncOp(it) }
                pendingOpId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.UPDATE, pendingPath)
                Result.failure(e)
            }
        }
    }

    suspend fun saveLedgerMirror(
        profile: UserProfile,
        memberUids: List<String>,
        date: String,
        entryType: String,
        expenseScope: String,
        channel: String,
        category: String,
        partyName: String,
        amount: Double,
        sourceCollection: String,
        sourceDocId: String,
        notes: String
    ): Result<String> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val ledgerId = newId("ldg")
        var opId: String? = null
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            val nowMs = System.currentTimeMillis()
            val localMap = mapOf<String, Any?>(
                "id" to ledgerId,
                "ownerUid" to ownerUid,
                "memberUids" to cleanMembers,
                "date" to date,
                "entryType" to entryType,
                "expenseScope" to expenseScope,
                "channel" to channel,
                "toChannel" to "",
                "category" to category.take(60),
                "partyName" to InputGuards.sanitizeName(partyName),
                "amount" to InputGuards.round2(amount),
                "tabEntry" to true,
                "sourceCollection" to sourceCollection,
                "sourceDocId" to sourceDocId,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L,
                "createdByUid" to uid,
                "createdAt" to nowMs,
                "updatedAt" to nowMs
            )
            offline?.applyLocalMutation(ownerUid, "ledger_entries", ledgerId, localMap, isUpdate = false)
            opId = offline?.enqueueSyncOp(ownerUid, "ledger_entries", ledgerId, "SET", localMap - "id")

            val remoteMap = localMap.toMutableMap().apply {
                remove("id")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            } as Map<String, Any>
            executeRemoteOrKeepQueued(opId) {
                workspaceCol(ownerUid, "ledger_entries").document(ledgerId).set(remoteMap).await()
            }
            Result.success(ledgerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(ledgerId)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.CREATE, "users/$ownerUid/ledger_entries/$ledgerId")
                Result.failure(e)
            }
        }
    }

    suspend fun inviteTeamMember(
        profile: UserProfile,
        memberEmail: String,
        displayName: String,
        roles: List<String>
    ): Result<Unit> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val inviteId = newId("inv")
        var opId: String? = null
        return try {
            repo.requireUserId()
            val nowMs = System.currentTimeMillis()
            val localMap = mapOf<String, Any?>(
                "id" to inviteId,
                "ownerUid" to ownerUid,
                "ownerName" to InputGuards.sanitizeName(profile.displayName.ifBlank { "Admin" }),
                "memberEmail" to memberEmail.trim().lowercase().take(160),
                "displayName" to InputGuards.sanitizeName(displayName),
                "roles" to roles.take(8),
                "approved" to true,
                "status" to "pending",
                "createdAt" to nowMs,
                "updatedAt" to nowMs
            )
            offline?.applyLocalMutation(ownerUid, "workspace_invites", inviteId, localMap, isUpdate = false)
            opId = offline?.enqueueSyncOp(ownerUid, "workspace_invites", inviteId, "INVITE", localMap - "id")

            val remoteMap = localMap.toMutableMap().apply {
                remove("id")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            } as Map<String, Any>
            executeRemoteOrKeepQueued(opId) {
                db.collection("workspace_invites").document(inviteId).set(remoteMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.CREATE, "workspace_invites/$inviteId")
                Result.failure(e)
            }
        }
    }

    suspend fun revokeInvite(ownerUid: String, inviteId: String): Result<Unit> {
        var opId: String? = null
        return try {
            repo.requireUserId()
            offline?.applyLocalDelete(ownerUid, "workspace_invites", inviteId)
            opId = offline?.enqueueSyncOp(ownerUid, "workspace_invites", inviteId, "REVOKE_INVITE")

            executeRemoteOrKeepQueued(opId) {
                db.collection("workspace_invites").document(inviteId).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.DELETE, "workspace_invites/$inviteId")
                Result.failure(e)
            }
        }
    }

    suspend fun updateMemberRoles(
        memberUid: String,
        roles: List<String>,
        approved: Boolean,
        ownerUid: String = ""
    ): Result<Unit> {
        var opId: String? = null
        return try {
            val resolvedOwner = ownerUid.ifBlank { repo.requireUserId() }
            repo.requireUserId()
            val cleanRoles = roles.take(8)
            val nextRole = if ("admin" in cleanRoles) "admin" else "member"
            val nowMs = System.currentTimeMillis()
            val localPatch = mapOf<String, Any?>(
                "role" to nextRole,
                "roles" to cleanRoles,
                "approved" to approved,
                "updatedAt" to nowMs
            )
            offline?.applyLocalMutation(resolvedOwner, "team_members", memberUid, localPatch, isUpdate = true)
            opId = offline?.enqueueSyncOp(resolvedOwner, "team_members", memberUid, "MEMBER_ROLE", localPatch)

            executeRemoteOrKeepQueued(opId) {
                db.collection("users").document(memberUid).update(
                    mapOf(
                        "role" to nextRole,
                        "roles" to cleanRoles,
                        "approved" to approved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineFirestoreException(e)) {
                Result.success(Unit)
            } else {
                opId?.let { offline?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.UPDATE, "users/$memberUid")
                Result.failure(e)
            }
        }
    }
}
