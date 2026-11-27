package com.gullzubair.sarim.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SarimMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        ApprovalPush.onNewToken(applicationContext, token)
    }

    /** Only reached while the app is in the foreground (the system shows it otherwise). */
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data[ApprovalPush.EXTRA_TYPE] != ApprovalPush.TYPE_APPROVAL) return
        val n = message.notification
        ApprovalPush.showApprovalNotification(
            context = applicationContext,
            pendingId = message.data[ApprovalPush.EXTRA_PENDING_ID],
            title = n?.title ?: "Approval needed",
            body = n?.body ?: "Something is waiting for your approval"
        )
    }
}
