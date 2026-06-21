package com.example.appbanmypham.model

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

const val SPA_CAPACITY_SETTINGS_COLLECTION = "spa_capacity_settings"
const val SPA_CAPACITY_OVERRIDES_COLLECTION = "spa_capacity_overrides"
const val APPOINTMENT_CAPACITY_BLOCKS_COLLECTION = "appointment_capacity_blocks"
const val DEFAULT_SPA_CAPACITY_SETTINGS_ID = "default"

data class SpaWorkingWindow(
    val start: String = "09:00",
    val end: String = "12:00"
)

data class SpaCapacitySettings(
    val id: String = DEFAULT_SPA_CAPACITY_SETTINGS_ID,
    val defaultConcurrentBookings: Int = 3,
    val slotMinutes: Int = 30,
    val workingWindows: List<SpaWorkingWindow> = DEFAULT_SPA_WORKING_WINDOWS,
    val closedWeekdays: List<Int> = emptyList(),
    val bookingHorizonDays: Int = 31,
    val noShowGraceMinutes: Int = 15,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

data class SpaCapacityOverride(
    val id: String = "",
    val dateKey: String = "",
    val dateLabel: String = "",
    val concurrentBookings: Int = 0,
    val closed: Boolean = false,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

data class AppointmentCapacityBlock(
    val id: String = "",
    val dateKey: String = "",
    val blockStartAt: Long = 0L,
    val blockLabel: String = "",
    val capacity: Int = 0,
    val bookedCount: Int = 0,
    val appointmentIds: List<String> = emptyList(),
    val closed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class EffectiveSpaCapacity(
    val dateKey: String,
    val capacity: Int,
    val closed: Boolean,
    val note: String = "",
    val settings: SpaCapacitySettings
)

enum class SpaSlotAvailabilityStatus {
    AVAILABLE,
    FULL,
    CLOSED,
    PAST,
    OUTSIDE_WORKING_HOURS
}

data class SpaSlotAvailability(
    val slot: String,
    val startAt: Long,
    val endAt: Long,
    val status: SpaSlotAvailabilityStatus,
    val remainingCapacity: Int = 0,
    val capacity: Int = 0,
    val bookedCount: Int = 0,
    val reason: String = ""
) {
    val selectable: Boolean get() = status == SpaSlotAvailabilityStatus.AVAILABLE
}

data class SpaCapacitySnapshot(
    val settings: SpaCapacitySettings,
    val overridesByDateKey: Map<String, SpaCapacityOverride>,
    val blocksByKey: Map<String, AppointmentCapacityBlock>,
    val legacyCountsByBlockKey: Map<String, Int>
)

data class FirestoreTransactionSet(
    val ref: DocumentReference,
    val data: Map<String, Any>
)

data class FirestoreTransactionUpdate(
    val ref: DocumentReference,
    val data: Map<String, Any>
)

val DEFAULT_SPA_WORKING_WINDOWS = listOf(
    SpaWorkingWindow("09:00", "12:00"),
    SpaWorkingWindow("13:30", "17:00")
)

fun firestoreDocToSpaCapacitySettings(doc: DocumentSnapshot): SpaCapacitySettings {
    val rawWindows = doc.get("workingWindows") as? List<*>
    val windows = rawWindows
        ?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            SpaWorkingWindow(
                start = map["start"] as? String ?: return@mapNotNull null,
                end = map["end"] as? String ?: return@mapNotNull null
            )
        }
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_SPA_WORKING_WINDOWS

    return SpaCapacitySettings(
        id = doc.id,
        defaultConcurrentBookings = ((doc.getLong("defaultConcurrentBookings") ?: 3L).toInt()).coerceAtLeast(1),
        slotMinutes = ((doc.getLong("slotMinutes") ?: 30L).toInt()).coerceIn(15, 120),
        workingWindows = windows,
        closedWeekdays = (doc.get("closedWeekdays") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            .orEmpty(),
        bookingHorizonDays = ((doc.getLong("bookingHorizonDays") ?: 31L).toInt()).coerceIn(1, 120),
        noShowGraceMinutes = ((doc.getLong("noShowGraceMinutes") ?: 15L).toInt()).coerceIn(0, 240),
        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
        updatedBy = doc.getString("updatedBy") ?: ""
    )
}

fun SpaCapacitySettings.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "defaultConcurrentBookings" to defaultConcurrentBookings.coerceAtLeast(1),
        "slotMinutes" to slotMinutes.coerceIn(15, 120),
        "workingWindows" to workingWindows.map { mapOf("start" to it.start, "end" to it.end) },
        "closedWeekdays" to closedWeekdays,
        "bookingHorizonDays" to bookingHorizonDays.coerceIn(1, 120),
        "noShowGraceMinutes" to noShowGraceMinutes.coerceIn(0, 240),
        "updatedAt" to now,
        "updatedBy" to updatedBy
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}

fun firestoreDocToSpaCapacityOverride(doc: DocumentSnapshot): SpaCapacityOverride =
    SpaCapacityOverride(
        id = doc.id,
        dateKey = doc.getString("dateKey") ?: doc.id,
        dateLabel = doc.getString("dateLabel") ?: "",
        concurrentBookings = ((doc.getLong("concurrentBookings") ?: 0L).toInt()).coerceAtLeast(0),
        closed = doc.getBoolean("closed") == true,
        note = doc.getString("note") ?: "",
        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
        updatedBy = doc.getString("updatedBy") ?: ""
    )

fun SpaCapacityOverride.toFirestoreMap(includeCreatedAt: Boolean = false): HashMap<String, Any> {
    val now = System.currentTimeMillis()
    return hashMapOf<String, Any>(
        "dateKey" to dateKey,
        "dateLabel" to dateLabel,
        "concurrentBookings" to concurrentBookings.coerceAtLeast(0),
        "closed" to closed,
        "note" to note,
        "updatedAt" to now,
        "updatedBy" to updatedBy
    ).apply {
        if (includeCreatedAt) this["createdAt"] = now
    }
}

fun firestoreDocToAppointmentCapacityBlock(doc: DocumentSnapshot): AppointmentCapacityBlock =
    AppointmentCapacityBlock(
        id = doc.id,
        dateKey = doc.getString("dateKey") ?: "",
        blockStartAt = doc.getLong("blockStartAt") ?: 0L,
        blockLabel = doc.getString("blockLabel") ?: "",
        capacity = ((doc.getLong("capacity") ?: 0L).toInt()).coerceAtLeast(0),
        bookedCount = ((doc.getLong("bookedCount") ?: 0L).toInt()).coerceAtLeast(0),
        appointmentIds = (doc.get("appointmentIds") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty(),
        closed = doc.getBoolean("closed") == true,
        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
    )

fun AppointmentCapacityBlock.toFirestoreMap(): HashMap<String, Any> =
    hashMapOf(
        "dateKey" to dateKey,
        "blockStartAt" to blockStartAt,
        "blockLabel" to blockLabel,
        "capacity" to capacity.coerceAtLeast(0),
        "bookedCount" to bookedCount.coerceAtLeast(0),
        "appointmentIds" to appointmentIds,
        "closed" to closed,
        "updatedAt" to System.currentTimeMillis()
    )

fun spaDateKey(startOfDayMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startOfDayMillis))

fun spaBlockKey(blockStartAt: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = blockStartAt }
    return "${spaDateKey(startOfDayMillis(blockStartAt))}_${"%02d".format(cal.get(Calendar.HOUR_OF_DAY))}-${"%02d".format(cal.get(Calendar.MINUTE))}"
}

fun startOfDayMillis(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun minutesOfDay(slot: String): Int? {
    val parts = slot.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun slotLabelFromMillis(timeMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(timeMillis))

fun isSlotInsideWorkingWindow(slot: String, durationMinutes: Int, settings: SpaCapacitySettings): Boolean {
    val start = minutesOfDay(slot) ?: return false
    val end = start + durationMinutes.coerceAtLeast(settings.slotMinutes)
    return settings.workingWindows.any { window ->
        val windowStart = minutesOfDay(window.start) ?: return@any false
        val windowEnd = minutesOfDay(window.end) ?: return@any false
        start >= windowStart && end <= windowEnd
    }
}

fun validSpaStartSlots(settings: SpaCapacitySettings, durationMinutes: Int): List<String> {
    val blockMinutes = settings.slotMinutes.coerceAtLeast(15)
    return settings.workingWindows.flatMap { window ->
        val start = minutesOfDay(window.start) ?: return@flatMap emptyList()
        val end = minutesOfDay(window.end) ?: return@flatMap emptyList()
        generateSequence(start) { it + blockMinutes }
            .takeWhile { it + durationMinutes.coerceAtLeast(blockMinutes) <= end }
            .map { "%02d:%02d".format(it / 60, it % 60) }
            .toList()
    }.distinct()
}

fun capacityBlockStartTimes(startAt: Long, endAt: Long, blockMinutes: Int): List<Long> {
    val blockMillis = blockMinutes.coerceAtLeast(15) * 60_000L
    return generateSequence(startAt) { it + blockMillis }
        .takeWhile { it < endAt }
        .toList()
}

fun capacityBlockKeys(startAt: Long, endAt: Long, blockMinutes: Int): List<String> =
    capacityBlockStartTimes(startAt, endAt, blockMinutes).map { spaBlockKey(it) }

fun resolveEffectiveSpaCapacity(
    settings: SpaCapacitySettings,
    override: SpaCapacityOverride?,
    dateStartMillis: Long
): EffectiveSpaCapacity {
    val weekday = Calendar.getInstance().apply { timeInMillis = dateStartMillis }.get(Calendar.DAY_OF_WEEK)
    val overrideCapacity = override?.concurrentBookings?.takeIf { it > 0 }
    val closed = weekday in settings.closedWeekdays || override?.closed == true
    return EffectiveSpaCapacity(
        dateKey = spaDateKey(dateStartMillis),
        capacity = (overrideCapacity ?: settings.defaultConcurrentBookings).coerceAtLeast(1),
        closed = closed,
        note = override?.note.orEmpty(),
        settings = settings
    )
}

fun buildSpaSlotAvailability(
    date: BookingDateOption,
    durationMinutes: Int,
    snapshot: SpaCapacitySnapshot,
    nowMillis: Long = System.currentTimeMillis()
): List<SpaSlotAvailability> {
    val effective = resolveEffectiveSpaCapacity(
        settings = snapshot.settings,
        override = snapshot.overridesByDateKey[spaDateKey(date.startOfDayMillis)],
        dateStartMillis = date.startOfDayMillis
    )
    val slots = validSpaStartSlots(snapshot.settings, durationMinutes).ifEmpty { SPA_BOOKING_SLOTS }
    return slots.map { slot ->
        val (startAt, endAt) = appointmentTimeRange(date.startOfDayMillis, slot, durationMinutes)
        val blockKeys = capacityBlockKeys(startAt, endAt, snapshot.settings.slotMinutes)
        val highestBooked = blockKeys.maxOfOrNull { key ->
            val counter = snapshot.blocksByKey[key]?.bookedCount ?: 0
            counter + (snapshot.legacyCountsByBlockKey[key] ?: 0)
        } ?: 0
        val anyBlockClosed = blockKeys.any { snapshot.blocksByKey[it]?.closed == true }
        val outside = !isSlotInsideWorkingWindow(slot, durationMinutes, snapshot.settings)
        val status = when {
            effective.closed || anyBlockClosed -> SpaSlotAvailabilityStatus.CLOSED
            startAt <= nowMillis -> SpaSlotAvailabilityStatus.PAST
            outside -> SpaSlotAvailabilityStatus.OUTSIDE_WORKING_HOURS
            highestBooked >= effective.capacity -> SpaSlotAvailabilityStatus.FULL
            else -> SpaSlotAvailabilityStatus.AVAILABLE
        }
        val reason = when (status) {
            SpaSlotAvailabilityStatus.AVAILABLE -> "Còn ${max(0, effective.capacity - highestBooked)} chỗ"
            SpaSlotAvailabilityStatus.FULL -> "Đã đủ ${effective.capacity}/${effective.capacity} khách"
            SpaSlotAvailabilityStatus.CLOSED -> effective.note.ifBlank { "Spa nghỉ hoặc đã đóng ngày này" }
            SpaSlotAvailabilityStatus.PAST -> "Đã qua giờ"
            SpaSlotAvailabilityStatus.OUTSIDE_WORKING_HOURS -> "Vượt giờ làm việc"
        }
        SpaSlotAvailability(
            slot = slot,
            startAt = startAt,
            endAt = endAt,
            status = status,
            remainingCapacity = max(0, effective.capacity - highestBooked),
            capacity = effective.capacity,
            bookedCount = highestBooked,
            reason = reason
        )
    }
}

suspend fun loadSpaCapacitySnapshot(
    db: FirebaseFirestore,
    dateOptions: List<BookingDateOption>
): SpaCapacitySnapshot {
    val settingsDoc = db.collection(SPA_CAPACITY_SETTINGS_COLLECTION)
        .document(DEFAULT_SPA_CAPACITY_SETTINGS_ID)
        .get()
        .await()
    val settings = if (settingsDoc.exists()) {
        firestoreDocToSpaCapacitySettings(settingsDoc)
    } else {
        SpaCapacitySettings()
    }

    val start = dateOptions.firstOrNull()?.startOfDayMillis ?: startOfDayMillis(System.currentTimeMillis())
    val end = (dateOptions.lastOrNull()?.startOfDayMillis ?: start) + 86_400_000L
    val dateKeys = dateOptions.map { spaDateKey(it.startOfDayMillis) }.toSet()

    val overrides = db.collection(SPA_CAPACITY_OVERRIDES_COLLECTION)
        .get()
        .await()
        .documents
        .mapNotNull { doc -> runCatching { firestoreDocToSpaCapacityOverride(doc) }.getOrNull() }
        .filter { it.dateKey in dateKeys }
        .associateBy { it.dateKey }

    val blocks = db.collection(APPOINTMENT_CAPACITY_BLOCKS_COLLECTION)
        .whereGreaterThanOrEqualTo("blockStartAt", start)
        .whereLessThan("blockStartAt", end)
        .get()
        .await()
        .documents
        .mapNotNull { doc -> runCatching { firestoreDocToAppointmentCapacityBlock(doc) }.getOrNull() }
        .associateBy { it.id }

    val legacyCounts = db.collection("appointments")
        .whereGreaterThanOrEqualTo("startAt", start)
        .whereLessThan("startAt", end)
        .get()
        .await()
        .documents
        .mapNotNull { doc ->
            val status = doc.getString("status") ?: return@mapNotNull null
            if (status !in AppointmentStatus.activeStatuses) return@mapNotNull null
            val reserved = (doc.get("reservedBlockKeys") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            if (reserved.isNotEmpty()) return@mapNotNull null
            val startAt = doc.getLong("startAt") ?: return@mapNotNull null
            val endAt = doc.getLong("endAt") ?: return@mapNotNull null
            capacityBlockKeys(startAt, endAt, settings.slotMinutes)
        }
        .flatten()
        .groupingBy { it }
        .eachCount()

    return SpaCapacitySnapshot(
        settings = settings,
        overridesByDateKey = overrides,
        blocksByKey = blocks,
        legacyCountsByBlockKey = legacyCounts
    )
}

suspend fun reserveSpaCapacityAndWrite(
    db: FirebaseFirestore,
    appointmentRef: DocumentReference,
    appointment: SpaAppointment,
    blockKeys: List<String>,
    blockStartTimes: List<Long>,
    effectiveCapacity: Int,
    extraSets: List<FirestoreTransactionSet> = emptyList(),
    extraUpdates: List<FirestoreTransactionUpdate> = emptyList(),
    verifyBeforeWrite: ((Transaction) -> Unit)? = null
) {
    db.runTransaction { tx ->
        val now = System.currentTimeMillis()
        val blockRefs = blockKeys.map { key -> db.collection(APPOINTMENT_CAPACITY_BLOCKS_COLLECTION).document(key) }
        val blockSnapshots = blockRefs.map { tx.get(it) }
        blockSnapshots.forEachIndexed { index, snap ->
            val key = blockKeys[index]
            val currentCount = (snap.getLong("bookedCount") ?: 0L).toInt()
            val closed = snap.getBoolean("closed") == true
            val capacity = ((snap.getLong("capacity") ?: effectiveCapacity.toLong()).toInt()).coerceAtLeast(1)
            if (closed || currentCount >= capacity) {
                throw IllegalStateException("Khung ${key.substringAfter('_').replace('-', ':')} vừa hết chỗ, vui lòng chọn giờ khác")
            }
        }
        verifyBeforeWrite?.invoke(tx)
        blockRefs.forEachIndexed { index, ref ->
            val snap = blockSnapshots[index]
            val key = blockKeys[index]
            val existingIds = (snap.get("appointmentIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val count = (snap.getLong("bookedCount") ?: 0L).toInt()
            val blockStartAt = blockStartTimes.getOrNull(index) ?: appointment.startAt
            val data = AppointmentCapacityBlock(
                id = key,
                dateKey = spaDateKey(startOfDayMillis(blockStartAt)),
                blockStartAt = blockStartAt,
                blockLabel = slotLabelFromMillis(blockStartAt),
                capacity = (snap.getLong("capacity") ?: effectiveCapacity.toLong()).toInt().coerceAtLeast(1),
                bookedCount = count + 1,
                appointmentIds = (existingIds + appointmentRef.id).distinct(),
                closed = false,
                updatedAt = now
            ).toFirestoreMap()
            tx.set(ref, data)
        }
        tx.set(
            appointmentRef,
            appointment.copy(reservedBlockKeys = blockKeys).toFirestoreMap(includeCreatedAt = true)
        )
        extraSets.forEach { tx.set(it.ref, it.data) }
        extraUpdates.forEach { tx.update(it.ref, it.data) }
        null
    }.await()
}

suspend fun releaseSpaCapacityForAppointment(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    statusUpdates: Map<String, Any>
) {
    db.runTransaction { tx ->
        val now = System.currentTimeMillis()
        val appointmentRef = db.collection("appointments").document(appointment.id)
        val blockKeys = appointment.reservedBlockKeys
        val blockRefs = blockKeys.map { db.collection(APPOINTMENT_CAPACITY_BLOCKS_COLLECTION).document(it) }
        val blockSnapshots = blockRefs.map { tx.get(it) }
        blockRefs.forEachIndexed { index, ref ->
            val snap = blockSnapshots[index]
            if (snap.exists()) {
                val ids = (snap.get("appointmentIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                    .filterNot { it == appointment.id }
                val bookedCount = ((snap.getLong("bookedCount") ?: 0L).toInt() - 1).coerceAtLeast(0)
                tx.update(ref, mapOf("bookedCount" to bookedCount, "appointmentIds" to ids, "updatedAt" to now))
            }
        }
        tx.update(appointmentRef, statusUpdates + mapOf("updatedAt" to now))
        null
    }.await()
}
