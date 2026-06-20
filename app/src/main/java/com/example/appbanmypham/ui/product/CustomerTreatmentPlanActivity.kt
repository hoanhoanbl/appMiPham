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
import com.example.appbanmypham.model.SPA_BOOKING_SLOTS
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.TreatmentSessionStatus
import com.example.appbanmypham.model.appointmentTimeRange
import com.example.appbanmypham.model.firestoreDocToConsultationChatMessage
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.firestoreDocToTreatmentSession
import com.example.appbanmypham.model.nextBookingDateOptions
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.progressPhotoTypeMeta
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
    var bookedSlotsByDate by remember { mutableStateOf<Map<Long, Set<String>>>(emptyMap()) }
    var scheduleTarget by remember { mutableStateOf<TreatmentSession?>(null) }
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
        val start = dateOptions.firstOrNull()?.startOfDayMillis ?: return@LaunchedEffect
        val end = (dateOptions.lastOrNull()?.startOfDayMillis ?: start) + DAY_MILLIS
        bookedSlotsByDate = withContext(Dispatchers.IO) {
            db.collection("appointments")
                .whereGreaterThanOrEqualTo("startAt", start)
                .whereLessThan("startAt", end)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    if (!AppointmentStatus.activeStatuses.contains(status)) return@mapNotNull null
                    val startAt = doc.getLong("startAt") ?: return@mapNotNull null
                    val slot = doc.getString("timeSlotLabel") ?: return@mapNotNull null
                    val date = dateOptions.firstOrNull { startAt >= it.startOfDayMillis && startAt < it.startOfDayMillis + DAY_MILLIS }
                        ?: return@mapNotNull null
                    date.startOfDayMillis to slot
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
        }
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
                user == null -> EmptyState("Vui long dang nhap de xem lieu trinh.", modifier = Modifier.fillMaxSize())
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MintGreen) }
                plans.isEmpty() -> EmptyState("Ban chua co lieu trinh nao.", modifier = Modifier.fillMaxSize())
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
                            SectionCard {
                                SectionTitle(Icons.Default.Spa, "Tong quan lieu trinh")
                                InfoText("Ten", plan.packageName)
                                InfoText("Tu van", plan.consultantName.ifBlank { plan.consultantEmail })
                                InfoText("Trang thai", treatmentPlanStatusMeta(plan.status).label)
                                InfoText("Tien do", "${plan.completedSessionCount}/${plan.sessionCount} buoi")
                                if (plan.consultationNote.isNotBlank()) InfoText("Ghi chu", plan.consultationNote)
                                if (plan.recommendationNote.isNotBlank()) InfoText("De xuat", plan.recommendationNote)
                                if (plan.requiresProgressPhotos) InfoText("Anh", progressPhotoPolicyMeta(plan.photoPolicy).label)
                            }
                        }
                        item {
                            SectionCard {
                                SectionTitle(Icons.Default.EventAvailable, "Timeline buoi dieu tri")
                                if (sessions.isEmpty()) {
                                    Text("Lieu trinh dang duoc khoi tao tu goi spa.", color = Color(0xFF8ACABA), fontSize = 13.sp)
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
                        }
                        item {
                            SectionCard {
                                SectionTitle(Icons.Default.Chat, "Chat voi tu van vien")
                                if (!canAccess || thread == null) {
                                    Text("Chat se hien khi tu van vien da nhan lich.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                                } else {
                                    ChatMessages(messages = messages, currentUserId = user.uid)
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = messageText,
                                            onValueChange = { messageText = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("Nhap tin nhan...", color = Color(0xFFAAD8CE)) },
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
                                                            senderName = user.displayName ?: user.email?.substringBefore("@") ?: "Khach hang",
                                                            message = text
                                                        )
                                                    }
                                                    if (result.isSuccess) messageText = ""
                                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Da gui" else "Gui tin that bai")
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

    scheduleTarget?.let { session ->
        val plan = selectedPlan
        if (plan != null && user != null) {
            ScheduleSessionDialog(
                session = session,
                dateOptions = dateOptions,
                bookedSlotsByDate = bookedSlotsByDate,
                onDismiss = { scheduleTarget = null },
                onConfirm = { date, slot ->
                    scope.launch {
                        val result = runCatching {
                            scheduleTreatmentSession(
                                db = db,
                                plan = plan,
                                session = session,
                                date = date,
                                slot = slot
                            )
                        }
                        snackbarHostState.showSnackbar(if (result.isSuccess) "Da gui lich buoi dieu tri" else result.exceptionOrNull()?.message ?: "Dat lich that bai")
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
            Text("Lieu trinh cua toi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("$count lieu trinh", color = Color.White.copy(0.8f), fontSize = 12.sp)
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
            Text("${plan.completedSessionCount}/${plan.sessionCount} buoi - ${treatmentPlanStatusMeta(plan.status).label}", color = Color(0xFF5A8A80), fontSize = 12.sp)
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
                Text("Buoi ${session.sessionNumber}/${session.totalSessions}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(session.dateLabel.ifBlank { "Dang cho ban chon lich" } + session.timeSlotLabel.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty(), color = Color(0xFF5A8A80), fontSize = 12.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(sessionBg(session.status)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(treatmentSessionStatusMeta(session.status).label, color = sessionColor(session.status), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (session.status == TreatmentSessionStatus.NO_SHOW) {
            Spacer(Modifier.height(6.dp))
            Text("Buoi nay duoc ghi nhan khach khong den. Buoi chua tinh hoan thanh, ban co the chon lich lai.", color = Color(0xFFE8A44A), fontSize = 12.sp)
        }
        if (needsSchedule) {
            Spacer(Modifier.height(10.dp))
            if (plan.consultantId.isBlank()) {
                Text("Tu van vien can nhan lich dau tien truoc khi ban dat cac buoi tiep theo.", color = Color(0xFF8ACABA), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onSchedule,
                enabled = canSchedule,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) {
                Text(if (session.status == TreatmentSessionStatus.NO_SHOW) "Chon lich lai" else "Chon lich cho buoi nay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Anh tien trinh", color = MintGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
    session: TreatmentSession,
    dateOptions: List<BookingDateOption>,
    bookedSlotsByDate: Map<Long, Set<String>>,
    onDismiss: () -> Unit,
    onConfirm: (BookingDateOption, String) -> Unit
) {
    var selectedDate by remember { mutableStateOf(dateOptions.firstOrNull()) }
    var selectedSlot by remember { mutableStateOf(SPA_BOOKING_SLOTS.first()) }
    val availableSlots = remember(selectedDate, bookedSlotsByDate) {
        val booked = selectedDate?.let { bookedSlotsByDate[it.startOfDayMillis] }.orEmpty()
        SPA_BOOKING_SLOTS.filterNot { it in booked }
    }
    LaunchedEffect(selectedDate?.startOfDayMillis, bookedSlotsByDate) {
        if (selectedSlot !in availableSlots) selectedSlot = availableSlots.firstOrNull().orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Chon lich buoi ${session.sessionNumber}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Chi chon duoc lich trong 1 thang toi. Khung gio da co lich se duoc an.", color = Color(0xFF6C8F87), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                CompactCalendar(dateOptions, selectedDate?.startOfDayMillis, bookedSlotsByDate) { selectedDate = it }
                Spacer(Modifier.height(12.dp))
                if (availableSlots.isEmpty()) {
                    Text("Ngay nay da kin lich.", color = Color(0xFFE8A44A), fontSize = 13.sp)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(availableSlots) { slot ->
                            val selected = selectedSlot == slot
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MintGreen else Color(0xFFF8FFFE))
                                    .clickable { selectedSlot = slot }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(slot, color = if (selected) Color.White else Color(0xFF1A4A40), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
            ) { Text("Gui lich") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huy", color = MintGreen) } }
    )
}

@Composable
private fun CompactCalendar(
    dateOptions: List<BookingDateOption>,
    selectedDateMillis: Long?,
    bookedSlotsByDate: Map<Long, Set<String>>,
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
            val booked = bookedSlotsByDate[option.startOfDayMillis]?.size ?: 0
            val full = booked >= SPA_BOOKING_SLOTS.size
            val selected = selectedDateMillis == option.startOfDayMillis
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> MintGreen
                            full -> Color(0xFFF2F2F2)
                            booked > 0 -> Color(0xFFFFFBF2)
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
        if (hasConflict) throw IllegalStateException("Khung gio nay vua co nguoi dat, vui long chon gio khac")
        val appointment = SpaAppointment(
            userId = plan.userId,
            userEmail = plan.userEmail,
            userName = plan.userName,
            phoneNumber = plan.phoneNumber,
            spaPackageId = plan.spaPackageId,
            spaPackageName = "${plan.packageName} - Buoi ${session.sessionNumber}/${session.totalSessions}",
            spaPackagePrice = 0.0,
            durationMinutes = plan.durationPerSessionMinutes,
            startAt = startAt,
            endAt = endAt,
            appointmentDateLabel = date.label,
            timeSlotLabel = slot,
            status = AppointmentStatus.ASSIGNED,
            note = "Khach chon lich cho buoi ${session.sessionNumber}/${session.totalSessions}",
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
                throw IllegalStateException("Buoi dieu tri khong hop le")
            }
            if (latestStatus == TreatmentSessionStatus.SCHEDULED && latestStartAt > 0L) {
                throw IllegalStateException("Buoi nay da duoc len lich")
            }
            val slotSnap = tx.get(slotRef)
            if (slotSnap.exists() && slotSnap.getString("status") == "active") {
                throw IllegalStateException("Khung gio nay vua co nguoi dat, vui long chon gio khac")
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
        Text("Chua co tin nhan nao.", color = Color(0xFF8ACABA), fontSize = 13.sp)
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
