package com.example.appbanmypham.ui.consultant

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.data.CloudinaryHelper
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.ChatSenderRole
import com.example.appbanmypham.model.ChatThreadStatus
import com.example.appbanmypham.model.ConsultationChatMessage
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.ProgressPhotoPolicy
import com.example.appbanmypham.model.ProgressPhotoType
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.SpaPackageType
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentPlanStatus
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.TreatmentSessionStatus
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToConsultationChatMessage
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.firestoreDocToTreatmentSession
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.progressPhotoTypeMeta
import com.example.appbanmypham.model.toFirestoreMap
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
    var messages by remember { mutableStateOf(listOf<ConsultationChatMessage>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var consultantNote by remember { mutableStateOf("") }
    var recommendationNote by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var customPlanName by remember { mutableStateOf("") }
    var customSessionCount by remember { mutableStateOf("3") }
    var customDuration by remember { mutableStateOf("60") }
    var skipTarget by remember { mutableStateOf<TreatmentSession?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var photoTarget by remember { mutableStateOf<Pair<TreatmentSession, String>?>(null) }
    var photoAngle by remember { mutableStateOf("") }
    var photoNote by remember { mutableStateOf("") }

    val currentAppointment = appointment
    val canManage = user != null && currentAppointment?.consultantId == user.uid
    val threadId = chatThread?.id ?: currentAppointment?.id.orEmpty()

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
                        uploaderName = signedUser.displayName ?: signedUser.email?.substringBefore("@") ?: "Consultant"
                    )
                }
                snackbarHostState.showSnackbar(if (result.isSuccess) "Da up anh tien trinh" else result.exceptionOrNull()?.message ?: "Upload that bai")
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
        regs += db.collection("consultation_chat_threads").document(appointmentId)
            .addSnapshotListener { snap, _ ->
                chatThread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
            }
        regs += db.collection("consultation_chat_messages")
            .whereEqualTo("threadId", appointmentId)
            .addSnapshotListener { snap, _ ->
                messages = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatMessage(it) }.getOrNull() }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        regs += db.collection("treatment_plans")
            .whereEqualTo("appointmentId", appointmentId)
            .addSnapshotListener { snap, _ ->
                treatmentPlan = snap?.documents?.firstOrNull()?.let { runCatching { firestoreDocToTreatmentPlan(it) }.getOrNull() }
                recommendationNote = treatmentPlan?.recommendationNote.orEmpty()
            }
        onDispose { regs.forEach { it.remove() } }
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

    LaunchedEffect(currentAppointment?.id, currentAppointment?.consultantId, chatThread?.id) {
        val appt = currentAppointment ?: return@LaunchedEffect
        if (appt.consultantId.isNotBlank() && chatThread == null) {
            runCatching { ensureChatThread(db, appt, treatmentPlan?.id.orEmpty()) }
        }
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
                    DetailHeader(appointment = currentAppointment, onBack = onBack)
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Person, "Thong tin khach")
                        InfoText("Khach", currentAppointment.userName.ifBlank { currentAppointment.userEmail.ifBlank { "Khach hang" } })
                        InfoText("SDT", currentAppointment.phoneNumber.ifBlank { "-" })
                        InfoText("Goi spa", currentAppointment.spaPackageName)
                        InfoText("Lich", "${currentAppointment.appointmentDateLabel} - ${currentAppointment.timeSlotLabel}")
                        if (currentAppointment.note.isNotBlank()) InfoText("Khach ghi chu", currentAppointment.note)
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.EditNote, "Ghi chu tu van")
                        OutlinedTextField(
                            value = consultantNote,
                            onValueChange = { consultantNote = it },
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                            enabled = canManage && !isBusy,
                            placeholder = { Text("Tinh trang da, muc tieu, dieu can luu y...", color = Color(0xFFAAD8CE)) },
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
                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Da luu ghi chu" else "Luu ghi chu that bai")
                                    isBusy = false
                                }
                            },
                            enabled = canManage && !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Luu ghi chu") }
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Chat, "Chat voi khach")
                        if (chatThread == null) {
                            Text("Chat se mo sau khi lich duoc gan tu van vien.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                        } else {
                            ChatMessages(messages = messages, currentUserId = user?.uid.orEmpty())
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier.weight(1f),
                                    enabled = canManage && !isBusy,
                                    placeholder = { Text("Nhap tin nhan...", color = Color(0xFFAAD8CE)) },
                                    colors = detailTextFieldColors(),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val text = messageText.trim()
                                        if (text.isBlank() || user == null) return@IconButton
                                        scope.launch {
                                            isBusy = true
                                            val result = runCatching {
                                                sendChatMessage(
                                                    db = db,
                                                    threadId = threadId,
                                                    appointment = currentAppointment,
                                                    plan = treatmentPlan,
                                                    senderId = user.uid,
                                                    senderName = user.displayName ?: user.email?.substringBefore("@") ?: "Consultant",
                                                    senderRole = ChatSenderRole.CONSULTANT,
                                                    message = text
                                                )
                                            }
                                            if (result.isSuccess) messageText = ""
                                            snackbarHostState.showSnackbar(if (result.isSuccess) "Da gui" else "Gui tin that bai")
                                            isBusy = false
                                        }
                                    },
                                    enabled = canManage && messageText.isNotBlank() && !isBusy,
                                    modifier = Modifier.size(46.dp).clip(CircleShape).background(MintGreen)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
                item {
                    DetailCard {
                        SectionTitle(Icons.Default.Spa, "Lieu trinh")
                        if (treatmentPlan == null) {
                            CreatePlanPanel(
                                appointment = currentAppointment,
                                spaPackage = spaPackage,
                                customPlanName = customPlanName,
                                onCustomPlanNameChange = { customPlanName = it },
                                customSessionCount = customSessionCount,
                                onCustomSessionCountChange = { customSessionCount = it },
                                customDuration = customDuration,
                                onCustomDurationChange = { customDuration = it },
                                recommendationNote = recommendationNote,
                                onRecommendationNoteChange = { recommendationNote = it },
                                canManage = canManage && !isBusy,
                                onCreatePlan = {
                                    scope.launch {
                                        isBusy = true
                                        val result = runCatching {
                                            createTreatmentPlanFromAppointment(
                                                db = db,
                                                appointment = currentAppointment,
                                                spaPackage = spaPackage,
                                                customPlanName = customPlanName.trim(),
                                                customSessionCount = customSessionCount.toIntOrNull() ?: 1,
                                                customDuration = customDuration.toIntOrNull() ?: currentAppointment.durationMinutes,
                                                consultationNote = consultantNote.trim(),
                                                recommendationNote = recommendationNote.trim()
                                            )
                                        }
                                        snackbarHostState.showSnackbar(if (result.isSuccess) "Da tao lieu trinh" else result.exceptionOrNull()?.message ?: "Tao lieu trinh that bai")
                                        isBusy = false
                                    }
                                }
                            )
                        } else {
                            treatmentPlan?.let { TreatmentPlanSummary(plan = it) }
                        }
                    }
                }
                if (treatmentPlan != null) {
                    item {
                        DetailCard {
                            SectionTitle(Icons.Default.EventAvailable, "Cac buoi dieu tri")
                            if (sessions.isEmpty()) {
                                Text("Chua co buoi dieu tri nao.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    sessions.forEach { session ->
                                        val sessionPhotos = photos.filter { it.treatmentSessionId == session.id }
                                        TreatmentSessionCard(
                                            session = session,
                                            photos = sessionPhotos,
                                            canManage = canManage && !isBusy,
                                            onUpload = { type ->
                                                photoTarget = session to type
                                            },
                                            onComplete = {
                                                scope.launch {
                                                    val missing = missingRequiredPhotoTypes(session, sessionPhotos)
                                                    if (missing.isNotEmpty()) {
                                                        skipTarget = session
                                                        skipReason = ""
                                                    } else {
                                                        isBusy = true
                                                        val result = runCatching { updateSessionStatus(db, session, TreatmentSessionStatus.COMPLETED, user?.uid.orEmpty()) }
                                                        snackbarHostState.showSnackbar(if (result.isSuccess) "Da hoan thanh buoi" else "Cap nhat that bai")
                                                        isBusy = false
                                                    }
                                                }
                                            },
                                            onNoShow = {
                                                scope.launch {
                                                    isBusy = true
                                                    val result = runCatching { updateSessionStatus(db, session, TreatmentSessionStatus.NO_SHOW, user?.uid.orEmpty()) }
                                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Da danh dau khach khong den" else "Cap nhat that bai")
                                                    isBusy = false
                                                }
                                            },
                                            onCancel = {
                                                scope.launch {
                                                    isBusy = true
                                                    val result = runCatching { updateSessionStatus(db, session, TreatmentSessionStatus.CANCELLED, user?.uid.orEmpty()) }
                                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Da huy buoi" else "Cap nhat that bai")
                                                    isBusy = false
                                                }
                                            },
                                            onReschedule = {
                                                scope.launch {
                                                    isBusy = true
                                                    val result = runCatching {
                                                        updateSessionStatus(
                                                            db = db,
                                                            session = session,
                                                            status = TreatmentSessionStatus.RESCHEDULED,
                                                            actorId = user?.uid.orEmpty(),
                                                            extra = mapOf("rescheduleReason" to "Consultant marked for reschedule")
                                                        )
                                                    }
                                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Da danh dau doi lich" else "Cap nhat that bai")
                                                    isBusy = false
                                                }
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

    if (photoTarget != null) {
        AlertDialog(
            onDismissRequest = { photoTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Thong tin anh tien trinh", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = photoAngle,
                        onValueChange = { photoAngle = it },
                        label = { Text("Goc chup") },
                        placeholder = { Text("Mat truoc, trai, phai...") },
                        colors = detailTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = photoNote,
                        onValueChange = { photoNote = it },
                        label = { Text("Ghi chu") },
                        colors = detailTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { photoLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) { Text("Chon anh") }
            },
            dismissButton = { TextButton(onClick = { photoTarget = null }) { Text("Huy", color = MintGreen) } }
        )
    }

    skipTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { skipTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Thieu anh tien trinh", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text = {
                Column {
                    Text("Buoi nay dang yeu cau anh tien trinh. Neu van hoan thanh, can ghi ly do bo qua.", color = Color(0xFF5A8A80), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = skipReason,
                        onValueChange = { skipReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("VD: Khach tu choi chup anh") },
                        colors = detailTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (skipReason.isBlank()) {
                                snackbarHostState.showSnackbar("Vui long nhap ly do bo qua anh")
                                return@launch
                            }
                            isBusy = true
                            val result = runCatching {
                                updateSessionStatus(
                                    db = db,
                                    session = session.copy(photoSkipReason = skipReason.trim()),
                                    status = TreatmentSessionStatus.COMPLETED,
                                    actorId = user?.uid.orEmpty(),
                                    extra = mapOf("photoSkipReason" to skipReason.trim())
                                )
                            }
                            snackbarHostState.showSnackbar(if (result.isSuccess) "Da hoan thanh buoi" else "Cap nhat that bai")
                            isBusy = false
                            skipTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) { Text("Hoan thanh") }
            },
            dismissButton = { TextButton(onClick = { skipTarget = null }) { Text("Quay lai", color = MintGreen) } }
        )
    }
}

@Composable
private fun DetailHeader(appointment: SpaAppointment, onBack: () -> Unit) {
    val meta = appointmentStatusMeta(appointment.status)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f)).align(Alignment.TopStart)
        ) { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) }
        Column(modifier = Modifier.padding(top = 52.dp)) {
            Text("LICH TU VAN", color = Color.White.copy(0.74f), fontSize = 11.sp, letterSpacing = 2.sp)
            Text(appointment.spaPackageName, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.22f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(meta.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color.White.copy(0.82f), fontSize = 12.sp)
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
private fun ChatMessages(messages: List<ConsultationChatMessage>, currentUserId: String) {
    if (messages.isEmpty()) {
        Text("Chua co tin nhan nao.", color = Color(0xFF8ACABA), fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        messages.takeLast(8).forEach { message ->
            val mine = message.senderId == currentUserId
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (mine) Color(0xFFEAF9F5) else Color(0xFFF7F7F7))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(message.senderName.ifBlank { message.senderRole }, color = if (mine) MintGreen else Color(0xFF8ACABA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(message.message, color = Color(0xFF1A4A40), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CreatePlanPanel(
    appointment: SpaAppointment,
    spaPackage: SpaPackage?,
    customPlanName: String,
    onCustomPlanNameChange: (String) -> Unit,
    customSessionCount: String,
    onCustomSessionCountChange: (String) -> Unit,
    customDuration: String,
    onCustomDurationChange: (String) -> Unit,
    recommendationNote: String,
    onRecommendationNoteChange: (String) -> Unit,
    canManage: Boolean,
    onCreatePlan: () -> Unit
) {
    val isTemplate = spaPackage?.packageType == SpaPackageType.TREATMENT_TEMPLATE
    Text(
        if (isTemplate) "Goi nay la template lieu trinh. Tao ke hoach rieng cho khach tu thong tin goi." else "Goi nay la dich vu le. Ban co the tao lieu trinh tuy chinh neu can.",
        color = Color(0xFF5A8A80),
        fontSize = 13.sp
    )
    Spacer(Modifier.height(10.dp))
    if (!isTemplate) {
        OutlinedTextField(
            value = customPlanName,
            onValueChange = onCustomPlanNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canManage,
            label = { Text("Ten lieu trinh tuy chinh") },
            placeholder = { Text(appointment.spaPackageName) },
            colors = detailTextFieldColors(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = if (isTemplate) (spaPackage?.sessionCount ?: 1).toString() else customSessionCount,
            onValueChange = onCustomSessionCountChange,
            modifier = Modifier.weight(1f),
            enabled = canManage && !isTemplate,
            label = { Text("So buoi") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = detailTextFieldColors(),
            shape = RoundedCornerShape(14.dp)
        )
        OutlinedTextField(
            value = if (isTemplate) (spaPackage?.durationPerSessionMinutes ?: appointment.durationMinutes).toString() else customDuration,
            onValueChange = onCustomDurationChange,
            modifier = Modifier.weight(1f),
            enabled = canManage && !isTemplate,
            label = { Text("Phut/buoi") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = detailTextFieldColors(),
            shape = RoundedCornerShape(14.dp)
        )
    }
    if (spaPackage?.requiresProgressPhotos == true) {
        Spacer(Modifier.height(8.dp))
        Text("Anh tien trinh: ${progressPhotoPolicyMeta(spaPackage.photoPolicy).label}", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (spaPackage.photoGuide.isNotBlank()) Text(spaPackage.photoGuide, color = Color(0xFF6C8F87), fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = recommendationNote,
        onValueChange = onRecommendationNoteChange,
        modifier = Modifier.fillMaxWidth().height(86.dp),
        enabled = canManage,
        label = { Text("De xuat lieu trinh hien cho khach") },
        colors = detailTextFieldColors(),
        shape = RoundedCornerShape(14.dp)
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onCreatePlan,
        enabled = canManage,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
        shape = RoundedCornerShape(14.dp)
    ) { Text("Tao lieu trinh") }
}

@Composable
private fun TreatmentPlanSummary(plan: TreatmentPlan) {
    InfoText("Ten", plan.packageName)
    InfoText("Trang thai", plan.status)
    InfoText("So buoi", "${plan.completedSessionCount}/${plan.sessionCount}")
    InfoText("Thoi luong", "${plan.durationPerSessionMinutes} phut/buoi")
    if (plan.recommendationNote.isNotBlank()) InfoText("De xuat", plan.recommendationNote)
    if (plan.requiresProgressPhotos) InfoText("Anh", progressPhotoPolicyMeta(plan.photoPolicy).label)
}

@Composable
private fun TreatmentSessionCard(
    session: TreatmentSession,
    photos: List<TreatmentProgressPhoto>,
    canManage: Boolean,
    onUpload: (String) -> Unit,
    onComplete: () -> Unit,
    onNoShow: () -> Unit,
    onCancel: () -> Unit,
    onReschedule: () -> Unit
) {
    val meta = treatmentSessionStatusMeta(session.status)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8FFFE)).padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Buoi ${session.sessionNumber}/${session.totalSessions}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(session.dateLabel.ifBlank { "Chua len lich cu the" } + session.timeSlotLabel.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty(), color = Color(0xFF5A8A80), fontSize = 12.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(sessionStatusBg(session.status)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(meta.label, color = sessionStatusColor(session.status), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (session.requiresProgressPhotos) {
            Spacer(Modifier.height(8.dp))
            Text("Yeu cau anh: ${progressPhotoPolicyMeta(session.photoPolicy).label}", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.take(3).forEach { photo ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = photo.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF9F5))
                            )
                            Text(progressPhotoTypeMeta(photo.photoType).label, color = Color(0xFF6C8F87), fontSize = 9.sp)
                        }
                    }
                }
            }
            if (session.status == TreatmentSessionStatus.SCHEDULED || session.status == TreatmentSessionStatus.RESCHEDULED) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onUpload(ProgressPhotoType.BEFORE) }, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Anh truoc", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onUpload(ProgressPhotoType.AFTER) }, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Anh sau", fontSize = 12.sp)
                    }
                }
            }
        }
        if (session.photoSkipReason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Bo qua anh: ${session.photoSkipReason}", color = Color(0xFFE8A44A), fontSize = 12.sp)
        }
        if (session.status == TreatmentSessionStatus.SCHEDULED || session.status == TreatmentSessionStatus.RESCHEDULED) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373))) {
                    Text("Huy", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onReschedule, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7B61D1))) {
                    Text("Doi lich", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNoShow, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE8A44A))) {
                    Text("Khong den", fontSize = 12.sp)
                }
                Button(onClick = onComplete, enabled = canManage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MintGreen)) {
                    Text("Xong", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyDetail(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventBusy, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("Khong tim thay lich hen", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = MintGreen)) { Text("Quay lai") }
        }
    }
}

private suspend fun sendChatMessage(
    db: FirebaseFirestore,
    threadId: String,
    appointment: SpaAppointment,
    plan: TreatmentPlan?,
    senderId: String,
    senderName: String,
    senderRole: String,
    message: String
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val chatMessage = ConsultationChatMessage(
            threadId = threadId,
            appointmentId = appointment.id,
            treatmentPlanId = plan?.id.orEmpty(),
            userId = appointment.userId,
            consultantId = appointment.consultantId,
            senderId = senderId,
            senderName = senderName,
            senderRole = senderRole,
            message = message,
            createdAt = now
        )
        db.collection("consultation_chat_messages").add(chatMessage.toFirestoreMap(includeCreatedAt = true)).await()
        db.collection("consultation_chat_threads").document(threadId).update(
            mapOf(
                "lastMessage" to message,
                "lastMessageAt" to now,
                "updatedAt" to now
            )
        ).await()
    }
}

