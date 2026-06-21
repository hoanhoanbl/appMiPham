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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.model.ChatSenderRole
import com.example.appbanmypham.model.ConsultationChatMessage
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.BookingDateOption
import com.example.appbanmypham.model.FirestoreTransactionUpdate
import com.example.appbanmypham.model.SPA_BOOKING_SLOTS
import com.example.appbanmypham.model.SpaCapacitySnapshot
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.SpaSlotAvailability
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.TreatmentSessionStatus
import com.example.appbanmypham.model.appointmentTimeRange
import com.example.appbanmypham.model.buildSpaSlotAvailability
import com.example.appbanmypham.model.capacityBlockKeys
import com.example.appbanmypham.model.capacityBlockStartTimes
import com.example.appbanmypham.model.firestoreDocToConsultationChatMessage
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.firestoreDocToTreatmentSession
import com.example.appbanmypham.model.loadSpaCapacitySnapshot
import com.example.appbanmypham.model.nextBookingDateOptions
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.progressPhotoTypeMeta
import com.example.appbanmypham.model.reserveSpaCapacityAndWrite
import com.example.appbanmypham.model.resolveEffectiveSpaCapacity
import com.example.appbanmypham.model.spaDateKey
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.model.treatmentPlanStatusMeta
import com.example.appbanmypham.model.treatmentSessionStatusMeta
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class CustomerTreatmentPlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    CustomerTreatmentPlanScreen(onBack = { finish() })
                }
            }
        }
    }
}

private enum class CustomerTreatmentTab(val label: String) {
    OVERVIEW("Tổng quan"),
    SESSIONS("Buổi"),
    CHAT("Chat")
}

