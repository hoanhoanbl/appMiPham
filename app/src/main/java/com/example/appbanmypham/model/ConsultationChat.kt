package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot

data class ConsultationChatThread(
    val id: String = "",
    val appointmentId: String = "",
    val treatmentPlanId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val consultantId: String = "",
    val consultantEmail: String = "",
    val consultantName: String = "",
    val status: String = ChatThreadStatus.ACTIVE,
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object ChatThreadStatus {
    const val ACTIVE = "active"
    const val CLOSED = "closed"
}

data class ConsultationChatMessage(
    val id: String = "",
    val threadId: String = "",
    val appointmentId: String = "",
    val treatmentPlanId: String = "",
    val userId: String = "",
    val consultantId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object ChatSenderRole {
    const val CUSTOMER = "customer"
    const val CONSULTANT = "consultant"
    const val ADMIN = "admin"
}

fun firestoreDocToConsultationChatThread(doc: DocumentSnapshot): ConsultationChatThread {
    val now = System.currentTimeMillis()
    return ConsultationChatThread(
        id = doc.id,
        appointmentId = doc.getString("appointmentId") ?: "",
        treatmentPlanId = doc.getString("treatmentPlanId") ?: "",
        userId = doc.getString("userId") ?: "",
        userEmail = doc.getString("userEmail") ?: "",
        userName = doc.getString("userName") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        consultantEmail = doc.getString("consultantEmail") ?: "",
        consultantName = doc.getString("consultantName") ?: "",
        status = doc.getString("status") ?: ChatThreadStatus.ACTIVE,
        lastMessage = doc.getString("lastMessage") ?: "",
        lastMessageAt = doc.getLong("lastMessageAt") ?: 0L,
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now
    )
}

fun ConsultationChatThread.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "appointmentId" to appointmentId,
        "treatmentPlanId" to treatmentPlanId,
        "userId" to userId,
        "userEmail" to userEmail,
        "userName" to userName,
        "consultantId" to consultantId,
        "consultantEmail" to consultantEmail,
        "consultantName" to consultantName,
        "status" to status,
        "lastMessage" to lastMessage,
        "lastMessageAt" to lastMessageAt,
        "updatedAt" to now
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}

fun firestoreDocToConsultationChatMessage(doc: DocumentSnapshot): ConsultationChatMessage {
    val now = System.currentTimeMillis()
    return ConsultationChatMessage(
        id = doc.id,
        threadId = doc.getString("threadId") ?: "",
        appointmentId = doc.getString("appointmentId") ?: "",
        treatmentPlanId = doc.getString("treatmentPlanId") ?: "",
        userId = doc.getString("userId") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        senderId = doc.getString("senderId") ?: "",
        senderName = doc.getString("senderName") ?: "",
        senderRole = doc.getString("senderRole") ?: "",
        message = doc.getString("message") ?: "",
        createdAt = doc.getLong("createdAt") ?: now
    )
}

fun ConsultationChatMessage.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "threadId" to threadId,
        "appointmentId" to appointmentId,
        "treatmentPlanId" to treatmentPlanId,
        "userId" to userId,
        "consultantId" to consultantId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderRole" to senderRole,
        "message" to message
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}