private suspend fun ensureChatThread(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    treatmentPlanId: String = ""
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.collection("consultation_chat_threads").document(appointment.id).set(
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

private suspend fun createTreatmentPlanFromAppointment(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    spaPackage: SpaPackage?,
    customPlanName: String,
    customSessionCount: Int,
    customDuration: Int,
    consultationNote: String,
    recommendationNote: String
) {
    withContext(Dispatchers.IO) {
        if (appointment.consultantId.isBlank()) throw IllegalStateException("Lich chua co tu van vien")
        val isTemplate = spaPackage?.packageType == SpaPackageType.TREATMENT_TEMPLATE
        val sessionCount = if (isTemplate) spaPackage?.sessionCount ?: 1 else customSessionCount.coerceAtLeast(1)
        val duration = if (isTemplate) {
            spaPackage?.durationPerSessionMinutes?.takeIf { it > 0 } ?: appointment.durationMinutes
        } else {
            customDuration.coerceAtLeast(1)
        }
        val plan = TreatmentPlan(
            appointmentId = appointment.id,
            userId = appointment.userId,
            userEmail = appointment.userEmail,
            userName = appointment.userName,
            phoneNumber = appointment.phoneNumber,
            consultantId = appointment.consultantId,
            consultantEmail = appointment.consultantEmail,
            consultantName = appointment.consultantName,
            spaPackageId = appointment.spaPackageId,
            packageName = if (isTemplate) spaPackage?.name ?: appointment.spaPackageName else customPlanName.ifBlank { appointment.spaPackageName },
            packageType = if (isTemplate) SpaPackageType.TREATMENT_TEMPLATE else SpaPackageType.SINGLE_SESSION,
            category = spaPackage?.category.orEmpty(),
            totalPrice = spaPackage?.price ?: appointment.spaPackagePrice,
            sessionCount = sessionCount,
            durationPerSessionMinutes = duration,
            suggestedIntervalDays = spaPackage?.suggestedIntervalDays ?: 7,
            requiresProgressPhotos = spaPackage?.requiresProgressPhotos == true,
            photoPolicy = spaPackage?.photoPolicy ?: ProgressPhotoPolicy.NONE,
            photoGuide = spaPackage?.photoGuide.orEmpty(),
            status = TreatmentPlanStatus.ACTIVE,
            consultationNote = consultationNote,
            recommendationNote = recommendationNote,
            chatThreadId = appointment.id
        )
        val planRef = db.collection("treatment_plans").add(plan.toFirestoreMap(includeCreatedAt = true)).await()
        val batch = db.batch()
        for (number in 1..sessionCount) {
            val sessionRef = db.collection("treatment_sessions").document()
            val isFirst = number == 1
            val session = TreatmentSession(
                treatmentPlanId = planRef.id,
                appointmentId = if (isFirst) appointment.id else "",
                userId = appointment.userId,
                consultantId = appointment.consultantId,
                spaPackageId = appointment.spaPackageId,
                packageName = plan.packageName,
                sessionNumber = number,
                totalSessions = sessionCount,
                scheduledStartAt = if (isFirst) appointment.startAt else 0L,
                scheduledEndAt = if (isFirst) appointment.endAt else 0L,
                dateLabel = if (isFirst) appointment.appointmentDateLabel else "",
                timeSlotLabel = if (isFirst) appointment.timeSlotLabel else "",
                requiresProgressPhotos = plan.requiresProgressPhotos,
                photoPolicy = plan.photoPolicy
            )
            batch.set(sessionRef, session.toFirestoreMap(includeCreatedAt = true))
        }
        batch.set(
            db.collection("consultation_chat_threads").document(appointment.id),
            mapOf(
                "appointmentId" to appointment.id,
                "treatmentPlanId" to planRef.id,
                "userId" to appointment.userId,
                "userEmail" to appointment.userEmail,
                "userName" to appointment.userName,
                "consultantId" to appointment.consultantId,
                "consultantEmail" to appointment.consultantEmail,
                "consultantName" to appointment.consultantName,
                "status" to ChatThreadStatus.ACTIVE,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
        batch.commit().await()
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
                        tx.update(planRef, mapOf("completedSessionCount" to FieldValue.increment(1), "updatedAt" to now))
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

private fun missingRequiredPhotoTypes(session: TreatmentSession, photos: List<TreatmentProgressPhoto>): List<String> {
    if (!session.requiresProgressPhotos) return emptyList()
    if (session.status !in setOf(TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED)) return emptyList()
    val existingTypes = photos.map { it.photoType }.toSet()
    return when (session.photoPolicy) {
        ProgressPhotoPolicy.AFTER_EACH_SESSION -> listOf(ProgressPhotoType.AFTER).filterNot { it in existingTypes }
        ProgressPhotoPolicy.BEFORE_AFTER_EACH_SESSION -> listOf(ProgressPhotoType.BEFORE, ProgressPhotoType.AFTER).filterNot { it in existingTypes }
        else -> emptyList()
    }
}

private fun sessionStatusColor(status: String): Color = when (status) {
    TreatmentSessionStatus.SCHEDULED -> Color(0xFF4A90D9)
    TreatmentSessionStatus.COMPLETED -> MintGreen
    TreatmentSessionStatus.CANCELLED -> Color(0xFFE57373)
    TreatmentSessionStatus.NO_SHOW -> Color(0xFFE8A44A)
    TreatmentSessionStatus.RESCHEDULED -> Color(0xFF7B61D1)
    else -> Color(0xFF8ACABA)
}

private fun sessionStatusBg(status: String): Color = when (status) {
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
