package com.example.appbanmypham.ui.consultant

import android.content.Intent
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.AppRoles
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.ChatThreadStatus
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.DEFAULT_SPA_CAPACITY_SETTINGS_ID
import com.example.appbanmypham.model.SPA_CAPACITY_SETTINGS_COLLECTION
import com.example.appbanmypham.model.SpaCapacitySettings
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.TreatmentPlanStatus
import com.example.appbanmypham.model.ACTIVE_TREATMENT_PLAN_KEYS_COLLECTION
import com.example.appbanmypham.model.activeTreatmentPlanKey
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToSpaCapacitySettings
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.ui.auth.LoginActivity
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
import java.util.Calendar

class ConsultantDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ConsultantDashboardScreen(
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            startActivity(
                                Intent(this, LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private enum class ConsultantHomeTab { SCHEDULES, CHAT }
private enum class ConsultantDateQuickFilter(val label: String) {
    ALL("Tất cả ngày"),
    TODAY("Hôm nay"),
    UPCOMING("Sắp tới"),
    PAST("Đã qua")
}

@Composable
fun ConsultantDashboardScreen(onLogout: () -> Unit = {}) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val user = auth.currentUser
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isRoleLoading by remember { mutableStateOf(true) }
    var isConsultant by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(listOf<SpaAppointment>()) }
    var assigned by remember { mutableStateOf(listOf<SpaAppointment>()) }
    var chatThreads by remember { mutableStateOf(listOf<ConsultationChatThread>()) }
    var mainTab by remember { mutableStateOf(ConsultantHomeTab.SCHEDULES) }
    var selectedTab by remember { mutableStateOf(0) }
    var dateFilter by remember { mutableStateOf(ConsultantDateQuickFilter.ALL) }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid == null) {
            isConsultant = false
            isRoleLoading = false
            return@LaunchedEffect
        }
        val role = runCatching {
            withContext(Dispatchers.IO) {
                db.collection("users").document(uid).get().await()
            }.getLong("role")?.toInt() ?: AppRoles.CUSTOMER
        }.getOrDefault(AppRoles.CUSTOMER)
        isConsultant = role == AppRoles.CONSULTANT
        isRoleLoading = false
    }

    DisposableEffect(isConsultant, user?.uid) {
        if (!isConsultant || user == null) return@DisposableEffect onDispose {}
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("appointments")
            .whereEqualTo("status", AppointmentStatus.PENDING)
            .addSnapshotListener { snap, _ ->
                pending = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                    ?.sortedWith(compareBy<SpaAppointment> { it.startAt }.thenBy { it.createdAt })
                    ?: emptyList()
            }
        regs += db.collection("appointments")
            .whereEqualTo("consultantId", user.uid)
            .addSnapshotListener { snap, _ ->
                assigned = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                    ?.sortedWith(compareBy<SpaAppointment> { it.startAt }.thenBy { it.createdAt })
                    ?: emptyList()
            }
        regs += db.collection("consultation_chat_threads")
            .whereEqualTo("consultantId", user.uid)
            .addSnapshotListener { snap, _ ->
                chatThreads = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                    ?.sortedByDescending { it.lastMessageAt.takeIf { time -> time > 0L } ?: it.updatedAt }
                    ?.distinctBy { thread -> thread.treatmentPlanId.ifBlank { thread.appointmentId.ifBlank { thread.id } } }
                    ?: emptyList()
            }
        onDispose { regs.forEach { it.remove() } }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isConsultant) {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    NavigationBarItem(
                        selected = mainTab == ConsultantHomeTab.SCHEDULES,
                        onClick = { mainTab = ConsultantHomeTab.SCHEDULES },
                        icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                        label = { Text("Lịch") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MintGreen, selectedTextColor = MintGreen)
                    )
                    NavigationBarItem(
                        selected = mainTab == ConsultantHomeTab.CHAT,
                        onClick = { mainTab = ConsultantHomeTab.CHAT },
                        icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        label = { Text("Chat") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MintGreen, selectedTextColor = MintGreen)
                    )
                }
            }
        }
    ) { padding ->
        when {
            isRoleLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }

            !isConsultant -> AccessDenied(onLogout = onLogout, modifier = Modifier.padding(padding))

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ConsultantHeader(userName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Consultant", onLogout = onLogout)

                if (mainTab == ConsultantHomeTab.SCHEDULES) {
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = MintGreen) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Chờ xác nhận (${pending.size})") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Lịch của tôi (${assigned.size})") })
                    }

                    val baseList = if (selectedTab == 0) pending else assigned
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ConsultantDateQuickFilter.values()) { option ->
                            QuickFilterPill(
                                label = option.label,
                                count = baseList.count { matchesConsultantDateFilter(it.startAt, option) },
                                selected = dateFilter == option,
                                onClick = { dateFilter = option }
                            )
                        }
                    }

                    val list = baseList.filter { matchesConsultantDateFilter(it.startAt, dateFilter) }
                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(54.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (selectedTab == 0) "Không có lịch chờ xác nhận" else "Chưa có lịch phù hợp", color = Color(0xFF8ACABA), fontSize = 15.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.id }) { appointment ->
                                ConsultantAppointmentCard(
                                    appointment = appointment,
                                    showConfirm = selectedTab == 0,
                                    onOpenDetail = {
                                        context.startActivity(
                                            Intent(context, ConsultantAppointmentDetailActivity::class.java)
                                                .putExtra("appointment_id", appointment.id)
                                        )
                                    },
                                    onConfirm = {
                                        scope.launch {
                                            val result = runCatching { confirmAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Đã nhận lịch và mở chat" else result.exceptionOrNull()?.message ?: "Nhận lịch thất bại"
                                            )
                                        }
                                    },
                                    onCheckIn = {
                                        scope.launch {
                                            val result = runCatching { checkInAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Đã check-in khách" else result.exceptionOrNull()?.message ?: "Check-in thất bại"
                                            )
                                        }
                                    },
                                    onStartService = {
                                        scope.launch {
                                            val result = runCatching { startServiceAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Đã bắt đầu dịch vụ" else result.exceptionOrNull()?.message ?: "Cập nhật thất bại"
                                            )
                                        }
                                    },
                                    onComplete = {
                                        scope.launch {
                                            val result = runCatching { completeAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Đã cập nhật lịch" else result.exceptionOrNull()?.message ?: "Cập nhật thất bại"
                                            )
                                        }
                                    },
                                    onNoShow = {
                                        scope.launch {
                                            val result = runCatching { markAppointmentNoShow(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "Đã đánh dấu khách không đến" else result.exceptionOrNull()?.message ?: "Cập nhật thất bại"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    ConsultantChatTab(
                        threads = chatThreads,
                        onOpenThread = { thread ->
                            context.startActivity(
                                Intent(context, ConsultantChatActivity::class.java)
                                    .putExtra("thread_id", thread.id)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsultantChatTab(
    threads: List<ConsultationChatThread>,
    onOpenThread: (ConsultationChatThread) -> Unit
) {
    if (threads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(8.dp))
                Text("Chưa có cuộc chat nào", color = Color(0xFF8ACABA), fontSize = 15.sp)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Hộp thoại khách hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("Chạm vào khách để mở đúng cuộc trò chuyện. Hồ sơ khách nằm trong màn chi tiết.", color = Color(0xFF6C8F87), fontSize = 12.sp)
        }
        items(threads, key = { it.id }) { thread ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenThread(thread) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = MintGreen)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(thread.userName.ifBlank { thread.userEmail.ifBlank { "Khách hàng" } }, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(thread.lastMessage.ifBlank { "Mở chat để trao đổi với khách" }, color = Color(0xFF6C8F87), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private suspend fun confirmAppointment(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        val planDoc = db.collection("treatment_plans")
            .whereEqualTo("appointmentId", appointmentId)
            .get()
            .await()
            .documents
            .firstOrNull()
        val sessionDocs = planDoc?.let { plan ->
            db.collection("treatment_sessions")
                .whereEqualTo("treatmentPlanId", plan.id)
                .get()
                .await()
                .documents
        }.orEmpty()
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            if (status != AppointmentStatus.PENDING || consultantId.isNotBlank()) {
                throw IllegalStateException("Lịch này đã được tư vấn viên khác nhận")
            }
            val now = System.currentTimeMillis()
            val consultantName = user.displayName ?: user.email?.substringBefore("@") ?: "Consultant"
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.ASSIGNED,
                    "consultantId" to user.uid,
                    "consultantEmail" to user.email.orEmpty(),
                    "consultantName" to consultantName,
                    "confirmedAt" to now,
                    "updatedAt" to now
                )
            )
            val planId = planDoc?.id.orEmpty()
            val threadId = planDoc?.getString("chatThreadId")?.takeIf { it.isNotBlank() } ?: appointmentId
            planDoc?.let { plan ->
                transaction.update(
                    plan.reference,
                    mapOf(
                        "consultantId" to user.uid,
                        "consultantEmail" to user.email.orEmpty(),
                        "consultantName" to consultantName,
                        "status" to TreatmentPlanStatus.ACTIVE,
                        "chatThreadId" to threadId,
                        "updatedAt" to now
                    )
                )
                sessionDocs.forEach { session ->
                    transaction.update(
                        session.reference,
                        mapOf(
                            "consultantId" to user.uid,
                            "updatedAt" to now
                        )
                    )
                }
            }
            transaction.set(
                db.collection("consultation_chat_threads").document(threadId),
                mapOf(
                    "appointmentId" to appointmentId,
                    "treatmentPlanId" to planId,
                    "userId" to (snap.getString("userId") ?: ""),
                    "userEmail" to (snap.getString("userEmail") ?: ""),
                    "userName" to (snap.getString("userName") ?: ""),
                    "consultantId" to user.uid,
                    "consultantEmail" to user.email.orEmpty(),
                    "consultantName" to consultantName,
                    "status" to ChatThreadStatus.ACTIVE,
                    "updatedAt" to now,
                    "createdAt" to now
                ),
                SetOptions.merge()
            )
            null
        }
            .await()
    }
}

private suspend fun checkInAppointment(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            if (status !in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED) || consultantId != user.uid) {
                throw IllegalStateException("Chỉ tư vấn viên phụ trách mới được check-in lịch này")
            }
            val now = System.currentTimeMillis()
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.CHECKED_IN,
                    "checkedInAt" to now,
                    "updatedAt" to now
                )
            )
            null
        }.await()
    }
}

private suspend fun startServiceAppointment(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            if (status != AppointmentStatus.CHECKED_IN || consultantId != user.uid) {
                throw IllegalStateException("Chỉ lịch đã check-in mới được bắt đầu dịch vụ")
            }
            val now = System.currentTimeMillis()
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.IN_SERVICE,
                    "inServiceAt" to now,
                    "updatedAt" to now
                )
            )
            null
        }.await()
    }
}

