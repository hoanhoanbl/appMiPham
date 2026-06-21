package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot

data class TreatmentPlan(
    val id: String = "",
    val appointmentId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val phoneNumber: String = "",
    val consultantId: String = "",
    val consultantEmail: String = "",
    val consultantName: String = "",
    val spaPackageId: String = "",
    val packageName: String = "",
    val packageImageUrl: String = "",
    val packageType: String = SpaPackageType.TREATMENT_TEMPLATE,
    val category: String = "",
    val totalPrice: Double = 0.0,
    val sessionCount: Int = 1,
    val completedSessionCount: Int = 0,
    val durationPerSessionMinutes: Int = 0,
    val suggestedIntervalDays: Int = 7,
    val requiresProgressPhotos: Boolean = false,
    val photoPolicy: String = ProgressPhotoPolicy.NONE,
    val photoGuide: String = "",
    val status: String = TreatmentPlanStatus.ACTIVE,
    val consultationNote: String = "",
    val recommendationNote: String = "",
    val internalNote: String = "",
    val chatThreadId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val cancelReason: String = ""
)

object TreatmentPlanStatus {
    const val DRAFT = "draft"
    const val WAITING_CONSULTANT = "waiting_consultant"
    const val ACTIVE = "active"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"

    val openStatuses = setOf(DRAFT, WAITING_CONSULTANT, ACTIVE)
}

const val ACTIVE_TREATMENT_PLAN_KEYS_COLLECTION = "active_treatment_plan_keys"

fun activeTreatmentPlanKey(userId: String, spaPackageId: String): String =
    "${userId}_${spaPackageId}".replace("/", "_")

data class TreatmentPlanStatusMeta(
    val key: String,
    val label: String,
    val description: String
)

val TREATMENT_PLAN_STATUSES = listOf(
    TreatmentPlanStatusMeta(TreatmentPlanStatus.DRAFT, "Bản nháp", "Kế hoạch đang được tư vấn viên chuẩn bị"),
    TreatmentPlanStatusMeta(TreatmentPlanStatus.WAITING_CONSULTANT, "Chờ tư vấn viên", "Liệu trình đã tạo từ gói spa và đang chờ tư vấn viên nhận lịch"),
    TreatmentPlanStatusMeta(TreatmentPlanStatus.ACTIVE, "Đang điều trị", "Liệu trình đang diễn ra"),
    TreatmentPlanStatusMeta(TreatmentPlanStatus.COMPLETED, "Đã hoàn thành", "Tất cả buổi cần thiết đã hoàn thành"),
    TreatmentPlanStatusMeta(TreatmentPlanStatus.CANCELLED, "Đã hủy", "Liệu trình đã dừng hoặc hủy")
)

fun treatmentPlanStatusMeta(status: String): TreatmentPlanStatusMeta =
    TREATMENT_PLAN_STATUSES.find { it.key == status }
        ?: TREATMENT_PLAN_STATUSES.first()

fun firestoreDocToTreatmentPlan(doc: DocumentSnapshot): TreatmentPlan {
    val now = System.currentTimeMillis()
    return TreatmentPlan(
        id = doc.id,
        appointmentId = doc.getString("appointmentId") ?: "",
        userId = doc.getString("userId") ?: "",
        userEmail = doc.getString("userEmail") ?: "",
        userName = doc.getString("userName") ?: "",
        phoneNumber = doc.getString("phoneNumber") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        consultantEmail = doc.getString("consultantEmail") ?: "",
        consultantName = doc.getString("consultantName") ?: "",
        spaPackageId = doc.getString("spaPackageId") ?: "",
        packageName = doc.getString("packageName") ?: "",
        packageImageUrl = doc.getString("packageImageUrl") ?: "",
        packageType = doc.getString("packageType") ?: SpaPackageType.TREATMENT_TEMPLATE,
        category = doc.getString("category") ?: "",
        totalPrice = doc.getDouble("totalPrice") ?: 0.0,
        sessionCount = (doc.getLong("sessionCount") ?: 1L).toInt().coerceAtLeast(1),
        completedSessionCount = (doc.getLong("completedSessionCount") ?: 0L).toInt().coerceAtLeast(0),
        durationPerSessionMinutes = (doc.getLong("durationPerSessionMinutes") ?: 0L).toInt().coerceAtLeast(0),
        suggestedIntervalDays = (doc.getLong("suggestedIntervalDays") ?: 7L).toInt().coerceAtLeast(0),
        requiresProgressPhotos = doc.getBoolean("requiresProgressPhotos") == true,
        photoPolicy = doc.getString("photoPolicy") ?: ProgressPhotoPolicy.NONE,
        photoGuide = doc.getString("photoGuide") ?: "",
        status = doc.getString("status") ?: TreatmentPlanStatus.ACTIVE,
        consultationNote = doc.getString("consultationNote") ?: "",
        recommendationNote = doc.getString("recommendationNote") ?: "",
        internalNote = doc.getString("internalNote") ?: "",
        chatThreadId = doc.getString("chatThreadId") ?: "",
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now,
        completedAt = doc.getLong("completedAt") ?: 0L,
        cancelledAt = doc.getLong("cancelledAt") ?: 0L,
        cancelReason = doc.getString("cancelReason") ?: ""
    )
}

fun TreatmentPlan.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "appointmentId" to appointmentId,
        "userId" to userId,
        "userEmail" to userEmail,
        "userName" to userName,
        "phoneNumber" to phoneNumber,
        "consultantId" to consultantId,
        "consultantEmail" to consultantEmail,
        "consultantName" to consultantName,
        "spaPackageId" to spaPackageId,
        "packageName" to packageName,
        "packageImageUrl" to packageImageUrl,
        "packageType" to packageType,
        "category" to category,
        "totalPrice" to totalPrice,
        "sessionCount" to sessionCount.coerceAtLeast(1),
        "completedSessionCount" to completedSessionCount.coerceAtLeast(0),
        "durationPerSessionMinutes" to durationPerSessionMinutes.coerceAtLeast(0),
        "suggestedIntervalDays" to suggestedIntervalDays.coerceAtLeast(0),
        "requiresProgressPhotos" to requiresProgressPhotos,
        "photoPolicy" to photoPolicy,
        "photoGuide" to photoGuide,
        "status" to status,
        "consultationNote" to consultationNote,
        "recommendationNote" to recommendationNote,
        "internalNote" to internalNote,
        "chatThreadId" to chatThreadId,
        "updatedAt" to now,
        "completedAt" to completedAt,
        "cancelledAt" to cancelledAt,
        "cancelReason" to cancelReason
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}
