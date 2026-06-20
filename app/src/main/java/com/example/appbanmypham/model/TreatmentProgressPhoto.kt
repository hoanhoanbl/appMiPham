package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot

data class TreatmentProgressPhoto(
    val id: String = "",
    val treatmentPlanId: String = "",
    val treatmentSessionId: String = "",
    val appointmentId: String = "",
    val userId: String = "",
    val consultantId: String = "",
    val photoType: String = ProgressPhotoType.AFTER,
    val angle: String = "",
    val imageUrl: String = "",
    val note: String = "",
    val uploadedBy: String = "",
    val uploaderName: String = "",
    val isHidden: Boolean = false,
    val hiddenReason: String = "",
    val hiddenAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object ProgressPhotoType {
    const val BEFORE = "before"
    const val AFTER = "after"
}

data class ProgressPhotoTypeMeta(
    val key: String,
    val label: String
)

val PROGRESS_PHOTO_TYPES = listOf(
    ProgressPhotoTypeMeta(ProgressPhotoType.BEFORE, "Truoc buoi"),
    ProgressPhotoTypeMeta(ProgressPhotoType.AFTER, "Sau buoi")
)

fun progressPhotoTypeMeta(type: String): ProgressPhotoTypeMeta =
    PROGRESS_PHOTO_TYPES.find { it.key == type }
        ?: PROGRESS_PHOTO_TYPES.last()

fun firestoreDocToTreatmentProgressPhoto(doc: DocumentSnapshot): TreatmentProgressPhoto {
    val now = System.currentTimeMillis()
    return TreatmentProgressPhoto(
        id = doc.id,
        treatmentPlanId = doc.getString("treatmentPlanId") ?: "",
        treatmentSessionId = doc.getString("treatmentSessionId") ?: "",
        appointmentId = doc.getString("appointmentId") ?: "",
        userId = doc.getString("userId") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        photoType = doc.getString("photoType") ?: ProgressPhotoType.AFTER,
        angle = doc.getString("angle") ?: "",
        imageUrl = doc.getString("imageUrl") ?: "",
        note = doc.getString("note") ?: "",
        uploadedBy = doc.getString("uploadedBy") ?: "",
        uploaderName = doc.getString("uploaderName") ?: "",
        isHidden = doc.getBoolean("isHidden") == true,
        hiddenReason = doc.getString("hiddenReason") ?: "",
        hiddenAt = doc.getLong("hiddenAt") ?: 0L,
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now
    )
}

fun TreatmentProgressPhoto.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "treatmentPlanId" to treatmentPlanId,
        "treatmentSessionId" to treatmentSessionId,
        "appointmentId" to appointmentId,
        "userId" to userId,
        "consultantId" to consultantId,
        "photoType" to photoType,
        "angle" to angle,
        "imageUrl" to imageUrl,
        "note" to note,
        "uploadedBy" to uploadedBy,
        "uploaderName" to uploaderName,
        "isHidden" to isHidden,
        "hiddenReason" to hiddenReason,
        "hiddenAt" to hiddenAt,
        "updatedAt" to now
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}