private suspend fun completeAppointment(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            val startAt = snap.getLong("startAt") ?: 0L
            val now = System.currentTimeMillis()
            if (status != AppointmentStatus.IN_SERVICE || consultantId != user.uid) {
                throw IllegalStateException("Chỉ lịch đang làm dịch vụ mới được hoàn thành")
            }
            if (startAt > now) {
                throw IllegalStateException("Chưa tới giờ hẹn, không thể hoàn thành lịch tương lai")
            }
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.COMPLETED,
                    "completedAt" to now,
                    "updatedAt" to now
                )
            )
            null
        }.await()
        syncTreatmentSessionFromAppointment(
            db = db,
            appointmentId = appointmentId,
            status = "completed",
            actorId = user.uid
        )
    }
}

private suspend fun syncTreatmentSessionFromAppointment(
    db: FirebaseFirestore,
    appointmentId: String,
    status: String,
    actorId: String
) {
    val now = System.currentTimeMillis()
    val sessions = db.collection("treatment_sessions")
        .whereEqualTo("appointmentId", appointmentId)
        .get()
        .await()
        .documents
    if (sessions.isEmpty()) return
    val batch = db.batch()
    sessions.forEach { doc ->
        val previousStatus = doc.getString("status") ?: ""
        val planId = doc.getString("treatmentPlanId").orEmpty()
        val updates = when (status) {
            "completed" -> mutableMapOf<String, Any>(
                "status" to "completed",
                "completedAt" to now,
                "completedBy" to actorId,
                "updatedAt" to now
            )
            "no_show" -> mutableMapOf<String, Any>(
                "status" to "no_show",
                "noShowAt" to now,
                "noShowBy" to actorId,
                "updatedAt" to now
            )
            else -> mutableMapOf("status" to status, "updatedAt" to now)
        }
        batch.update(doc.reference, updates)
        if (status == "completed" && previousStatus != "completed" && planId.isNotBlank()) {
            val planRef = db.collection("treatment_plans").document(planId)
            val planSnap = planRef.get().await()
            val completedCount = ((planSnap.getLong("completedSessionCount") ?: 0L).toInt() + 1)
            val sessionCount = ((planSnap.getLong("sessionCount") ?: 1L).toInt()).coerceAtLeast(1)
            val planUpdates = mutableMapOf<String, Any>(
                "completedSessionCount" to FieldValue.increment(1),
                "updatedAt" to now
            )
            if (completedCount >= sessionCount) {
                planUpdates["status"] = TreatmentPlanStatus.COMPLETED
                planUpdates["completedAt"] = now
                val planUserId = planSnap.getString("userId").orEmpty()
                val planPackageId = planSnap.getString("spaPackageId").orEmpty()
                if (planUserId.isNotBlank() && planPackageId.isNotBlank()) {
                    batch.delete(
                        db.collection(ACTIVE_TREATMENT_PLAN_KEYS_COLLECTION)
                            .document(activeTreatmentPlanKey(planUserId, planPackageId))
                    )
                }
            }
            batch.update(
                planRef,
                planUpdates
            )
        }
    }
    batch.commit().await()
}

