package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot

data class SpaPackage(
    val id: String = "",
    val name: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val category: String = "",
    val packageType: String = SpaPackageType.SINGLE_SESSION,
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val durationMinutes: Int = 0,
    val sessionCount: Int = 1,
    val durationPerSessionMinutes: Int = 0,
    val suggestedIntervalDays: Int = 7,
    val requiresProgressPhotos: Boolean = false,
    val photoPolicy: String = ProgressPhotoPolicy.NONE,
    val photoGuide: String = "",
    val imageUrl: String = "",
    val benefits: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val suitableFor: List<String> = emptyList(),
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object SpaPackageType {
    const val SINGLE_SESSION = "single_session"
    const val TREATMENT_TEMPLATE = "treatment_template"
}

data class SpaPackageTypeMeta(
    val key: String,
    val label: String,
    val description: String
)

val SPA_PACKAGE_TYPES = listOf(
    SpaPackageTypeMeta(SpaPackageType.SINGLE_SESSION, "Dịch vụ 1 buổi", "Khách đặt lịch và hoàn thành trong một lần hẹn"),
    SpaPackageTypeMeta(SpaPackageType.TREATMENT_TEMPLATE, "Gói liệu trình", "Gói spa trọn gói với số buổi cố định do admin thiết lập")
)

fun spaPackageTypeMeta(type: String): SpaPackageTypeMeta =
    SPA_PACKAGE_TYPES.find { it.key == type }
        ?: SPA_PACKAGE_TYPES.first()

object ProgressPhotoPolicy {
    const val NONE = "none"
    const val AFTER_EACH_SESSION = "after_each_session"
    const val BEFORE_AFTER_EACH_SESSION = "before_after_each_session"
}

data class ProgressPhotoPolicyMeta(
    val key: String,
    val label: String,
    val description: String
)

val PROGRESS_PHOTO_POLICIES = listOf(
    ProgressPhotoPolicyMeta(ProgressPhotoPolicy.NONE, "Không cần ảnh", "Không yêu cầu ảnh tiến trình"),
    ProgressPhotoPolicyMeta(ProgressPhotoPolicy.AFTER_EACH_SESSION, "Ảnh sau mỗi buổi", "Nhắc tư vấn viên up ảnh sau buổi điều trị"),
    ProgressPhotoPolicyMeta(ProgressPhotoPolicy.BEFORE_AFTER_EACH_SESSION, "Ảnh trước và sau", "Nhắc up ảnh trước và sau mỗi buổi")
)

fun progressPhotoPolicyMeta(policy: String): ProgressPhotoPolicyMeta =
    PROGRESS_PHOTO_POLICIES.find { it.key == policy }
        ?: PROGRESS_PHOTO_POLICIES.first()

fun firestoreDocToSpaPackage(doc: DocumentSnapshot): SpaPackage {
    val now = System.currentTimeMillis()
    val duration = (doc.getLong("durationMinutes") ?: 0L).toInt()
    val packageType = doc.getString("packageType") ?: SpaPackageType.SINGLE_SESSION
    val sessionCount = (doc.getLong("sessionCount") ?: 1L).toInt().coerceAtLeast(1)
    val durationPerSession = (doc.getLong("durationPerSessionMinutes") ?: duration.toLong()).toInt()
    val photoPolicy = doc.getString("photoPolicy") ?: ProgressPhotoPolicy.NONE
    return SpaPackage(
        id = doc.id,
        name = doc.getString("name") ?: "",
        shortDescription = doc.getString("shortDescription") ?: "",
        description = doc.getString("description") ?: "",
        category = doc.getString("category") ?: "",
        packageType = packageType,
        price = doc.getDouble("price") ?: 0.0,
        originalPrice = doc.getDouble("originalPrice") ?: 0.0,
        durationMinutes = duration,
        sessionCount = sessionCount,
        durationPerSessionMinutes = durationPerSession,
        suggestedIntervalDays = (doc.getLong("suggestedIntervalDays") ?: 7L).toInt().coerceAtLeast(0),
        requiresProgressPhotos = doc.getBoolean("requiresProgressPhotos") == true,
        photoPolicy = photoPolicy,
        photoGuide = doc.getString("photoGuide") ?: "",
        imageUrl = doc.getString("imageUrl") ?: "",
        benefits = doc.getStringArray("benefits"),
        steps = doc.getStringArray("steps"),
        suitableFor = doc.getStringArray("suitableFor"),
        isActive = doc.getBoolean("isActive") == true,
        sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now
    )
}

private fun DocumentSnapshot.getStringArray(field: String): List<String> =
    (get(field) as? List<*>)
        ?.mapNotNull { it as? String }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

fun SpaPackage.toFirestoreMap(imageUrlOverride: String = imageUrl, includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "name" to name,
        "shortDescription" to shortDescription,
        "description" to description,
        "category" to category,
        "packageType" to packageType,
        "price" to price,
        "originalPrice" to originalPrice,
        "durationMinutes" to durationMinutes,
        "sessionCount" to sessionCount.coerceAtLeast(1),
        "durationPerSessionMinutes" to durationPerSessionMinutes.coerceAtLeast(0),
        "suggestedIntervalDays" to suggestedIntervalDays.coerceAtLeast(0),
        "requiresProgressPhotos" to requiresProgressPhotos,
        "photoPolicy" to photoPolicy,
        "photoGuide" to photoGuide,
        "imageUrl" to imageUrlOverride,
        "benefits" to benefits,
        "steps" to steps,
        "suitableFor" to suitableFor,
        "isActive" to isActive,
        "sortOrder" to sortOrder,
        "updatedAt" to now
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}
