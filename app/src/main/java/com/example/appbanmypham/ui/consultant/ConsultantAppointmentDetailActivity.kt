package com.example.appbanmypham.ui.consultant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.data.CloudinaryHelper
import com.example.appbanmypham.model.APPOINTMENT_CAPACITY_BLOCKS_COLLECTION
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.BookingDateOption
import com.example.appbanmypham.model.ChatThreadStatus
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.SpaCapacitySnapshot
import com.example.appbanmypham.model.ProgressPhotoType
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentPlanStatus
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.TreatmentSessionStatus
import com.example.appbanmypham.model.ACTIVE_TREATMENT_PLAN_KEYS_COLLECTION
import com.example.appbanmypham.model.activeTreatmentPlanKey
import com.example.appbanmypham.model.appointmentTimeRange
import com.example.appbanmypham.model.buildSpaSlotAvailability
import com.example.appbanmypham.model.capacityBlockKeys
import com.example.appbanmypham.model.capacityBlockStartTimes
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.customerConsultationThreadId
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.firestoreDocToTreatmentSession
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.progressPhotoTypeMeta
import com.example.appbanmypham.model.loadSpaCapacitySnapshot
import com.example.appbanmypham.model.nextBookingDateOptions
import com.example.appbanmypham.model.resolveEffectiveSpaCapacity
import com.example.appbanmypham.model.spaDateKey
import com.example.appbanmypham.model.slotLabelFromMillis
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.model.treatmentPlanStatusMeta
import com.example.appbanmypham.model.treatmentSessionStatusMeta
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ConsultantAppointmentDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appointmentId = intent.getStringExtra("appointment_id").orEmpty()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ConsultantAppointmentDetailScreen(
                        appointmentId = appointmentId,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ConsultantAppointmentDetailScreen(
    appointmentId: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var appointment by remember { mutableStateOf<SpaAppointment?>(null) }
    var spaPackage by remember { mutableStateOf<SpaPackage?>(null) }
    var treatmentPlan by remember { mutableStateOf<TreatmentPlan?>(null) }
    var sessions by remember { mutableStateOf(listOf<TreatmentSession>()) }
    var photos by remember { mutableStateOf(listOf<TreatmentProgressPhoto>()) }
    var chatThread by remember { mutableStateOf<ConsultationChatThread?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var consultantNote by remember { mutableStateOf("") }
    var recommendationNote by remember { mutableStateOf("") }
    var photoTarget by remember { mutableStateOf<Pair<TreatmentSession, String>?>(null) }
    var photoAngle by remember { mutableStateOf("") }
    var photoNote by remember { mutableStateOf("") }
    var rescheduleTarget by remember { mutableStateOf<TreatmentSession?>(null) }
    var capacitySnapshot by remember { mutableStateOf<SpaCapacitySnapshot?>(null) }

    val currentAppointment = appointment
    val canManage = user != null && currentAppointment?.consultantId == user.uid
    val threadId = chatThread?.id ?: currentAppointment?.id.orEmpty()
    val dateOptions = remember { nextBookingDateOptions(31) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = photoTarget
        val appt = appointment
        val plan = treatmentPlan
        val signedUser = user
        if (uri != null && target != null && appt != null && plan != null && signedUser != null) {
            scope.launch {
                isBusy = true
                val result = runCatching {
                    uploadProgressPhoto(
                        context = context,
                        db = db,
                        imageUri = uri,
                        appointment = appt,
                        plan = plan,
                        session = target.first,
                        photoType = target.second,
                        angle = photoAngle.trim(),
                        note = photoNote.trim(),
                        userId = signedUser.uid,
                        uploaderName = signedUser.displayName ?: signedUser.email?.substringBefore("@") ?: "Tư vấn viên"
                    )
                }
                snackbarHostState.showSnackbar(if (result.isSuccess) "Đã tải ảnh tiến trình" else result.exceptionOrNull()?.message ?: "Tải ảnh thất bại")
                isBusy = false
                photoTarget = null
                photoAngle = ""
                photoNote = ""
            }
        }
    }

    DisposableEffect(appointmentId) {
        if (appointmentId.isBlank()) return@DisposableEffect onDispose {}
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("appointments").document(appointmentId)
            .addSnapshotListener { snap, _ ->
                appointment = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                consultantNote = appointment?.consultantNote.orEmpty()
                isLoading = false
            }
        regs += db.collection("treatment_plans")
            .whereEqualTo("appointmentId", appointmentId)
            .addSnapshotListener { snap, _ ->
                treatmentPlan = snap?.documents?.firstOrNull()?.let { runCatching { firestoreDocToTreatmentPlan(it) }.getOrNull() }
                recommendationNote = treatmentPlan?.recommendationNote.orEmpty()
            }
        regs += db.collection("treatment_sessions")
            .whereEqualTo("appointmentId", appointmentId)
            .addSnapshotListener { snap, _ ->
                val planId = snap?.documents?.firstOrNull()?.getString("treatmentPlanId").orEmpty()
                if (planId.isNotBlank() && treatmentPlan?.id != planId) {
                    db.collection("treatment_plans").document(planId).get()
                        .addOnSuccessListener { planSnap ->
                            treatmentPlan = runCatching { firestoreDocToTreatmentPlan(planSnap) }.getOrNull()
                            recommendationNote = treatmentPlan?.recommendationNote.orEmpty()
                        }
                }
            }
        onDispose { regs.forEach { it.remove() } }
    }

    DisposableEffect(appointmentId, currentAppointment?.userId, treatmentPlan?.chatThreadId) {
        val targetThreadId = currentAppointment?.userId?.takeIf { it.isNotBlank() }?.let { customerConsultationThreadId(it) }
            ?: treatmentPlan?.chatThreadId?.takeIf { it.isNotBlank() }
            ?: appointmentId
        if (targetThreadId.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("consultation_chat_threads").document(targetThreadId)
            .addSnapshotListener { snap, _ ->
                chatThread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
            }
        onDispose { reg.remove() }
    }

    LaunchedEffect(currentAppointment?.spaPackageId) {
        val packageId = currentAppointment?.spaPackageId.orEmpty()
        if (packageId.isNotBlank()) {
            spaPackage = withContext(Dispatchers.IO) {
                runCatching {
                    firestoreDocToSpaPackage(db.collection("spa_packages").document(packageId).get().await())
                }.getOrNull()
            }
        }
    }

    DisposableEffect(treatmentPlan?.id) {
        val planId = treatmentPlan?.id ?: return@DisposableEffect onDispose {}
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("treatment_sessions")
            .whereEqualTo("treatmentPlanId", planId)
            .addSnapshotListener { snap, _ ->
                sessions = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToTreatmentSession(it) }.getOrNull() }
                    ?.sortedBy { it.sessionNumber }
                    ?: emptyList()
            }
        regs += db.collection("treatment_progress_photos")
            .whereEqualTo("treatmentPlanId", planId)
            .addSnapshotListener { snap, _ ->
                photos = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToTreatmentProgressPhoto(it) }.getOrNull() }
                    ?.filter { !it.isHidden }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        onDispose { regs.forEach { it.remove() } }
    }

    LaunchedEffect(currentAppointment?.id, currentAppointment?.consultantId, treatmentPlan?.chatThreadId, chatThread?.id) {
        val appt = currentAppointment ?: return@LaunchedEffect
        if (appt.consultantId.isNotBlank() && chatThread == null) {
            runCatching { ensureChatThread(db, appt, treatmentPlan?.id.orEmpty()) }
        }
    }

    LaunchedEffect(Unit) {
        capacitySnapshot = withContext(Dispatchers.IO) { loadSpaCapacitySnapshot(db, dateOptions) }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }
            currentAppointment == null -> EmptyDetail(onBack = onBack, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DetailHeader(
                        appointment = currentAppointment,
                        packageImageUrl = treatmentPlan?.packageImageUrl?.takeIf { it.isNotBlank() } ?: spaPackage?.imageUrl.orEmpty(),
                        progressLabel = treatmentPlan?.let { "${it.completedSessionCount}/${it.sessionCount} buổi" },
                        onBack = onBack
                    )
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Person, "Thông tin khách")
                        InfoText("Khách", currentAppointment.userName.ifBlank { currentAppointment.userEmail.ifBlank { "Khách hàng" } })
                        InfoText("SDT", currentAppointment.phoneNumber.ifBlank { "-" })
                        InfoText("Gói spa", currentAppointment.spaPackageName)
                        InfoText("Lịch", "${currentAppointment.appointmentDateLabel} - ${currentAppointment.timeSlotLabel}")
                        if (currentAppointment.note.isNotBlank()) InfoText("Khách ghi chú", currentAppointment.note)
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.EditNote, "Ghi chú tư vấn")
                        OutlinedTextField(
                            value = consultantNote,
                            onValueChange = { consultantNote = it },
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                            enabled = canManage && !isBusy,
                            placeholder = { Text("Tình trạng da, mục tiêu, điều cần lưu ý...", color = Color(0xFFAAD8CE)) },
                            colors = detailTextFieldColors(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    val result = runCatching {
                                        db.collection("appointments").document(currentAppointment.id).update(
                                            mapOf(
                                                "consultantNote" to consultantNote.trim(),
                                                "updatedAt" to System.currentTimeMillis()
                                            )
                                        ).await()
                                    }
                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Đã lưu ghi chú" else "Lưu ghi chú thất bại")
                                    isBusy = false
                                }
                            },
                            enabled = canManage && !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Lưu ghi chú") }
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Chat, "Cu\u1ED9c tr\u00F2 chuy\u1EC7n")
                        if (chatThread == null) {
                            Text("Cu\u1ED9c tr\u00F2 chuy\u1EC7n s\u1EBD m\u1EDF sau khi l\u1ECBch \u0111\u01B0\u1EE3c g\u00E1n t\u01B0 v\u1EA5n vi\u00EAn.", color = Color(0xFF8ACABA), fontSize = 13.sp, lineHeight = 19.sp)
                        } else {
                            Text("Tin nh\u1EAFn \u0111\u01B0\u1EE3c t\u00E1ch sang m\u00E0n chat ri\u00EAng \u0111\u1EC3 kh\u00F4ng l\u1EABn v\u1EDBi thao t\u00E1c l\u1ECBch v\u00E0 li\u1EC7u tr\u00ECnh.", color = Color(0xFF6C8F87), fontSize = 13.sp, lineHeight = 19.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, ConsultantChatActivity::class.java)
                                            .putExtra("thread_id", chatThread?.id ?: threadId)
                                    )
                                },
                                enabled = canManage,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("M\u1EDF m\u00E0n chat ri\u00EAng", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Spa, "Liệu trình")
                        if (treatmentPlan == null) {
                            Text(
                                "Liệu trình nhiều buổi được tạo từ gói spa ngay khi khách đặt lịch. Nếu đây là dịch vụ 1 buổi thì không cần tạo liệu trình riêng.",
                                color = Color(0xFF6C8F87),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        } else {
                            treatmentPlan?.let { plan ->
                                TreatmentPlanSummary(plan = plan)
                                Spacer(Modifier.height(12.dp))
                                Text("\u0110\u1EC1 xu\u1EA5t t\u01B0 v\u1EA5n", color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = recommendationNote,
                                    onValueChange = { recommendationNote = it },
                                    modifier = Modifier.fillMaxWidth().height(92.dp),
                                    enabled = canManage && !isBusy,
                                    placeholder = { Text("Ghi l\u1EA1i khuy\u1EBFn ngh\u1ECB ch\u0103m s\u00F3c, l\u01B0u \u00FD sau bu\u1ED5i \u0111i\u1EC1u tr\u1ECB...", color = Color(0xFFAAD8CE)) },
                                    colors = detailTextFieldColors(),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            val result = runCatching {
                                                db.collection("treatment_plans").document(plan.id).update(
                                                    mapOf(
                                                        "recommendationNote" to recommendationNote.trim(),
                                                        "updatedAt" to System.currentTimeMillis()
                                                    )
                                                ).await()
                                            }
                                            snackbarHostState.showSnackbar(if (result.isSuccess) "\u0110\u00E3 l\u01B0u \u0111\u1EC1 xu\u1EA5t" else "L\u01B0u \u0111\u1EC1 xu\u1EA5t th\u1EA5t b\u1EA1i")
                                            isBusy = false
                                        }
                                    },
                                    enabled = canManage && !isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("L\u01B0u \u0111\u1EC1 xu\u1EA5t") }
                            }
                        }
                    }
                }
                if (treatmentPlan != null) {
                    item {
                        DetailCard {
                            SectionTitle(Icons.Default.EventAvailable, "Các buổi điều trị")
                            if (sessions.isEmpty()) {
                                Text("Chưa có buổi điều trị nào.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    sessions.forEach { session ->
                                        val sessionPhotos = photos.filter { it.treatmentSessionId == session.id }
                                        TreatmentSessionCard(
                                            session = session,
                                            photos = sessionPhotos,
                                            canManage = canManage && !isBusy,
                                            onUpload = { type ->
                                                photoAngle = ""
                                                photoNote = ""
                                                photoTarget = session to type
                                            },
                                            onComplete = {
                                                scope.launch {
                                                    isBusy = true
                                                    val result = runCatching { updateSessionStatus(db, session, TreatmentSessionStatus.COMPLETED, user?.uid.orEmpty()) }
                                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Đã hoàn thành buổi. Có thể thêm ảnh trước/sau." else "Cập nhật thất bại")
                                                    isBusy = false
                                                }
                                            },
                                            onReschedule = {
                                                rescheduleTarget = session
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    photoTarget?.let { target ->
        val photoTypeLabel = progressPhotoTypeMeta(target.second).label.lowercase()
        AlertDialog(
            onDismissRequest = { photoTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Thêm ảnh $photoTypeLabel", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = photoAngle,
                        onValueChange = { photoAngle = it },
                        label = { Text("Góc chụp") },
                        placeholder = { Text("Mặt trước, trái, phải...") },
                        colors = detailTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = photoNote,
                        onValueChange = { photoNote = it },
                        label = { Text("Ghi chú") },
                        colors = detailTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { photoLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) { Text("Chọn ảnh") }
            },
            dismissButton = { TextButton(onClick = { photoTarget = null }) { Text("Hủy", color = MintGreen) } }
        )
    }

    rescheduleTarget?.let { session ->
        val appt = currentAppointment
        val plan = treatmentPlan
        if (appt != null) {
            RescheduleSessionDialog(
                appointment = appt,
                plan = plan,
                session = session,
                dateOptions = dateOptions,
                capacitySnapshot = capacitySnapshot,
                isBusy = isBusy,
                onDismiss = { rescheduleTarget = null },
                onConfirm = { date, slot ->
                    scope.launch {
                        isBusy = true
                        val result = runCatching {
                            rescheduleTreatmentSessionWithCapacity(
                                db = db,
                                appointment = appt,
                                plan = plan,
                                session = session,
                                date = date,
                                slot = slot,
                                actorId = user?.uid.orEmpty()
                            )
                        }
                        if (result.isSuccess) {
                            capacitySnapshot = withContext(Dispatchers.IO) { loadSpaCapacitySnapshot(db, dateOptions) }
                        }
                        snackbarHostState.showSnackbar(if (result.isSuccess) "Đã đổi lịch buổi điều trị" else result.exceptionOrNull()?.message ?: "Đổi lịch thất bại")
                        isBusy = false
                        rescheduleTarget = null
                    }
                }
            )
        }
    }

}

@Composable
private fun DetailHeader(
    appointment: SpaAppointment,
    packageImageUrl: String,
    progressLabel: String?,
    onBack: () -> Unit
) {
    val meta = appointmentStatusMeta(appointment.status)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f))
                ) { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) }
                Spacer(Modifier.width(10.dp))
                Text("Quản lí liệu trình", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.22f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(meta.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 104.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (packageImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = packageImageUrl,
                            contentDescription = "Ảnh gói ${appointment.spaPackageName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        appointment.spaPackageName,
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color.White.copy(0.84f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (progressLabel != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(0.18f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text("Tiến độ $progressLabel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE4F3EF)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun InfoText(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label:", color = Color(0xFF8ACABA), fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Text(value, color = Color(0xFF4A7A70), fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TreatmentPlanSummary(plan: TreatmentPlan) {
    val progress = if (plan.sessionCount > 0) {
        (plan.completedSessionCount.toFloat() / plan.sessionCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF8FFFE))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.packageName, color = Color(0xFF173F37), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("Theo dõi tiến độ và nhắc ảnh sau từng buổi", color = Color(0xFF6C8F87), fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            SoftStatusChip(label = treatmentPlanStatusMeta(plan.status).label, color = MintGreen, background = Color(0xFFEAF9F5))
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(20.dp)),
            color = MintGreen,
            trackColor = Color(0xFFE1F1ED)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TreatmentMetric("Hoàn thành", "${plan.completedSessionCount}/${plan.sessionCount}", Modifier.weight(1f))
            TreatmentMetric("Thời lượng", "${plan.durationPerSessionMinutes} phút", Modifier.weight(1f))
            TreatmentMetric("Giãn cách", "${plan.suggestedIntervalDays} ngày", Modifier.weight(1f))
        }
        if (plan.requiresProgressPhotos) {
            Spacer(Modifier.height(10.dp))
            SoftInfoBanner(
                icon = Icons.Default.Image,
                text = "Ảnh tiến trình: ${progressPhotoPolicyMeta(plan.photoPolicy).label}"
            )
        }
        if (plan.recommendationNote.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            SoftInfoBanner(
                icon = Icons.Default.EditNote,
                text = plan.recommendationNote
            )
        }
    }
}

@Composable
private fun TreatmentSessionCard(
    session: TreatmentSession,
    photos: List<TreatmentProgressPhoto>,
    canManage: Boolean,
    onUpload: (String) -> Unit,
    onComplete: () -> Unit,
    onReschedule: () -> Unit
) {
    val meta = treatmentSessionStatusMeta(session.status)
    val hasConcreteSchedule = session.scheduledStartAt > 0L && session.timeSlotLabel.isNotBlank()
    val canUploadProgressPhotos = session.status == TreatmentSessionStatus.COMPLETED
    val canConfirmOrReschedule = session.status == TreatmentSessionStatus.SCHEDULED ||
        session.status == TreatmentSessionStatus.RESCHEDULED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF8FFFE))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(sessionStatusBg(session.status)),
                contentAlignment = Alignment.Center
            ) {
                Text(session.sessionNumber.toString(), color = sessionStatusColor(session.status), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Buổi ${session.sessionNumber}/${session.totalSessions}", color = Color(0xFF173F37), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    session.dateLabel.ifBlank { "Chưa lên lịch cụ thể" } + session.timeSlotLabel.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty(),
                    color = Color(0xFF5A8A80),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftStatusChip(label = meta.label, color = sessionStatusColor(session.status), background = sessionStatusBg(session.status))
        }
        if (session.requiresProgressPhotos) {
            Spacer(Modifier.height(10.dp))
            SoftInfoBanner(
                icon = Icons.Default.Image,
                text = "Yêu cầu ảnh: ${progressPhotoPolicyMeta(session.photoPolicy).label}"
            )
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    photos.take(3).forEach { photo ->
                        ProgressPhotoThumb(photo = photo, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (photos.isEmpty() && canUploadProgressPhotos) {
                Spacer(Modifier.height(6.dp))
                Text("Chưa có ảnh cho buổi này.", color = Color(0xFF8ACABA), fontSize = 12.sp)
            } else if (!canUploadProgressPhotos) {
                Spacer(Modifier.height(6.dp))
                Text("Ảnh trước/sau sẽ được thêm sau khi xác nhận hoàn thành buổi.", color = Color(0xFF8ACABA), fontSize = 12.sp)
            }
            if (canUploadProgressPhotos) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onUpload(ProgressPhotoType.BEFORE) }, enabled = canManage, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("\u1EA2nh tr\u01B0\u1EDBc", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onUpload(ProgressPhotoType.AFTER) }, enabled = canManage, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Ảnh sau", fontSize = 12.sp)
                    }
                }
            }
        }
        if (session.photoSkipReason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("B\u1ECF qua \u1EA3nh: ${session.photoSkipReason}", color = Color(0xFFE8A44A), fontSize = 12.sp)
        }
        if (!hasConcreteSchedule && session.status in setOf(TreatmentSessionStatus.UNSCHEDULED, TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED)) {
            Spacer(Modifier.height(8.dp))
            Text("\u0110ang ch\u1EDD kh\u00E1ch ch\u1ECDn ng\u00E0y gi\u1EDD cho bu\u1ED5i n\u00E0y.", color = Color(0xFF8ACABA), fontSize = 12.sp)
        } else if (canConfirmOrReschedule) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReschedule, enabled = canManage, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7B61D1))) {
                    Text("Đổi lịch", fontSize = 12.sp)
                }
                Button(onClick = onComplete, enabled = canManage, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MintGreen)) {
                    Text("Xác nhận", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TreatmentMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color(0xFF173F37), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF6C8F87), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SoftStatusChip(label: String, color: Color, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SoftInfoBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = Color(0xFF5A756F), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProgressPhotoThumb(photo: TreatmentProgressPhoto, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = progressPhotoTypeMeta(photo.photoType).label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEAF9F5))
        )
        Spacer(Modifier.height(4.dp))
        Text(progressPhotoTypeMeta(photo.photoType).label, color = Color(0xFF6C8F87), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RescheduleSessionDialog(
    appointment: SpaAppointment,
    plan: TreatmentPlan?,
    session: TreatmentSession,
    dateOptions: List<BookingDateOption>,
    capacitySnapshot: SpaCapacitySnapshot?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BookingDateOption, String) -> Unit
) {
    val duration = (plan?.durationPerSessionMinutes ?: appointment.durationMinutes).coerceAtLeast(30)
    var selectedDate by remember { mutableStateOf(dateOptions.firstOrNull()) }
    var selectedSlot by remember { mutableStateOf("") }
    val availabilityByDate = remember(capacitySnapshot, duration, dateOptions) {
        val snapshot = capacitySnapshot ?: return@remember emptyMap()
        dateOptions.associate { date ->
            date.startOfDayMillis to buildSpaSlotAvailability(date, duration, snapshot)
        }
    }
    val availableSlots = remember(selectedDate, availabilityByDate) {
        selectedDate?.let { date ->
            availabilityByDate[date.startOfDayMillis]?.filter { it.selectable }.orEmpty()
        }.orEmpty()
    }
    LaunchedEffect(selectedDate?.startOfDayMillis, availableSlots) {
        if (selectedSlot !in availableSlots.map { it.slot }) {
            selectedSlot = availableSlots.firstOrNull()?.slot.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Đổi lịch buổi ${session.sessionNumber}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Chọn ngày và giờ còn chỗ. Các khung giờ đã kín hoặc trùng lịch sẽ không hiện để chọn.", color = Color(0xFF6C8F87), fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Ngày", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(dateOptions.chunked(3)) { rowDates ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            rowDates.forEach { date ->
                                val availability = availabilityByDate[date.startOfDayMillis].orEmpty()
                                val selectable = availability.any { it.selectable }
                                val selected = selectedDate?.startOfDayMillis == date.startOfDayMillis
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) MintGreen else if (selectable) Color(0xFFF8FFFE) else Color(0xFFF2F2F2))
                                        .clickable(enabled = selectable && !isBusy) { selectedDate = date }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(date.compactLabel, color = if (selected) Color.White else if (selectable) Color(0xFF1A4A40) else Color(0xFFB8B8B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            repeat(3 - rowDates.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Giờ còn chỗ", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                when {
                    capacitySnapshot == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MintGreen, modifier = Modifier.size(24.dp))
                    }
                    availableSlots.isEmpty() -> Text("Ngày này không còn khung giờ phù hợp.", color = Color(0xFFE8A44A), fontSize = 13.sp)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableSlots.chunked(3).forEach { rowSlots ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                rowSlots.forEach { availability ->
                                    val selected = selectedSlot == availability.slot
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) MintGreen else Color(0xFFF8FFFE))
                                            .clickable(enabled = !isBusy) { selectedSlot = availability.slot }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(availability.slot, color = if (selected) Color.White else Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Còn ${availability.remainingCapacity}", color = if (selected) Color.White.copy(0.85f) else MintGreen, fontSize = 9.sp)
                                        }
                                    }
                                }
                                repeat(3 - rowSlots.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val date = selectedDate ?: return@Button
                    if (selectedSlot.isNotBlank()) onConfirm(date, selectedSlot)
                },
                enabled = !isBusy && selectedDate != null && selectedSlot.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) { Text("Xác nhận đổi lịch") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Hủy", color = MintGreen) } }
    )
}

@Composable
private fun EmptyDetail(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventBusy, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("Không tìm thấy lịch hẹn", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = MintGreen)) { Text("Quay l\u1EA1i") }
        }
    }
}