@Composable
fun CustomerTreatmentPlanScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var plans by remember { mutableStateOf(listOf<TreatmentPlan>()) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf(listOf<TreatmentSession>()) }
    var photos by remember { mutableStateOf(listOf<TreatmentProgressPhoto>()) }
    var thread by remember { mutableStateOf<ConsultationChatThread?>(null) }
    var messages by remember { mutableStateOf(listOf<ConsultationChatMessage>()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(user != null) }
    var isSending by remember { mutableStateOf(false) }
    var capacitySnapshot by remember { mutableStateOf<SpaCapacitySnapshot?>(null) }
    var scheduleTarget by remember { mutableStateOf<TreatmentSession?>(null) }
    var selectedTab by remember { mutableStateOf(CustomerTreatmentTab.OVERVIEW) }
    val dateOptions = remember { nextBookingDateOptions(31) }

    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    val canAccess = user != null && selectedPlan?.userId == user.uid

    DisposableEffect(user?.uid) {
        val uid = user?.uid ?: run {
            plans = emptyList()
            isLoading = false
            return@DisposableEffect onDispose {}
        }
        val reg = db.collection("treatment_plans")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                plans = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToTreatmentPlan(it) }.getOrNull() }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                if (selectedPlanId == null && plans.isNotEmpty()) selectedPlanId = plans.first().id
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    LaunchedEffect(Unit) {
        capacitySnapshot = withContext(Dispatchers.IO) { loadSpaCapacitySnapshot(db, dateOptions) }
    }

    DisposableEffect(selectedPlan?.id) {
        val plan = selectedPlan ?: return@DisposableEffect onDispose {}
        val signedUser = user ?: return@DisposableEffect onDispose {}
        if (plan.userId != signedUser.uid) return@DisposableEffect onDispose {}
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("treatment_sessions")
            .whereEqualTo("treatmentPlanId", plan.id)
            .addSnapshotListener { snap, _ ->
                sessions = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToTreatmentSession(it) }.getOrNull() }
                    ?.sortedBy { it.sessionNumber }
                    ?: emptyList()
            }
        regs += db.collection("treatment_progress_photos")
            .whereEqualTo("treatmentPlanId", plan.id)
            .addSnapshotListener { snap, _ ->
                photos = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToTreatmentProgressPhoto(it) }.getOrNull() }
                    ?.filter { !it.isHidden && it.userId == signedUser.uid }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        val threadId = plan.chatThreadId.ifBlank { plan.appointmentId }
        if (threadId.isNotBlank()) {
            regs += db.collection("consultation_chat_threads").document(threadId)
                .addSnapshotListener { snap, _ ->
                    thread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                }
            regs += db.collection("consultation_chat_messages")
                .whereEqualTo("threadId", threadId)
                .addSnapshotListener { snap, _ ->
                    messages = snap?.documents
                        ?.mapNotNull { runCatching { firestoreDocToConsultationChatMessage(it) }.getOrNull() }
                        ?.filter { it.userId == signedUser.uid }
                        ?.sortedBy { it.createdAt }
                        ?: emptyList()
                }
        }
        onDispose { regs.forEach { it.remove() } }
    }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack = onBack, count = plans.size)
            when {
                user == null -> EmptyState("Vui lòng đăng nhập để xem liệu trình.", modifier = Modifier.fillMaxSize())
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MintGreen) }
                plans.isEmpty() -> EmptyState("Bạn chưa có liệu trình nào.", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 210.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(plans, key = { it.id }) { plan ->
                                PlanListItem(
                                    plan = plan,
                                    selected = selectedPlan?.id == plan.id,
                                    onClick = { selectedPlanId = plan.id }
                                )
                            }
                        }
                    }
                    selectedPlan?.let { plan ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CustomerTreatmentTab.values().forEach { tab ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selectedTab == tab) MintGreen else Color.White)
                                            .clickable { selectedTab = tab }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(tab.label, color = if (selectedTab == tab) Color.White else MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item {
                            when (selectedTab) {
                                CustomerTreatmentTab.OVERVIEW -> SectionCard {
                                    SectionTitle(Icons.Default.Spa, "Tổng quan liệu trình")
                                    InfoText("Tên", plan.packageName)
                                    InfoText("Tư vấn", plan.consultantName.ifBlank { plan.consultantEmail })
                                    InfoText("Trạng thái", treatmentPlanStatusMeta(plan.status).label)
                                    InfoText("Tiến độ", "${plan.completedSessionCount}/${plan.sessionCount} buổi")
                                    if (plan.consultationNote.isNotBlank()) InfoText("Ghi chú", plan.consultationNote)
                                    if (plan.recommendationNote.isNotBlank()) InfoText("Đề xuất", plan.recommendationNote)
                                    if (plan.requiresProgressPhotos) InfoText("Ảnh", progressPhotoPolicyMeta(plan.photoPolicy).label)
                                }
                                CustomerTreatmentTab.SESSIONS -> SectionCard {
                                    SectionTitle(Icons.Default.EventAvailable, "Timeline buổi điều trị")
                                    if (sessions.isEmpty()) {
                                        Text("Liệu trình đang được khởi tạo từ gói spa.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            sessions.forEach { session ->
                                                CustomerSessionCard(
                                                    plan = plan,
                                                    session = session,
                                                    photos = photos.filter { it.treatmentSessionId == session.id },
                                                    onSchedule = { scheduleTarget = session }
                                                )
                                            }
                                        }
                                    }
                                }
                                CustomerTreatmentTab.CHAT -> SectionCard {
                                    SectionTitle(Icons.Default.Chat, "Chat với tư vấn viên")
                                    if (!canAccess || thread == null || user == null) {
                                        Text("Chat sẽ hiển thị khi tư vấn viên đã nhận lịch.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                                    } else {
                                        ChatMessages(messages = messages, currentUserId = user.uid)
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = messageText,
                                                onValueChange = { messageText = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("Nhập tin nhắn...", color = Color(0xFFAAD8CE)) },
                                                singleLine = true,
                                                colors = fieldColors(),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    val text = messageText.trim()
                                                    if (text.isBlank()) return@IconButton
                                                    scope.launch {
                                                        isSending = true
                                                        val result = runCatching {
                                                            sendCustomerMessage(
                                                                db = db,
                                                                plan = plan,
                                                                threadId = thread!!.id,
                                                                senderId = user.uid,
                                                                senderName = user.displayName ?: user.email?.substringBefore("@") ?: "Khách hàng",
                                                                message = text
                                                            )
                                                        }
                                                        if (result.isSuccess) messageText = ""
                                                        snackbarHostState.showSnackbar(if (result.isSuccess) "Đã gửi" else "Gửi tin thất bại")
                                                        isSending = false
                                                    }
                                                },
                                                enabled = messageText.isNotBlank() && !isSending,
                                                modifier = Modifier.size(46.dp).clip(CircleShape).background(MintGreen)
                                            ) { Icon(Icons.Default.Send, contentDescription = null, tint = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    scheduleTarget?.let { session ->
        val plan = selectedPlan
        if (plan != null && user != null) {
            ScheduleSessionDialog(
                plan = plan,
                session = session,
                dateOptions = dateOptions,
                capacitySnapshot = capacitySnapshot,
                onDismiss = { scheduleTarget = null },
                onConfirm = { date, slot ->
                    scope.launch {
                        val result = runCatching {
                            scheduleTreatmentSessionWithCapacity(
                                db = db,
                                plan = plan,
                                session = session,
                                date = date,
                                slot = slot
                            )
                        }
            snackbarHostState.showSnackbar(if (result.isSuccess) "Đã gửi lịch buổi điều trị" else result.exceptionOrNull()?.message ?: "Đặt lịch thất bại")
                        scheduleTarget = null
                    }
                }
            )
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, count: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal).statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f)).align(Alignment.TopStart)) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.padding(top = 52.dp)) {
            Text("SPA", color = Color.White.copy(0.74f), fontSize = 11.sp, letterSpacing = 2.sp)
            Text("Liệu trình của tôi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("$count liệu trình", color = Color.White.copy(0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlanListItem(plan: TreatmentPlan, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) Color(0xFFEAF9F5) else Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MintGreen.copy(0.14f)), contentAlignment = Alignment.Center) {
            if (plan.packageImageUrl.isNotBlank()) {
                AsyncImage(
                    model = plan.packageImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(plan.packageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${plan.completedSessionCount}/${plan.sessionCount} buổi - ${treatmentPlanStatusMeta(plan.status).label}", color = Color(0xFF5A8A80), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) { Column(modifier = Modifier.padding(16.dp), content = content) }
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
        Text("$label:", color = Color(0xFF8ACABA), fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(value, color = Color(0xFF4A7A70), fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CustomerSessionCard(
    plan: TreatmentPlan,
    session: TreatmentSession,
    photos: List<TreatmentProgressPhoto>,
    onSchedule: () -> Unit
) {
    val needsSchedule = session.status == TreatmentSessionStatus.UNSCHEDULED ||
        session.status == TreatmentSessionStatus.NO_SHOW ||
        (session.scheduledStartAt == 0L && session.status in setOf(TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED))
    val canSchedule = plan.consultantId.isNotBlank() && needsSchedule
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8FFFE)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
            Text("Buổi ${session.sessionNumber}/${session.totalSessions}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(session.dateLabel.ifBlank { "Dang cho ban chon lich" } + session.timeSlotLabel.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty(), color = Color(0xFF5A8A80), fontSize = 12.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(sessionBg(session.status)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(treatmentSessionStatusMeta(session.status).label, color = sessionColor(session.status), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (session.status == TreatmentSessionStatus.NO_SHOW) {
            Spacer(Modifier.height(6.dp))
            Text("Buổi này được ghi nhận khách không đến. Buổi chưa tính hoàn thành, bạn có thể chọn lịch lại.", color = Color(0xFFE8A44A), fontSize = 12.sp)
        }
        if (needsSchedule) {
            Spacer(Modifier.height(10.dp))
            if (plan.consultantId.isBlank()) {
                Text("Tư vấn viên cần nhận lịch đầu tiên trước khi bạn đặt các buổi tiếp theo.", color = Color(0xFF8ACABA), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onSchedule,
                enabled = canSchedule,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) {
                Text(if (session.status == TreatmentSessionStatus.NO_SHOW) "Chọn lịch lại" else "Chọn lịch cho buổi này", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ảnh tiến trình", color = MintGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.take(4).forEach { photo ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = photo.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF9F5))
                        )
                        Text(progressPhotoTypeMeta(photo.photoType).label, color = Color(0xFF6C8F87), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSessionDialog(
    plan: TreatmentPlan,
    session: TreatmentSession,
    dateOptions: List<BookingDateOption>,
    capacitySnapshot: SpaCapacitySnapshot?,
    onDismiss: () -> Unit,
    onConfirm: (BookingDateOption, String) -> Unit
) {
    var selectedDate by remember { mutableStateOf(dateOptions.firstOrNull()) }
    var selectedSlot by remember { mutableStateOf(SPA_BOOKING_SLOTS.first()) }
    val availabilityByDate = remember(capacitySnapshot, plan.durationPerSessionMinutes, dateOptions) {
        val snapshot = capacitySnapshot ?: return@remember emptyMap()
        dateOptions.associate { date ->
            date.startOfDayMillis to buildSpaSlotAvailability(date, plan.durationPerSessionMinutes, snapshot)
        }
    }
    val availableSlots = remember(selectedDate, availabilityByDate) {
        selectedDate?.let { date ->
            availabilityByDate[date.startOfDayMillis]?.filter { it.selectable }.orEmpty()
        }.orEmpty()
    }
    LaunchedEffect(selectedDate?.startOfDayMillis, availabilityByDate) {
        if (selectedSlot !in availableSlots.map { it.slot }) selectedSlot = availableSlots.firstOrNull()?.slot.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Chọn lịch buổi ${session.sessionNumber}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Chỉ chọn được lịch trong 1 tháng tới. Khung giờ đã kín sẽ được ẩn.", color = Color(0xFF6C8F87), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                CompactCalendar(dateOptions, selectedDate?.startOfDayMillis, availabilityByDate) { selectedDate = it }
                Spacer(Modifier.height(12.dp))
                if (availableSlots.isEmpty()) {
                    Text("Ngày này đã kín lịch.", color = Color(0xFFE8A44A), fontSize = 13.sp)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(availableSlots) { availability ->
                            val slot = availability.slot
                            val selected = selectedSlot == slot
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MintGreen else Color(0xFFF8FFFE))
                                    .clickable { selectedSlot = slot }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(slot, color = if (selected) Color.White else Color(0xFF1A4A40), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("Còn ${availability.remainingCapacity}", color = if (selected) Color.White.copy(0.85f) else MintGreen, fontSize = 10.sp)
                                }
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
                enabled = selectedDate != null && selectedSlot.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) { Text("Gửi lịch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", color = MintGreen) } }
    )
}

@Composable
private fun CompactCalendar(
    dateOptions: List<BookingDateOption>,
    selectedDateMillis: Long?,
    availabilityByDate: Map<Long, List<SpaSlotAvailability>>,
    onSelect: (BookingDateOption) -> Unit
) {
    val leadingBlanks = remember(dateOptions) {
        val first = dateOptions.firstOrNull()?.startOfDayMillis ?: 0L
        if (first == 0L) 0 else {
            val dayOfWeek = Calendar.getInstance().apply { timeInMillis = first }.get(Calendar.DAY_OF_WEEK)
            (dayOfWeek + 5) % 7
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.height(256.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        userScrollEnabled = false
    ) {
        items(List(leadingBlanks) { it }) {
            Spacer(Modifier.aspectRatio(1f))
        }
        items(dateOptions) { option ->
            val availability = availabilityByDate[option.startOfDayMillis].orEmpty()
            val full = availability.isNotEmpty() && availability.none { it.selectable }
            val hasLimitedCapacity = availability.any { it.bookedCount > 0 }
            val selected = selectedDateMillis == option.startOfDayMillis
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> MintGreen
                            full -> Color(0xFFF2F2F2)
                            hasLimitedCapacity -> Color(0xFFFFFBF2)
                            else -> Color(0xFFF8FFFE)
                        }
                    )
                    .clickable(enabled = !full) { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label.substringBefore("/"),
                    color = if (selected) Color.White else if (full) Color(0xFFB8B8B8) else Color(0xFF1A4A40),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun scheduleTreatmentSessionWithCapacity(
    db: FirebaseFirestore,
    plan: TreatmentPlan,
    session: TreatmentSession,
    date: BookingDateOption,
    slot: String
) {
    withContext(Dispatchers.IO) {
        if (session.appointmentId.isNotBlank() && session.scheduledStartAt > 0L && session.status in setOf(TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED)) {
            throw IllegalStateException("Buổi này đã có lịch đang hoạt động")
        }
        val duration = plan.durationPerSessionMinutes.coerceAtLeast(30)
        val (startAt, endAt) = appointmentTimeRange(date.startOfDayMillis, slot, duration)
        val dateOption = BookingDateOption(date.startOfDayMillis, date.label, date.compactLabel)
        val capacitySnapshot = loadSpaCapacitySnapshot(db, listOf(dateOption))
        val availability = buildSpaSlotAvailability(dateOption, duration, capacitySnapshot)
            .firstOrNull { it.slot == slot }
        if (availability?.selectable != true) {
            throw IllegalStateException(availability?.reason ?: "Khung giờ này không khả dụng, vui lòng chọn giờ khác")
        }
        val effective = resolveEffectiveSpaCapacity(
            settings = capacitySnapshot.settings,
            override = capacitySnapshot.overridesByDateKey[spaDateKey(date.startOfDayMillis)],
            dateStartMillis = date.startOfDayMillis
        )
        val blockStartTimes = capacityBlockStartTimes(startAt, endAt, capacitySnapshot.settings.slotMinutes)
        val blockKeys = capacityBlockKeys(startAt, endAt, capacitySnapshot.settings.slotMinutes)
        val appointmentRef = db.collection("appointments").document()
        val sessionRef = db.collection("treatment_sessions").document(session.id)
        val appointment = SpaAppointment(
            id = appointmentRef.id,
            userId = plan.userId,
            userEmail = plan.userEmail,
            userName = plan.userName,
            phoneNumber = plan.phoneNumber,
            spaPackageId = plan.spaPackageId,
            spaPackageName = "${plan.packageName} - Buổi ${session.sessionNumber}/${session.totalSessions}",
            spaPackagePrice = 0.0,
            durationMinutes = duration,
            startAt = startAt,
            endAt = endAt,
            appointmentDateLabel = date.label,
            timeSlotLabel = slot,
            status = if (plan.consultantId.isBlank()) AppointmentStatus.PENDING else AppointmentStatus.ASSIGNED,
            note = "Khách chọn lịch cho buổi ${session.sessionNumber}/${session.totalSessions}",
            consultantId = plan.consultantId,
            consultantEmail = plan.consultantEmail,
            consultantName = plan.consultantName,
            capacityUnits = 1,
            reservedBlockKeys = blockKeys
        )
        val now = System.currentTimeMillis()
        val sessionUpdates = mapOf(
            "appointmentId" to appointmentRef.id,
            "scheduledStartAt" to startAt,
            "scheduledEndAt" to endAt,
            "dateLabel" to date.label,
            "timeSlotLabel" to slot,
            "status" to TreatmentSessionStatus.SCHEDULED,
            "updatedAt" to now
        )
        reserveSpaCapacityAndWrite(
            db = db,
            appointmentRef = appointmentRef,
            appointment = appointment,
            blockKeys = blockKeys,
            blockStartTimes = blockStartTimes,
            effectiveCapacity = effective.capacity,
            extraUpdates = listOf(FirestoreTransactionUpdate(sessionRef, sessionUpdates)),
            verifyBeforeWrite = { tx ->
                val latestSession = tx.get(sessionRef)
                val latestStatus = latestSession.getString("status") ?: session.status
                val latestStartAt = latestSession.getLong("scheduledStartAt") ?: 0L
                val latestAppointmentId = latestSession.getString("appointmentId").orEmpty()
                if (latestSession.getString("userId") != plan.userId || latestSession.getString("treatmentPlanId") != plan.id) {
                    throw IllegalStateException("Buổi điều trị không hợp lệ")
                }
                if (latestAppointmentId.isNotBlank() && latestStartAt > 0L && latestStatus in setOf(TreatmentSessionStatus.SCHEDULED, TreatmentSessionStatus.RESCHEDULED)) {
                    throw IllegalStateException("Buổi này đã được lên lịch")
                }
            }
        )
    }
}

private suspend fun scheduleTreatmentSession(
    db: FirebaseFirestore,
    plan: TreatmentPlan,
    session: TreatmentSession,
    date: BookingDateOption,
    slot: String
) {
    withContext(Dispatchers.IO) {
        val (startAt, endAt) = appointmentTimeRange(date.startOfDayMillis, slot, plan.durationPerSessionMinutes)
        val hasConflict = db.collection("appointments")
            .whereEqualTo("startAt", startAt)
            .get()
            .await()
            .documents
            .any { AppointmentStatus.activeStatuses.contains(it.getString("status")) }
        if (hasConflict) throw IllegalStateException("Khung giờ này vừa có người đặt, vui lòng chọn giờ khác")
        val appointment = SpaAppointment(
            userId = plan.userId,
            userEmail = plan.userEmail,
            userName = plan.userName,
            phoneNumber = plan.phoneNumber,
            spaPackageId = plan.spaPackageId,
            spaPackageName = "${plan.packageName} - Buổi ${session.sessionNumber}/${session.totalSessions}",
            spaPackagePrice = 0.0,
            durationMinutes = plan.durationPerSessionMinutes,
            startAt = startAt,
            endAt = endAt,
            appointmentDateLabel = date.label,
            timeSlotLabel = slot,
            status = AppointmentStatus.ASSIGNED,
            note = "Khách chọn lịch cho buổi ${session.sessionNumber}/${session.totalSessions}",
            consultantId = plan.consultantId,
            consultantEmail = plan.consultantEmail,
            consultantName = plan.consultantName
        )
        val appointmentRef = db.collection("appointments").document()
        val sessionRef = db.collection("treatment_sessions").document(session.id)
        val slotRef = db.collection("appointment_slots").document(startAt.toString())
        db.runTransaction { tx ->
            val latestSession = tx.get(sessionRef)
            val latestStatus = latestSession.getString("status") ?: session.status
            val latestStartAt = latestSession.getLong("scheduledStartAt") ?: 0L
            if (latestSession.getString("userId") != plan.userId || latestSession.getString("treatmentPlanId") != plan.id) {
                throw IllegalStateException("Buổi điều trị không hợp lệ")
            }
            if (latestStatus == TreatmentSessionStatus.SCHEDULED && latestStartAt > 0L) {
                throw IllegalStateException("Buổi này đã được lên lịch")
            }
            val slotSnap = tx.get(slotRef)
            if (slotSnap.exists() && slotSnap.getString("status") == "active") {
                throw IllegalStateException("Khung giờ này vừa có người đặt, vui lòng chọn giờ khác")
            }
            val now = System.currentTimeMillis()
            tx.set(
                slotRef,
                mapOf(
                    "startAt" to startAt,
                    "endAt" to endAt,
                    "dateLabel" to date.label,
                    "timeSlotLabel" to slot,
                    "appointmentId" to appointmentRef.id,
                    "userId" to plan.userId,
                    "consultantId" to plan.consultantId,
                    "status" to "active",
                    "updatedAt" to now,
                    "createdAt" to now
                )
            )
            tx.set(appointmentRef, appointment.toFirestoreMap(includeCreatedAt = true))
            tx.update(
                sessionRef,
                mapOf(
                    "appointmentId" to appointmentRef.id,
                    "scheduledStartAt" to startAt,
                    "scheduledEndAt" to endAt,
                    "dateLabel" to date.label,
                    "timeSlotLabel" to slot,
                    "status" to TreatmentSessionStatus.SCHEDULED,
                    "updatedAt" to now
                )
            )
            null
        }.await()
    }
}

private const val DAY_MILLIS = 86_400_000L

@Composable
private fun ChatMessages(messages: List<ConsultationChatMessage>, currentUserId: String) {
    if (messages.isEmpty()) {
        Text("Chưa có tin nhắn nào.", color = Color(0xFF8ACABA), fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        messages.takeLast(8).forEach { message ->
            val mine = message.senderId == currentUserId
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Column(
                    modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(if (mine) Color(0xFFEAF9F5) else Color(0xFFF7F7F7)).padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(message.senderName.ifBlank { message.senderRole }, color = if (mine) MintGreen else Color(0xFF8ACABA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(message.message, color = Color(0xFF1A4A40), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color(0xFF8ACABA), fontSize = 14.sp)
        }
    }
}

private suspend fun sendCustomerMessage(
    db: FirebaseFirestore,
    plan: TreatmentPlan,
    threadId: String,
    senderId: String,
    senderName: String,
    message: String
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val chatMessage = ConsultationChatMessage(
            threadId = threadId,
            appointmentId = plan.appointmentId,
            treatmentPlanId = plan.id,
            userId = plan.userId,
            consultantId = plan.consultantId,
            senderId = senderId,
            senderName = senderName,
            senderRole = ChatSenderRole.CUSTOMER,
            message = message,
            createdAt = now
        )
        db.collection("consultation_chat_messages").add(chatMessage.toFirestoreMap(includeCreatedAt = true)).await()
        db.collection("consultation_chat_threads").document(threadId).update(
            mapOf("lastMessage" to message, "lastMessageAt" to now, "updatedAt" to now)
        ).await()
    }
}

private fun sessionColor(status: String): Color = when (status) {
    TreatmentSessionStatus.UNSCHEDULED -> Color(0xFF8ACABA)
    TreatmentSessionStatus.SCHEDULED -> Color(0xFF4A90D9)
    TreatmentSessionStatus.COMPLETED -> MintGreen
    TreatmentSessionStatus.CANCELLED -> Color(0xFFE57373)
    TreatmentSessionStatus.NO_SHOW -> Color(0xFFE8A44A)
    TreatmentSessionStatus.RESCHEDULED -> Color(0xFF7B61D1)
    else -> Color(0xFF8ACABA)
}

private fun sessionBg(status: String): Color = when (status) {
    TreatmentSessionStatus.UNSCHEDULED -> Color(0xFFF5F5F5)
    TreatmentSessionStatus.SCHEDULED -> Color(0xFFE8F0FB)
    TreatmentSessionStatus.COMPLETED -> Color(0xFFEAF9F5)
    TreatmentSessionStatus.CANCELLED -> Color(0xFFFFECEC)
    TreatmentSessionStatus.NO_SHOW -> Color(0xFFFFF3E0)
    TreatmentSessionStatus.RESCHEDULED -> Color(0xFFF0ECFF)
    else -> Color(0xFFF5F5F5)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