private suspend fun noShowGraceMillis(db: FirebaseFirestore): Long {
    val settings = runCatching {
        val doc = db.collection(SPA_CAPACITY_SETTINGS_COLLECTION)
            .document(DEFAULT_SPA_CAPACITY_SETTINGS_ID)
            .get()
            .await()
        if (doc.exists()) firestoreDocToSpaCapacitySettings(doc) else SpaCapacitySettings()
    }.getOrDefault(SpaCapacitySettings())
    return settings.noShowGraceMinutes.coerceAtLeast(0) * 60_000L
}

private suspend fun markAppointmentNoShow(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        val graceMillis = noShowGraceMillis(db)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            val startAt = snap.getLong("startAt") ?: 0L
            if (status !in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED) || consultantId != user.uid) {
                throw IllegalStateException("Chỉ tư vấn viên phụ trách mới được đánh dấu khách không đến")
            }
            val now = System.currentTimeMillis()
            if (now < startAt + graceMillis) {
                throw IllegalStateException("Chưa qua thời gian chờ khách trễ")
            }
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.NO_SHOW,
                    "noShowAt" to now,
                    "updatedAt" to now
                )
            )
            null
        }.await()
        syncTreatmentSessionFromAppointment(
            db = db,
            appointmentId = appointmentId,
            status = "no_show",
            actorId = user.uid
        )
    }
}

