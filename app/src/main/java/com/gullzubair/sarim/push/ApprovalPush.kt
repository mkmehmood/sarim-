package com.gullzubair.sarim.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gullzubair.sarim.MainActivity
import com.gullzubair.sarim.R
import com.gullzubair.sarim.data.firebase.isFirebaseConfigured
import com.gullzubair.sarim.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Approval push notifications. Mirrors the web app's `modules/push.js`:
 *
 *  - Admins register their FCM token at `users/{owner}/fcm_tokens/{uid}_{deviceId}`.
 *  - When a non-admin submits something for approval, the submitter's device POSTs the
 *    pending id to the `sarim-push` Cloudflare Worker, which verifies the submission and
 *    sends an FCM message to every other admin device in the workspace.
 *  - Tapping the notification opens the Approvals screen.
 */
object ApprovalPush {
    private const val TAG = "ApprovalPush"

    /** Same endpoint the web app uses (PUSH_WORKER_URL). */
    const val WORKER_URL = "https://sarim-push.3mahmoodkhan.workers.dev/notify"

    const val CHANNEL_ID = "approvals"
    const val EXTRA_TYPE = "type"
    const val EXTRA_PENDING_ID = "pendingId"
    const val TYPE_APPROVAL = "approval"

    private const val PREFS = "sarim_push_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_OWNER_UID = "owner_uid"
    private const val KEY_UID = "uid"
    private const val KEY_DOC_ID = "doc_id"

    private const val DEDUPE_WINDOW_MS = 15_000L
    private val RETRY_DELAYS_MS = longArrayOf(0L, 3_000L, 8_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentNotifies = HashMap<String, Long>()

    // ---- Tap-to-open ------------------------------------------------------------------

    data class OpenRequest(val pendingId: String?, val nonce: Long = System.nanoTime())

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest

    /** Called from MainActivity when it is launched/resumed from an approval notification. */
    fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_TYPE) != TYPE_APPROVAL) return
        _openRequest.value = OpenRequest(intent.getStringExtra(EXTRA_PENDING_ID))
        intent.removeExtra(EXTRA_TYPE)
        intent.removeExtra(EXTRA_PENDING_ID)
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    // ---- Channel & permission ---------------------------------------------------------

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Approvals", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Items waiting for your approval"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // ---- Token registration (admins only) ---------------------------------------------

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stable per-install id; only [a-zA-Z0-9] so the token doc id satisfies the Firestore rules. */
    private fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "").take(32)
        p.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun firestore(context: Context): FirebaseFirestore =
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))

    /**
     * Registers this device for approval pushes. No-op unless the user is an approved admin,
     * Firebase is configured and notifications are permitted.
     */
    suspend fun register(context: Context, profile: UserProfile) {
        val app = context.applicationContext
        if (!isFirebaseConfigured(app) || !profile.isAdmin || !profile.approved) return
        if (Firebase.auth.currentUser?.uid != profile.uid) return
        ensureChannel(app)
        if (!hasNotificationPermission(app)) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            saveToken(app, profile.effectiveOwnerUid, profile.uid, token)
        }.onFailure { Log.w(TAG, "Could not register for push: ${it.message}") }
    }

    private suspend fun saveToken(context: Context, ownerUid: String, uid: String, token: String) {
        val docId = "${uid}_${deviceId(context)}"
        // The Firestore rules only allow exactly these four fields.
        firestore(context).collection("users").document(ownerUid)
            .collection("fcm_tokens").document(docId)
            .set(
                mapOf(
                    "uid" to uid,
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        prefs(context).edit()
            .putString(KEY_OWNER_UID, ownerUid)
            .putString(KEY_UID, uid)
            .putString(KEY_DOC_ID, docId)
            .apply()
    }

    /** FCM rotated the token: re-save it if this device is currently registered. */
    fun onNewToken(context: Context, token: String) {
        val app = context.applicationContext
        val p = prefs(app)
        val owner = p.getString(KEY_OWNER_UID, null) ?: return
        val uid = p.getString(KEY_UID, null) ?: return
        if (!isFirebaseConfigured(app) || Firebase.auth.currentUser?.uid != uid) return
        scope.launch {
            runCatching { saveToken(app, owner, uid, token) }
                .onFailure { Log.w(TAG, "Could not save refreshed token: ${it.message}") }
        }
    }

    /**
     * Removes this device's token. Call before signing out (rules need the user's auth to
     * delete the doc) so a signed-out phone stops receiving approval alerts.
     */
    suspend fun unregister(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val owner = p.getString(KEY_OWNER_UID, null)
        val docId = p.getString(KEY_DOC_ID, null)
        p.edit().remove(KEY_OWNER_UID).remove(KEY_UID).remove(KEY_DOC_ID).apply()
        if (!isFirebaseConfigured(app)) return
        if (owner != null && docId != null) {
            withTimeoutOrNull(2_500L) {
                runCatching {
                    firestore(app).collection("users").document(owner)
                        .collection("fcm_tokens").document(docId).delete().await()
                }
            }
        }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    // ---- Notify admins (non-admin submitters) -----------------------------------------

    /**
     * Fire-and-forget: asks the Worker to push to the workspace admins about [pendingId].
     * Safe to call from anywhere; never throws. The Worker re-verifies that the pending doc
     * exists and belongs to the caller, and de-duplicates per item.
     */
    fun notifyAdmins(pendingId: String) {
        if (pendingId.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(recentNotifies) {
            recentNotifies.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
            if (recentNotifies.containsKey(pendingId)) return
            recentNotifies[pendingId] = now
        }
        scope.launch {
            runCatching {
                for (wait in RETRY_DELAYS_MS) {
                    if (wait > 0) delay(wait)
                    val user = Firebase.auth.currentUser ?: return@launch
                    val idToken = user.getIdToken(false).await().token ?: return@launch
                    val status = postNotify(idToken, pendingId)
                    // 403 right after submit usually means the pending doc isn't visible yet.
                    if (status != 403) return@launch
                }
            }.onFailure { Log.w(TAG, "notify failed: ${it.message}") }
        }
    }

    private suspend fun postNotify(idToken: String, pendingId: String): Int = withContext(Dispatchers.IO) {
        val conn = (URL(WORKER_URL).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("pendingId", pendingId).toString().toByteArray()) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    // ---- Display (app in foreground) ---------------------------------------------------

    /**
     * FCM shows the notification itself when the app is in the background; when it is in the
     * foreground FirebaseMessagingService.onMessageReceived is called instead and we show it.
     * The tag matches the Worker's (`approval-<id>`) so repeats replace rather than stack.
     */
    fun showApprovalNotification(context: Context, pendingId: String?, title: String, body: String) {
        val app = context.applicationContext
        if (!hasNotificationPermission(app)) return
        ensureChannel(app)
        val tapIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TYPE, TYPE_APPROVAL)
            pendingId?.let { putExtra(EXTRA_PENDING_ID, it) }
        }
        val requestCode = (pendingId ?: "approval").hashCode()
        val pi = PendingIntent.getActivity(
            app, requestCode, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            NotificationManagerCompat.from(app).notify("approval-${pendingId ?: "new"}", 1001, notification)
        }
    }
}
