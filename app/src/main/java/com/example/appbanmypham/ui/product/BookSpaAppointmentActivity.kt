package com.example.appbanmypham.ui.product

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.SPA_BOOKING_SLOTS
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.SpaPackageType
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentPlanStatus
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.TreatmentSessionStatus
import com.example.appbanmypham.model.appointmentTimeRange
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.model.nextBookingDateOptions
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class BookSpaAppointmentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageId = intent.getStringExtra("spa_package_id") ?: run {
            finish()
            return
        }

        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    BookSpaAppointmentScreen(packageId = packageId, onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun BookSpaAppointmentScreen(packageId: String, onBack: () -> Unit = {}) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateOptions = remember { nextBookingDateOptions(31) }

    var item by remember { mutableStateOf<SpaPackage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var bookedSlotsByDate by remember { mutableStateOf<Map<Long, Set<String>>>(emptyMap()) }
    var phone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(dateOptions.firstOrNull()) }
    var selectedSlot by remember { mutableStateOf(SPA_BOOKING_SLOTS.first()) }

    LaunchedEffect(packageId) {
        runCatching {
            val doc = withContext(Dispatchers.IO) {
                db.collection("spa_packages").document(packageId).get().await()
            }
            val parsed = firestoreDocToSpaPackage(doc)
            item = parsed.takeIf { it.isActive }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val start = dateOptions.firstOrNull()?.startOfDayMillis ?: return@LaunchedEffect
        val end = (dateOptions.lastOrNull()?.startOfDayMillis ?: start) + DAY_MILLIS
        val booked = withContext(Dispatchers.IO) {
            db.collection("appointments")
                .whereGreaterThanOrEqualTo("startAt", start)
                .whereLessThan("startAt", end)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    val slot = doc.getString("timeSlotLabel") ?: return@mapNotNull null
                    val startAt = doc.getLong("startAt") ?: return@mapNotNull null
                    if (!AppointmentStatus.activeStatuses.contains(status)) return@mapNotNull null
                    val day = dateOptions.firstOrNull { startAt >= it.startOfDayMillis && startAt < it.startOfDayMillis + DAY_MILLIS }
                        ?: return@mapNotNull null
                    day.startOfDayMillis to slot
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
        }
        bookedSlotsByDate = booked
    }

    val availableSlots = remember(selectedDate, bookedSlotsByDate) {
        val booked = selectedDate?.let { bookedSlotsByDate[it.startOfDayMillis] }.orEmpty()
        SPA_BOOKING_SLOTS.filterNot { it in booked }
    }

    LaunchedEffect(selectedDate?.startOfDayMillis, bookedSlotsByDate) {
        if (selectedSlot !in availableSlots) selectedSlot = availableSlots.firstOrNull().orEmpty()
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Dat lich spa",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }

                item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Goi spa khong kha dung", color = Color(0xFF8ACABA), fontSize = 15.sp)
                    }
                }

                else -> {
                    val spa = item!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFEAF9F5)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(30.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(spa.name, color = Color(0xFF1A4A40), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Text("${spa.durationMinutes} phut - ${"%,.0f".format(spa.price)}d", color = Color(0xFF8ACABA), fontSize = 13.sp)
                                        if (spa.packageType == SpaPackageType.TREATMENT_TEMPLATE) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("${spa.sessionCount} buoi - ${spa.durationPerSessionMinutes.takeIf { it > 0 } ?: spa.durationMinutes} phut/buoi", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (spa.packageType == SpaPackageType.TREATMENT_TEMPLATE) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF2)),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = Color(0xFFE8A44A), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Dat lich dau tien cua lieu trinh", color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Goi nay co ${spa.sessionCount} buoi co dinh. Khi dat lich, he thong tao san lieu trinh va danh sach buoi; ban chi can chon lich cho buoi dau tien truoc.",
                                            color = Color(0xFF5A8A80),
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        )
                                        if (spa.requiresProgressPhotos) {
                                            Spacer(Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MintGreen, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(progressPhotoPolicyMeta(spa.photoPolicy).label, color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { FormSectionTitle("Chon ngay trong 1 thang toi") }
                        item {
                            BookingCalendarGrid(
                                dateOptions = dateOptions,
                                selectedDateMillis = selectedDate?.startOfDayMillis,
                                bookedSlotsByDate = bookedSlotsByDate,
                                onSelect = { selectedDate = it }
                            )
                        }

                        item { FormSectionTitle("Chon gio") }
                        item {
                            if (availableSlots.isEmpty()) {
                                Text("Ngay nay da kin lich, vui long chon ngay khac.", color = Color(0xFFE8A44A), fontSize = 13.sp)
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.heightIn(max = 150.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    userScrollEnabled = false
                                ) {
                                    items(availableSlots) { slot ->
                                        val selected = selectedSlot == slot
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (selected) MintGreen else Color.White)
                                                .clickable { selectedSlot = slot }
                                                .padding(horizontal = 10.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, tint = if (selected) Color.White else MintGreen, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(slot, color = if (selected) Color.White else Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("So dien thoai") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MintGreen) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = appointmentTextFieldColors()
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                                label = { Text("Ghi chu") },
                                placeholder = { Text("Tinh trang da, mong muon tu van...") },
                                shape = RoundedCornerShape(14.dp),
                                colors = appointmentTextFieldColors()
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    val user = auth.currentUser
                                    val date = selectedDate
                                    if (user == null) {
                                        scope.launch { snackbarHostState.showSnackbar("Vui long dang nhap de dat lich") }
                                        return@Button
                                    }
                                    if (date == null || phone.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("Vui long nhap day du ngay, gio va so dien thoai") }
                                        return@Button
                                    }
                                    if (selectedSlot.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("Ngay nay da het khung gio trong") }
                                        return@Button
                                    }

                                    isSubmitting = true
                                    scope.launch {
                                        val result = runCatching {
                                            createSpaAppointment(
                                                db = db,
                                                spa = spa,
                                                userId = user.uid,
                                                userEmail = user.email.orEmpty(),
                                                userName = user.displayName.orEmpty(),
                                                phoneNumber = phone.trim(),
                                                note = note.trim(),
                                                dateStartMillis = date.startOfDayMillis,
                                                dateLabel = date.label,
                                                slot = selectedSlot
                                            )
                                        }
                                        isSubmitting = false
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar(if (spa.packageType == SpaPackageType.TREATMENT_TEMPLATE) "Da tao lieu trinh va gui lich buoi dau" else "Da gui yeu cau dat lich")
                                            onBack()
                                        } else {
                                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Dat lich that bai")
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Xac nhan dat lich", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DAY_MILLIS = 86_400_000L

@Composable
private fun BookingCalendarGrid(
    dateOptions: List<com.example.appbanmypham.model.BookingDateOption>,
    selectedDateMillis: Long?,
    bookedSlotsByDate: Map<Long, Set<String>>,
    onSelect: (com.example.appbanmypham.model.BookingDateOption) -> Unit
) {
    val leadingBlanks = remember(dateOptions) {
        val first = dateOptions.firstOrNull()?.startOfDayMillis ?: 0L
        if (first == 0L) 0 else {
            val dayOfWeek = Calendar.getInstance().apply { timeInMillis = first }.get(Calendar.DAY_OF_WEEK)
            (dayOfWeek + 5) % 7
        }
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach {
                    Text(it, color = Color(0xFF8ACABA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(294.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(List(leadingBlanks) { it }) {
                    Spacer(Modifier.aspectRatio(1f))
                }
                items(dateOptions) { option ->
                    val bookedCount = bookedSlotsByDate[option.startOfDayMillis]?.size ?: 0
                    val isFull = bookedCount >= SPA_BOOKING_SLOTS.size
                    val selected = selectedDateMillis == option.startOfDayMillis
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    selected -> MintGreen
                                    isFull -> Color(0xFFF2F2F2)
                                    bookedCount > 0 -> Color(0xFFFFFBF2)
                                    else -> Color(0xFFF8FFFE)
                                }
                            )
                            .clickable(enabled = !isFull) { onSelect(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                option.label.substringBefore("/"),
                                color = when {
                                    selected -> Color.White
                                    isFull -> Color(0xFFB8B8B8)
                                    else -> Color(0xFF1A4A40)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (bookedCount > 0) {
                                Text(
                                    if (isFull) "Kin" else "-$bookedCount",
                                    color = if (selected) Color.White.copy(0.85f) else Color(0xFFE8A44A),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun createSpaAppointment(
    db: FirebaseFirestore,
    spa: SpaPackage,
    userId: String,
    userEmail: String,
    userName: String,
    phoneNumber: String,
    note: String,
    dateStartMillis: Long,
    dateLabel: String,
    slot: String
) {
    val visitDuration = if (spa.packageType == SpaPackageType.TREATMENT_TEMPLATE) {
        spa.durationPerSessionMinutes.takeIf { it > 0 } ?: spa.durationMinutes
    } else {
        spa.durationMinutes
    }
    val (startAt, endAt) = appointmentTimeRange(dateStartMillis, slot, visitDuration)
    val conflicting = withContext(Dispatchers.IO) {
        db.collection("appointments")
            .whereEqualTo("startAt", startAt)
            .get()
            .await()
            .documents
            .any { AppointmentStatus.activeStatuses.contains(it.getString("status")) }
    }
    if (conflicting) throw IllegalStateException("Khung gio nay da co lich, vui long chon gio khac")

    val appointment = SpaAppointment(
        userId = userId,
        userEmail = userEmail,
        userName = userName,
        phoneNumber = phoneNumber,
        spaPackageId = spa.id,
        spaPackageName = spa.name,
        spaPackagePrice = spa.price,
        durationMinutes = visitDuration,
        startAt = startAt,
        endAt = endAt,
        appointmentDateLabel = dateLabel,
        timeSlotLabel = slot,
        status = AppointmentStatus.PENDING,
        note = note
    )
    withContext(Dispatchers.IO) {
        if (spa.packageType != SpaPackageType.TREATMENT_TEMPLATE) {
            db.collection("appointments")
                .add(appointment.toFirestoreMap(includeCreatedAt = true))
                .await()
            return@withContext
        }

        val now = System.currentTimeMillis()
        val appointmentRef = db.collection("appointments").document()
        val planRef = db.collection("treatment_plans").document()
        val sessionCount = spa.sessionCount.coerceAtLeast(1)
        val plan = TreatmentPlan(
            appointmentId = appointmentRef.id,
            userId = userId,
            userEmail = userEmail,
            userName = userName,
            phoneNumber = phoneNumber,
            consultantId = "",
            consultantEmail = "",
            consultantName = "",
            spaPackageId = spa.id,
            packageName = spa.name,
            packageImageUrl = spa.imageUrl,
            packageType = SpaPackageType.TREATMENT_TEMPLATE,
            category = spa.category,
            totalPrice = spa.price,
            sessionCount = sessionCount,
            completedSessionCount = 0,
            durationPerSessionMinutes = visitDuration,
            suggestedIntervalDays = spa.suggestedIntervalDays,
            requiresProgressPhotos = spa.requiresProgressPhotos,
            photoPolicy = spa.photoPolicy,
            photoGuide = spa.photoGuide,
            status = TreatmentPlanStatus.WAITING_CONSULTANT,
            consultationNote = note,
            chatThreadId = appointmentRef.id,
            createdAt = now,
            updatedAt = now
        )
        val batch = db.batch()
        batch.set(appointmentRef, appointment.toFirestoreMap(includeCreatedAt = true))
        batch.set(planRef, plan.toFirestoreMap(includeCreatedAt = true))
        for (number in 1..sessionCount) {
            val isFirst = number == 1
            val session = TreatmentSession(
                treatmentPlanId = planRef.id,
                appointmentId = if (isFirst) appointmentRef.id else "",
                userId = userId,
                consultantId = "",
                spaPackageId = spa.id,
                packageName = spa.name,
                sessionNumber = number,
                totalSessions = sessionCount,
                scheduledStartAt = if (isFirst) startAt else 0L,
                scheduledEndAt = if (isFirst) endAt else 0L,
                dateLabel = if (isFirst) dateLabel else "",
                timeSlotLabel = if (isFirst) slot else "",
                status = if (isFirst) TreatmentSessionStatus.SCHEDULED else TreatmentSessionStatus.UNSCHEDULED,
                requiresProgressPhotos = spa.requiresProgressPhotos,
                photoPolicy = spa.photoPolicy,
                createdAt = now,
                updatedAt = now
            )
            batch.set(db.collection("treatment_sessions").document(), session.toFirestoreMap(includeCreatedAt = true))
        }
        batch.commit().await()
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(MintGreen))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun appointmentTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
