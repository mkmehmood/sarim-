# Approval push notifications

Parity with the web app's `modules/push.js` + `worker/` (sarim-push Cloudflare Worker).

## Flow
1. **Admin devices register**: once an approved admin is signed in (and, on Android 13+, grants
   the notification permission), `ApprovalPush.register` saves the FCM token to
   `users/{owner}/fcm_tokens/{uid}_{deviceId}` (fields: `uid`, `token`, `platform`, `updatedAt`
   - exactly what `firestore.rules` allows).
2. **Member submits for approval**: after the `pending/{id}` doc is written
   (`WorkspaceOperations`, or later by the offline outbox replay in `OfflineSyncRepository`),
   `ApprovalPush.notifyAdmins(id)` POSTs `{pendingId}` + the Firebase ID token to the Worker.
   The Worker verifies the submission and pushes to every *other* admin device.
3. **Tap**: the notification carries `type=approval` + `pendingId`; `MainActivity` hands it to
   `ApprovalPush`, and `WorkspaceNavContainer` navigates to the Approvals screen.
4. **Sign-out** deletes the device's token doc and the FCM token (`onBeforeFirebaseSignOut`).

## Setup requirements
- `app/google-services.json` must belong to the **same Firebase project** as the Worker's
  `PROJECT_ID` (see `worker/wrangler.toml` in the web repo), with this app's package registered.
- Worker URL is `ApprovalPush.WORKER_URL` (same as `PUSH_WORKER_URL` on the web).
- Android 13+ shows a one-time permission prompt for admins.

## Known limits
- Tapping opens the Approvals list; it does not scroll to/highlight the specific `pendingId`.
- Not compiled/tested in CI yet - verify on a device with two accounts (admin + member).