@Composable
private fun ConsultantHeader(userName: String, onLogout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.28f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Xin chào,", color = Color.White.copy(0.78f), fontSize = 12.sp)
                Text(userName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Tư vấn viên spa", color = Color.White.copy(0.72f), fontSize = 11.sp)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape).background(Color.White.copy(0.22f)).clickable { onLogout() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ConsultantAppointmentCard(
    appointment: SpaAppointment,
    showConfirm: Boolean,
    onOpenDetail: () -> Unit,
    onConfirm: () -> Unit,
    onCheckIn: () -> Unit,
    onStartService: () -> Unit,
    onComplete: () -> Unit,
    onNoShow: () -> Unit
) {
    val meta = appointmentStatusMeta(appointment.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appointment.spaPackageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color(0xFF5A8A80), fontSize = 13.sp)
                }
                StatusPill(status = appointment.status, label = meta.label)
            }
            Spacer(Modifier.height(10.dp))
            InfoLine(Icons.Default.Person, appointment.userName.ifBlank { appointment.userEmail.ifBlank { "Khách hàng" } })
            InfoLine(Icons.Default.Schedule, "${appointment.durationMinutes} phút")
            if (appointment.phoneNumber.isNotBlank()) InfoLine(Icons.Default.CheckCircle, appointment.phoneNumber)
            if (appointment.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(appointment.note, color = Color(0xFF4A7A70), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            when {
                showConfirm -> Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nhận lịch", fontWeight = FontWeight.Bold)
                }
                appointment.status in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED) -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onNoShow,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE8A44A))
                    ) {
                        Text("Không đến", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                    ) {
                        Text("Check-in", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                appointment.status == AppointmentStatus.CHECKED_IN -> Button(
                    onClick = onStartService,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) {
                    Text("Bắt đầu dịch vụ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                appointment.status == AppointmentStatus.IN_SERVICE -> Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) {
                    Text("Hoàn thành", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            if (!showConfirm) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintGreen, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mở chi tiết / chat / liệu trình", color = MintGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, label: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusBg(status)).padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(label, color = statusColor(status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFF4A7A70), fontSize = 13.sp)
    }
}

@Composable
private fun AccessDenied(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(58.dp))
            Spacer(Modifier.height(10.dp))
            Text("Không có quyền tư vấn viên", color = Color(0xFF1A4A40), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Tài khoản này chưa được gán vai trò tư vấn viên.", color = Color(0xFF8ACABA), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MintGreen), shape = RoundedCornerShape(14.dp)) {
                Text("Đăng xuất")
            }
        }
    }
}

@Composable
private fun QuickFilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MintGreen else Color(0xFFF8FFFE))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label ($count)",
            color = if (selected) Color.White else MintGreen,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

