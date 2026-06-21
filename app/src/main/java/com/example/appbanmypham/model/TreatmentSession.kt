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
    const val UNSCHEDULED = "unscheduled"
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
    TreatmentSessionStatusMeta(TreatmentSessionStatus.UNSCHEDULED, "Chờ đặt lịch", "Buổi điều trị đã có trong gói nhưng chưa chọn ngày giờ"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.SCHEDULED, "Đã lên lịch", "Buổi điều trị đang chờ thực hiện"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.COMPLETED, "Hoàn thành", "Buổi điều trị đã hoàn thành"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.CANCELLED, "Đã hủy", "Buổi điều trị đã hủy"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.NO_SHOW, "Khách không đến", "Khách vắng mặt, không trừ buổi trong MVP"),
    TreatmentSessionStatusMeta(TreatmentSessionStatus.RESCHEDULED, "Đã đổi lịch", "Buổi điều trị đã được hẹn lại")
)

fun treatmentSessionStatusMeta(status: String): TreatmentSessionStatusMeta =
    TREATMENT_SESSION_STATUSES.find { it.key == status }
        ?: TREATMENT_SESSION_STATUSES.first()

fun firestoreDocToTreatmentSession(doc: DocumentSnapshot): TreatmentSession {
    val now = System.currentTimeMillis()
    val scheduledStartAt = doc.getLong("scheduledStartAt") ?: 0L
    val scheduledEndAt = doc.getLong("scheduledEndAt") ?: 0L
    val timeSlotLabel = doc.getString("timeSlotLabel") ?: ""
    val rawStatus = doc.getString("status") ?: TreatmentSessionStatus.SCHEDULED
    val normalizedStatus =
        if (rawStatus in setOf(TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED) &&
            scheduledStartAt == 0L &&
            timeSlotLabel.isBlank()
        ) {
            TreatmentSessionStatus.UNSCHEDULED
        } else {
            rawStatus
        }
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
        scheduledStartAt = scheduledStartAt,
        scheduledEndAt = scheduledEndAt,
        dateLabel = doc.getString("dateLabel") ?: "",
        timeSlotLabel = timeSlotLabel,
        status = normalizedStatus,
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
