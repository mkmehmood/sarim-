package com.gullzubair.sarim.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WorkspaceOperations(private val repo: AppRepository) {
    private val db: FirebaseFirestore get() = repo.db

    private fun workspaceCol(ownerUid: String, collection: String) =
        db.collection("users").document(ownerUid).collection(collection)

    private fun isAdmin(profile: UserProfile): Boolean =
        profile.role == "admin" || profile.uid == profile.workspaceOwnerUid

    private fun newId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

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
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            if (isAdmin(profile)) {
                val finalMap = payload.toMutableMap().apply {
                    put("ownerUid", ownerUid)
                    put("memberUids", cleanMembers)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (!isUpdate) {
                        put("createdByUid", uid)
                        put("createdAt", FieldValue.serverTimestamp())
                    }
                }.filterValues { it != null } as Map<String, Any>

                val docRef = workspaceCol(ownerUid, targetCollection).document(resolvedId)
                if (isUpdate) docRef.update(finalMap).await() else docRef.set(finalMap).await()
                Result.success(resolvedId)
            } else {
                val pendingId = newId("pend")
                val safeData = payload.filterValues { it != null }.mapValues { (_, v) -> v as Any }
                val pendingPayload = mapOf(
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                workspaceCol(ownerUid, "pending").document(pendingId).set(pendingPayload).await()
                Result.success("PENDING:$pendingId")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
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
        return try {
            val uid = repo.requireUserId()
            if (isAdmin(profile)) {
                workspaceCol(ownerUid, collection).document(docId).update(
                    mapOf(
                        "recycled" to true,
                        "recycledAtEpoch" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val pendingId = newId("pend")
                workspaceCol(ownerUid, "pending").document(pendingId).set(
                    mapOf(
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
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromRecycleBin(ownerUid: String, collection: String, docId: String): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        return try {
            repo.requireUserId()
            workspaceCol(ownerUid, collection).document(docId).update(
                mapOf(
                    "recycled" to false,
                    "recycledAtEpoch" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun permanentDelete(ownerUid: String, collection: String, docId: String): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        return try {
            repo.requireUserId()
            workspaceCol(ownerUid, collection).document(docId).delete().await()
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
        memberUids: List<String>,
        item: PendingApproval,
        approve: Boolean
    ): Result<Unit> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val pendingPath = "users/$ownerUid/pending/${item.id}"
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            if (approve) {
                val targetRef = workspaceCol(ownerUid, item.targetCollection).document(item.targetDocId)
                if (item.action == "delete") {
                    targetRef.update(
                        mapOf(
                            "recycled" to true,
                            "recycledAtEpoch" to System.currentTimeMillis(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    val merged = item.payloadData.toMutableMap().apply {
                        put("ownerUid", ownerUid)
                        put("memberUids", cleanMembers)
                        put("createdByUid", item.submittedByUid)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    targetRef.set(merged).await()
                }
            }
            workspaceCol(ownerUid, "pending").document(item.id).update(
                mapOf(
                    "status" to if (approve) "approved" else "rejected",
                    "reviewedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pendingPath)
            Result.failure(e)
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
        return try {
            val uid = repo.requireUserId()
            val cleanMembers = (memberUids + ownerUid).distinct().take(20)
            workspaceCol(ownerUid, "ledger_entries").document(ledgerId).set(
                mapOf(
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ledgerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$ownerUid/ledger_entries/$ledgerId")
            Result.failure(e)
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
        return try {
            repo.requireUserId()
            db.collection("workspace_invites").document(inviteId).set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "ownerName" to InputGuards.sanitizeName(profile.displayName.ifBlank { "Admin" }),
                    "memberEmail" to memberEmail.trim().lowercase().take(160),
                    "displayName" to InputGuards.sanitizeName(displayName),
                    "roles" to roles.take(8),
                    "approved" to true,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "workspace_invites/$inviteId")
            Result.failure(e)
        }
    }

    suspend fun updateMemberRoles(
        memberUid: String,
        roles: List<String>,
        approved: Boolean
    ): Result<Unit> {
        return try {
            repo.requireUserId()
            db.collection("users").document(memberUid).update(
                mapOf(
                    "role" to if ("admin" in roles) "admin" else "member",
                    "roles" to roles.take(8),
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
}
