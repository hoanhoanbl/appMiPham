package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SpaAppointment(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val phoneNumber: String = "",
    val spaPackageId: String = "",
    val spaPackageName: String = "",
    val spaPackagePrice: Double = 0.0,
    val durationMinutes: Int = 0,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val appointmentDateLabel: String = "",
    val timeSlotLabel: String = "",
    val status: String = AppointmentStatus.PENDING,
    val note: String = "",
    val consultantId: String = "",
    val consultantEmail: String = "",
    val consultantName: String = "",
    val consultantNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confirmedAt: Long = 0L,
    val completedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val cancelReason: String = ""
)

object AppointmentStatus {
    const val PENDING = "pending"
    const val ASSIGNED = "assigned"
    const val CONFIRMED = "confirmed"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
    const val NO_SHOW = "no_show"
    const val RESCHEDULED = "rescheduled"

    val activeStatuses = setOf(PENDING, ASSIGNED, CONFIRMED, RESCHEDULED)
}

data class AppointmentStatusMeta(
    val key: String,
    val label: String,
    val description: String
)

val SPA_APPOINTMENT_STATUSES = listOf(
    AppointmentStatusMeta(AppointmentStatus.PENDING, "Cho xac nhan", "Dang cho tu van vien xac nhan"),
    AppointmentStatusMeta(AppointmentStatus.ASSIGNED, "Da co tu van vien", "Tu van vien da nhan va co the trao doi voi khach"),
    AppointmentStatusMeta(AppointmentStatus.CONFIRMED, "Da xac nhan lich", "Lich hen da duoc xac nhan thoi gian"),
    AppointmentStatusMeta(AppointmentStatus.COMPLETED, "Hoan thanh", "Lich hen da hoan thanh"),
    AppointmentStatusMeta(AppointmentStatus.CANCELLED, "Da huy", "Lich hen da huy"),
    AppointmentStatusMeta(AppointmentStatus.NO_SHOW, "Khach khong den", "Khach vang mat, khong tru buoi trong MVP"),
    AppointmentStatusMeta(AppointmentStatus.RESCHEDULED, "Da doi lich", "Lich hen da duoc hen lai")
)

fun appointmentStatusMeta(status: String): AppointmentStatusMeta =
    SPA_APPOINTMENT_STATUSES.find { it.key == status }
        ?: AppointmentStatusMeta(status, status, status)

val SPA_BOOKING_SLOTS = listOf("09:00", "10:30", "13:30", "15:00", "16:30")

fun firestoreDocToSpaAppointment(doc: DocumentSnapshot): SpaAppointment {
    val now = System.currentTimeMillis()
    return SpaAppointment(
        id = doc.id,
        userId = doc.getString("userId") ?: "",
        userEmail = doc.getString("userEmail") ?: "",
        userName = doc.getString("userName") ?: "",
        phoneNumber = doc.getString("phoneNumber") ?: "",
        spaPackageId = doc.getString("spaPackageId") ?: "",
        spaPackageName = doc.getString("spaPackageName") ?: "",
        spaPackagePrice = doc.getDouble("spaPackagePrice") ?: 0.0,
        durationMinutes = (doc.getLong("durationMinutes") ?: 0L).toInt(),
        startAt = doc.getLong("startAt") ?: 0L,
        endAt = doc.getLong("endAt") ?: 0L,
        appointmentDateLabel = doc.getString("appointmentDateLabel") ?: "",
        timeSlotLabel = doc.getString("timeSlotLabel") ?: "",
        status = doc.getString("status") ?: AppointmentStatus.PENDING,
        note = doc.getString("note") ?: "",
        consultantId = doc.getString("consultantId") ?: "",
        consultantEmail = doc.getString("consultantEmail") ?: "",
        consultantName = doc.getString("consultantName") ?: "",
        consultantNote = doc.getString("consultantNote") ?: "",
        createdAt = doc.getLong("createdAt") ?: now,
        updatedAt = doc.getLong("updatedAt") ?: now,
        confirmedAt = doc.getLong("confirmedAt") ?: 0L,
        completedAt = doc.getLong("completedAt") ?: 0L,
        cancelledAt = doc.getLong("cancelledAt") ?: 0L,
        cancelReason = doc.getString("cancelReason") ?: ""
    )
}

fun SpaAppointment.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "userId" to userId,
        "userEmail" to userEmail,
        "userName" to userName,
        "phoneNumber" to phoneNumber,
        "spaPackageId" to spaPackageId,
        "spaPackageName" to spaPackageName,
        "spaPackagePrice" to spaPackagePrice,
        "durationMinutes" to durationMinutes,
        "startAt" to startAt,
        "endAt" to endAt,
        "appointmentDateLabel" to appointmentDateLabel,
        "timeSlotLabel" to timeSlotLabel,
        "status" to status,
        "note" to note,
        "consultantId" to consultantId,
        "consultantEmail" to consultantEmail,
        "consultantName" to consultantName,
        "consultantNote" to consultantNote,
        "updatedAt" to now,
        "confirmedAt" to confirmedAt,
        "completedAt" to completedAt,
        "cancelledAt" to cancelledAt,
        "cancelReason" to cancelReason
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}

data class BookingDateOption(
    val startOfDayMillis: Long,
    val label: String,
    val compactLabel: String
)

fun nextBookingDateOptions(days: Int = 14): List<BookingDateOption> {
    val fullFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val compactFormat = SimpleDateFormat("EEE dd/MM", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (0 until days).map {
        val millis = cal.timeInMillis
        val date = Date(millis)
        val option = BookingDateOption(
            startOfDayMillis = millis,
            label = fullFormat.format(date),
            compactLabel = compactFormat.format(date)
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
        option
    }
}

fun appointmentTimeRange(dateStartMillis: Long, slot: String, durationMinutes: Int): Pair<Long, Long> {
    val parts = slot.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateStartMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    val end = start + durationMinutes.coerceAtLeast(0) * 60_000L
    return start to end
}
