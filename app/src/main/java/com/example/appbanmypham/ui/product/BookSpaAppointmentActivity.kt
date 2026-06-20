package com.example.appbanmypham.ui.product

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val dateOptions = remember { nextBookingDateOptions(14) }

    var item by remember { mutableStateOf<SpaPackage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
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
                                            "Goi nay co ${spa.sessionCount} buoi. Sau khi tu van vien nhan lich, ban se chat va xem ke hoach cac buoi tiep theo trong muc Lieu trinh cua toi.",
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

                        item { FormSectionTitle("Chon ngay") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(dateOptions) { option ->
                                    val selected = selectedDate?.startOfDayMillis == option.startOfDayMillis
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selected) MintGreen else Color.White)
                                            .clickable { selectedDate = option }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            option.compactLabel,
                                            color = if (selected) Color.White else Color(0xFF1A4A40),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        item { FormSectionTitle("Chon gio") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(SPA_BOOKING_SLOTS) { slot ->
                                    val selected = selectedSlot == slot
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selected) MintGreen else Color.White)
                                            .clickable { selectedSlot = slot }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
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
                                            snackbarHostState.showSnackbar("Da gui yeu cau dat lich")
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
    val (startAt, endAt) = appointmentTimeRange(dateStartMillis, slot, spa.durationMinutes)
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
        durationMinutes = spa.durationMinutes,
        startAt = startAt,
        endAt = endAt,
        appointmentDateLabel = dateLabel,
        timeSlotLabel = slot,
        status = AppointmentStatus.PENDING,
        note = note
    )
    withContext(Dispatchers.IO) {
        db.collection("appointments")
            .add(appointment.toFirestoreMap(includeCreatedAt = true))
            .await()
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
