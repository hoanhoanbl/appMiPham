package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot

data class TreatmentSession(
    val id: String = "",
    val treatmentPlanId: String = "",
    val appointmentId: String = "",
    val userId: String = "",
    val consultantId: String = "",
    val spaPackageId: String = "",
    val packageName: String = "",
    val sessionNumber: Int = 1,
    val totalSessions: Int = 1,
    val scheduledStartAt: Long = 0L,
    val scheduledEndAt: Long = 0L,
    val dateLabel: String = "",
    val timeSlotLabel: String = "",
    val status: String = TreatmentSessionStatus.SCHEDULED,
    val requiresProgressPhotos: Boolean = false,
    val photoPolicy: String = ProgressPhotoPolicy.NONE,
    val photoSkipReason: String = "",
    val note: String = "",
    val completedAt: Long = 0L,
    val completedBy: String = "",
    val cancelledAt: Long = 0L,
    val cancelledBy: String = "",
    val cancelReason: String = "",
    val noShowAt: Long = 0L,
    val noShowBy: String = "",
    val noShowNote: String = "",
    val rescheduledAt: Long = 0L,
    val rescheduledBy: String = "",
    val rescheduleReason: String = "",
    val previousScheduledStartAt: Long = 0L,
    val previousScheduledEndAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object TreatmentSessionStatus {
    const val SCHEDULED = "scheduled"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
    const val NO_SHOW = "no_show"
    const val RESCHEDULED = "rescheduled"
}

data class TreatmentSessionStatusMeta(
    val key: String,
    val label: String,
    val description: String
)

val TREATMENT_SESSION_STATUSES = listOf(
    TreatmentSessionStatusMeta(TreatmentSessionStatus.SCHEDULED, "Da len lich", "Buoi dieu tri dang cho thuc hien"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.COMPLETED, "Hoan thanh", "Buoi dieu tri da hoan thanh"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.CANCELLED, "Da huy", "Buoi dieu tri da huy"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.NO_SHOW, "Khach khong den", "Khach vang mat, khong tru buoi trong MVP"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.RESCHEDULED, "Da doi lich", "Buoi dieu tri da duoc hen lai")
)

fun treatmentSessionStatusMeta(status: String): TreatmentSessionStatusMeta =
    TREATMENT_SESSION_STATUSES.find { it.key == status }
        ?: TREATMENT_SESSION_STATUSES.first()

fun firestoreDocToTreatmentSession(doc: DocumentSnapshot): TreatmentSession {
    val now = System.currentTimeMillis()
    return TreatmentSession(
        id = doc.id,
        treatmentPlanId = doc.getString("treatmentPlanId") ?: "",
        appointmentId = doc.getString("appointmentId") ?: "",
        userId = doc.getString("userId") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        spaPackageId = doc.getString("spaPackageId") ?: "",
        packageName = doc.getString("packageName") ?: "",
        sessionNumber = (doc.getLong("sessionNumber") ?: 1L).toInt().coerceAtLeast(1),
        totalSessions = (doc.getLong("totalSessions") ?: 1L).toInt().coerceAtLeast(1),
        scheduledStartAt = doc.getLong("scheduledStartAt") ?: 0L,
        scheduledEndAt = doc.getLong("scheduledEndAt") ?: 0L,
        dateLabel = doc.getString("dateLabel") ?: "",
        timeSlotLabel = doc.getString("timeSlotLabel") ?: "",
        status = doc.getString("status") ?: TreatmentSessionStatus.SCHEDULED,
        requiresProgressPhotos = doc.getBoolean("requiresProgressPhotos") == true,
        photoPolicy = doc.getString("photoPolicy") ?: ProgressPhotoPolicy.NONE,
        photoSkipReason = doc.getString("photoSkipReason") ?: "",
        note = doc.getString("note") ?: "",
        completedAt = doc.getLong("completedAt") ?: 0L,
        completedBy = doc.getString("completedBy") ?: "",
        cancelledAt = doc.getLong("cancelledAt") ?: 0L,
        cancelledBy = doc.getString("cancelledBy") ?: "",
        cancelReason = doc.getString("cancelReason") ?: "",
        noShowAt = doc.getLong("noShowAt") ?: 0L,
        noShowBy = doc.getString("noShowBy") ?: "",
        noShowNote = doc.getString("noShowNote") ?: "",
        rescheduledAt = doc.getLong("rescheduledAt") ?: 0L,
        rescheduledBy = doc.getString("rescheduledBy") ?: "",
        rescheduleReason = doc.getString("rescheduleReason") ?: "",
        previousScheduledStartAt = doc.getLong("previousScheduledStartAt") ?: 0L,
        previousScheduledEndAt = doc.getLong("previousScheduledEndAt") ?: 0L,
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now
    )
}

fun TreatmentSession.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "treatmentPlanId" to treatmentPlanId,
        "appointmentId" to appointmentId,
        "userId" to userId,
        "consultantId" to consultantId,
        "spaPackageId" to spaPackageId,
        "packageName" to packageName,
        "sessionNumber" to sessionNumber.coerceAtLeast(1),
        "totalSessions" to totalSessions.coerceAtLeast(1),
        "scheduledStartAt" to scheduledStartAt,
        "scheduledEndAt" to scheduledEndAt,
        "dateLabel" to dateLabel,
        "timeSlotLabel" to timeSlotLabel,
        "status" to status,
        "requiresProgressPhotos" to requiresProgressPhotos,
        "photoPolicy" to photoPolicy,
        "photoSkipReason" to photoSkipReason,
        "note" to note,
        "completedAt" to completedAt,
        "completedBy" to completedBy,
        "cancelledAt" to cancelledAt,
        "cancelledBy" to cancelledBy,
        "cancelReason" to cancelReason,
        "noShowAt" to noShowAt,
        "noShowBy" to noShowBy,
        "noShowNote" to noShowNote,
        "rescheduledAt" to rescheduledAt,
        "rescheduledBy" to rescheduledBy,
        "rescheduleReason" to rescheduleReason,
        "previousScheduledStartAt" to previousScheduledStartAt,
        "previousScheduledEndAt" to previousScheduledEndAt,
        "updatedAt" to now
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}