private suspend fun ensureChatThread(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    treatmentPlanId: String = ""
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val threadId = appointment.userId.takeIf { it.isNotBlank() }?.let { customerConsultationThreadId(it) } ?: appointment.id
        db.collection("consultation_chat_threads").document(threadId).set(
            mapOf(
                "appointmentId" to appointment.id,
                "treatmentPlanId" to treatmentPlanId,
                "userId" to appointment.userId,
                "userEmail" to appointment.userEmail,
                "userName" to appointment.userName,
                "consultantId" to appointment.consultantId,
                "consultantEmail" to appointment.consultantEmail,
                "consultantName" to appointment.consultantName,
                "status" to ChatThreadStatus.ACTIVE,
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).await()
    }
}

private suspend fun updateSessionStatus(
    db: FirebaseFirestore,
    session: TreatmentSession,
    status: String,
    actorId: String,
    extra: Map<String, Any> = emptyMap()
) {
    withContext(Dispatchers.IO) {
        val sessionRef = db.collection("treatment_sessions").document(session.id)
        val planRef = db.collection("treatment_plans").document(session.treatmentPlanId)
        val appointmentRef = session.appointmentId.takeIf { it.isNotBlank() }?.let { db.collection("appointments").document(it) }
        db.runTransaction { tx ->
            val currentSession = tx.get(sessionRef)
            val previousStatus = currentSession.getString("status") ?: TreatmentSessionStatus.SCHEDULED
            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to now
            )
            updates.putAll(extra)
            when (status) {
                TreatmentSessionStatus.COMPLETED -> {
                    updates["completedAt"] = now
                    updates["completedBy"] = actorId
                    if (previousStatus != TreatmentSessionStatus.COMPLETED) {
                        val currentPlan = tx.get(planRef)
                        val completedCount = ((currentPlan.getLong("completedSessionCount") ?: 0L).toInt() + 1)
                        val sessionCount = ((currentPlan.getLong("sessionCount") ?: 1L).toInt()).coerceAtLeast(1)
                        val planUpdates = mutableMapOf<String, Any>(
                            "completedSessionCount" to FieldValue.increment(1),
                            "updatedAt" to now
                        )
                        if (completedCount >= sessionCount) {
                            planUpdates["status"] = TreatmentPlanStatus.COMPLETED
                            planUpdates["completedAt"] = now
                            val planUserId = currentPlan.getString("userId").orEmpty()
                            val planPackageId = currentPlan.getString("spaPackageId").orEmpty()
                            if (planUserId.isNotBlank() && planPackageId.isNotBlank()) {
                                tx.delete(db.collection(ACTIVE_TREATMENT_PLAN_KEYS_COLLECTION).document(activeTreatmentPlanKey(planUserId, planPackageId)))
                            }
                        }
                        tx.update(planRef, planUpdates)
                    }
                    appointmentRef?.let { tx.update(it, mapOf("status" to AppointmentStatus.COMPLETED, "completedAt" to now, "updatedAt" to now)) }
                }
                TreatmentSessionStatus.NO_SHOW -> {
                    updates["noShowAt"] = now
                    updates["noShowBy"] = actorId
                    appointmentRef?.let { tx.update(it, mapOf("status" to AppointmentStatus.NO_SHOW, "updatedAt" to now)) }
                }
                TreatmentSessionStatus.CANCELLED -> {
                    updates["cancelledAt"] = now
                    updates["cancelledBy"] = actorId
                    appointmentRef?.let { tx.update(it, mapOf("status" to AppointmentStatus.CANCELLED, "cancelledAt" to now, "updatedAt" to now, "cancelReason" to "Consultant cancelled session")) }
                }
                TreatmentSessionStatus.RESCHEDULED -> {
                    updates["rescheduledAt"] = now
                    updates["rescheduledBy"] = actorId
                    updates["previousScheduledStartAt"] = session.scheduledStartAt
                    updates["previousScheduledEndAt"] = session.scheduledEndAt
                    appointmentRef?.let { tx.update(it, mapOf("status" to AppointmentStatus.RESCHEDULED, "updatedAt" to now)) }
                }
            }
            tx.update(sessionRef, updates)
            null
        }.await()
    }
}