private fun statusColor(status: String): Color = when (status) {
    AppointmentStatus.PENDING -> Color(0xFFE8A44A)
    AppointmentStatus.ASSIGNED -> Color(0xFF7B61D1)
    AppointmentStatus.CONFIRMED -> Color(0xFF4A90D9)
    AppointmentStatus.CHECKED_IN -> Color(0xFF2E8A7A)
    AppointmentStatus.IN_SERVICE -> Color(0xFF009688)
    AppointmentStatus.COMPLETED -> MintGreen
    AppointmentStatus.CANCELLED -> Color(0xFFE57373)
    AppointmentStatus.NO_SHOW -> Color(0xFFE8A44A)
    AppointmentStatus.RESCHEDULED -> Color(0xFF4A90D9)
    else -> Color(0xFF8ACABA)
}

private fun statusBg(status: String): Color = when (status) {
    AppointmentStatus.PENDING -> Color(0xFFFFF3E0)
    AppointmentStatus.ASSIGNED -> Color(0xFFF0ECFF)
    AppointmentStatus.CONFIRMED -> Color(0xFFE8F0FB)
    AppointmentStatus.CHECKED_IN -> Color(0xFFEAF9F5)
    AppointmentStatus.IN_SERVICE -> Color(0xFFE0F2F1)
    AppointmentStatus.COMPLETED -> Color(0xFFEAF9F5)
    AppointmentStatus.CANCELLED -> Color(0xFFFFECEC)
    AppointmentStatus.NO_SHOW -> Color(0xFFFFF3E0)
    AppointmentStatus.RESCHEDULED -> Color(0xFFE8F0FB)
    else -> Color(0xFFF5F5F5)
}

private fun matchesConsultantDateFilter(startAt: Long, filter: ConsultantDateQuickFilter): Boolean {
    if (filter == ConsultantDateQuickFilter.ALL || startAt <= 0L) return true
    val startToday = consultantStartOfTodayMillis()
    val endToday = startToday + 86_400_000L
    return when (filter) {
        ConsultantDateQuickFilter.ALL -> true
        ConsultantDateQuickFilter.TODAY -> startAt in startToday until endToday
        ConsultantDateQuickFilter.UPCOMING -> startAt >= endToday
        ConsultantDateQuickFilter.PAST -> startAt < startToday
    }
}

private fun consultantStartOfTodayMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