private suspend fun rescheduleTreatmentSessionWithCapacity(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    plan: TreatmentPlan?,
    session: TreatmentSession,
    date: BookingDateOption,
    slot: String,
    actorId: String
) {
    withContext(Dispatchers.IO) {
        val duration = (plan?.durationPerSessionMinutes ?: appointment.durationMinutes).coerceAtLeast(30)
        val (startAt, endAt) = appointmentTimeRange(date.startOfDayMillis, slot, duration)
        val capacitySnapshot = loadSpaCapacitySnapshot(db, listOf(date))
        val availability = buildSpaSlotAvailability(date, duration, capacitySnapshot)
            .firstOrNull { it.slot == slot }
        val oldBlockKeys = appointment.reservedBlockKeys
        val newBlockStartTimes = capacityBlockStartTimes(startAt, endAt, capacitySnapshot.settings.slotMinutes)
        val newBlockKeys = capacityBlockKeys(startAt, endAt, capacitySnapshot.settings.slotMinutes)
        val choosingOnlyCurrentReservedBlocks = newBlockKeys.isNotEmpty() && newBlockKeys.all { it in oldBlockKeys }
        if (availability?.selectable != true && !choosingOnlyCurrentReservedBlocks) {
            throw IllegalStateException(availability?.reason ?: "Khung giờ này không khả dụng, vui lòng chọn giờ khác")
        }
        val effective = resolveEffectiveSpaCapacity(
            settings = capacitySnapshot.settings,
            override = capacitySnapshot.overridesByDateKey[spaDateKey(date.startOfDayMillis)],
            dateStartMillis = date.startOfDayMillis
        )
        db.runTransaction { tx ->
            val now = System.currentTimeMillis()
            val appointmentRef = db.collection("appointments").document(appointment.id)
            val sessionRef = db.collection("treatment_sessions").document(session.id)
            val latestAppointment = tx.get(appointmentRef)
            val latestSession = tx.get(sessionRef)
            if (!latestAppointment.exists() || !latestSession.exists()) {
                throw IllegalStateException("Không tìm thấy lịch điều trị")
            }
            if (latestAppointment.getString("consultantId") != appointment.consultantId ||
                latestSession.getString("treatmentPlanId") != session.treatmentPlanId
            ) {
                throw IllegalStateException("Lịch điều trị không hợp lệ")
            }

            val oldOnlyKeys = oldBlockKeys.filterNot { it in newBlockKeys }
            val newOnlyKeys = newBlockKeys.filterNot { it in oldBlockKeys }
            val oldOnlyRefs = oldOnlyKeys.map { db.collection(APPOINTMENT_CAPACITY_BLOCKS_COLLECTION).document(it) }
            val newOnlyRefs = newOnlyKeys.map { db.collection(APPOINTMENT_CAPACITY_BLOCKS_COLLECTION).document(it) }
            val oldOnlySnaps = oldOnlyRefs.map { tx.get(it) }
            val newOnlySnaps = newOnlyRefs.map { tx.get(it) }

            newOnlySnaps.forEachIndexed { index, snap ->
                val key = newOnlyKeys[index]
                val currentCount = (snap.getLong("bookedCount") ?: 0L).toInt()
                val closed = snap.getBoolean("closed") == true
                val capacity = ((snap.getLong("capacity") ?: effective.capacity.toLong()).toInt()).coerceAtLeast(1)
                if (closed || currentCount >= capacity) {
                    throw IllegalStateException("Khung ${key.substringAfter('_').replace('-', ':')} vừa hết chỗ, vui lòng chọn giờ khác")
                }
            }

            oldOnlyRefs.forEachIndexed { index, ref ->
                val snap = oldOnlySnaps[index]
                if (snap.exists()) {
                    val ids = (snap.get("appointmentIds") as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                        .filterNot { it == appointment.id }
                    val count = ((snap.getLong("bookedCount") ?: 0L).toInt() - 1).coerceAtLeast(0)
                    tx.update(ref, mapOf("bookedCount" to count, "appointmentIds" to ids, "updatedAt" to now))
                }
            }

            newOnlyRefs.forEachIndexed { index, ref ->
                val snap = newOnlySnaps[index]
                val key = newOnlyKeys[index]
                val existingIds = (snap.get("appointmentIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val count = (snap.getLong("bookedCount") ?: 0L).toInt()
                val blockStartAt = newBlockStartTimes.getOrNull(newBlockKeys.indexOf(key)) ?: startAt
                tx.set(
                    ref,
                    mapOf(
                        "dateKey" to spaDateKey(date.startOfDayMillis),
                        "blockStartAt" to blockStartAt,
                        "blockLabel" to slotLabelFromMillis(blockStartAt),
                        "capacity" to ((snap.getLong("capacity") ?: effective.capacity.toLong()).toInt()).coerceAtLeast(1),
                        "bookedCount" to count + 1,
                        "appointmentIds" to (existingIds + appointment.id).distinct(),
                        "closed" to false,
                        "updatedAt" to now
                    )
                )
            }

            tx.update(
                appointmentRef,
                mapOf(
                    "startAt" to startAt,
                    "endAt" to endAt,
                    "appointmentDateLabel" to date.label,
                    "timeSlotLabel" to slot,
                    "status" to AppointmentStatus.RESCHEDULED,
                    "durationMinutes" to duration,
                    "reservedBlockKeys" to newBlockKeys,
                    "updatedAt" to now
                )
            )
            tx.update(
                sessionRef,
                mapOf(
                    "scheduledStartAt" to startAt,
                    "scheduledEndAt" to endAt,
                    "dateLabel" to date.label,
                    "timeSlotLabel" to slot,
                    "status" to TreatmentSessionStatus.RESCHEDULED,
                    "rescheduledAt" to now,
                    "rescheduledBy" to actorId,
                    "rescheduleReason" to "Tư vấn viên đổi lịch",
                    "previousScheduledStartAt" to session.scheduledStartAt,
                    "previousScheduledEndAt" to session.scheduledEndAt,
                    "updatedAt" to now
                )
            )
            null
        }.await()
    }
}

private suspend fun uploadProgressPhoto(
    context: android.content.Context,
    db: FirebaseFirestore,
    imageUri: Uri,
    appointment: SpaAppointment,
    plan: TreatmentPlan,
    session: TreatmentSession,
    photoType: String,
    angle: String,
    note: String,
    userId: String,
    uploaderName: String
) {
    withContext(Dispatchers.IO) {
        val imageUrl = CloudinaryHelper.uploadImage(context, imageUri)
        val photo = TreatmentProgressPhoto(
            treatmentPlanId = plan.id,
            treatmentSessionId = session.id,
            appointmentId = appointment.id,
            userId = appointment.userId,
            consultantId = appointment.consultantId,
            photoType = photoType,
            angle = angle,
            imageUrl = imageUrl,
            note = note,
            uploadedBy = userId,
            uploaderName = uploaderName
        )
        db.collection("treatment_progress_photos").add(photo.toFirestoreMap(includeCreatedAt = true)).await()
    }
}

private fun sessionStatusColor(status: String): Color = when (status) {
    TreatmentSessionStatus.UNSCHEDULED -> Color(0xFF8ACABA)
    TreatmentSessionStatus.SCHEDULED -> Color(0xFF4A90D9)
    TreatmentSessionStatus.COMPLETED -> MintGreen
    TreatmentSessionStatus.CANCELLED -> Color(0xFFE57373)
    TreatmentSessionStatus.NO_SHOW -> Color(0xFFE8A44A)
    TreatmentSessionStatus.RESCHEDULED -> Color(0xFF7B61D1)
    else -> Color(0xFF8ACABA)
}

private fun sessionStatusBg(status: String): Color = when (status) {
    TreatmentSessionStatus.UNSCHEDULED -> Color(0xFFF5F5F5)
    TreatmentSessionStatus.SCHEDULED -> Color(0xFFE8F0FB)
    TreatmentSessionStatus.COMPLETED -> Color(0xFFEAF9F5)
    TreatmentSessionStatus.CANCELLED -> Color(0xFFFFECEC)
    TreatmentSessionStatus.NO_SHOW -> Color(0xFFFFF3E0)
    TreatmentSessionStatus.RESCHEDULED -> Color(0xFFF0ECFF)
    else -> Color(0xFFF5F5F5)
}

@Composable
private fun detailTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
