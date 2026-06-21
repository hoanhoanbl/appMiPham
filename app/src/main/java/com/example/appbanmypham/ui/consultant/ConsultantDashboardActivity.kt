package com.example.appbanmypham.ui.consultant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.example.appbanmypham.model.customerConsultationThreadId
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToSpaCapacitySettings
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.firestoreDocToSpaPackage
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    ALL("T\u1EA5t c\u1EA3 ng\u00E0y"),
    TODAY("H\u00F4m nay"),
    UPCOMING("S\u1EAFp t\u1EDBi"),
    PAST("\u0110\u00E3 qua")
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
    var spaPackageImages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var assignedChatThreads by remember { mutableStateOf(listOf<ConsultationChatThread>()) }
    var openChatThreads by remember { mutableStateOf(listOf<ConsultationChatThread>()) }
    val chatThreads = remember(assignedChatThreads, openChatThreads) {
        dedupeChatThreadsByCustomer(assignedChatThreads + openChatThreads)
    }
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
        regs += db.collection("spa_packages")
            .addSnapshotListener { snap, _ ->
                spaPackageImages = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaPackage(it) }.getOrNull() }
                    ?.associate { it.id to it.imageUrl }
                    ?: emptyMap()
            }
        regs += db.collection("consultation_chat_threads")
            .whereEqualTo("consultantId", user.uid)
            .addSnapshotListener { snap, _ ->
                assignedChatThreads = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                    ?.sortedByDescending { chatSortTime(it) }
                    ?: emptyList()
            }
        regs += db.collection("consultation_chat_threads")
            .whereEqualTo("consultantId", "")
            .addSnapshotListener { snap, _ ->
                openChatThreads = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                    ?.filter { it.userId.isNotBlank() }
                    ?.sortedByDescending { chatSortTime(it) }
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
                        label = { Text("L\u1ECBch l\u00E0m vi\u1EC7c") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MintGreen, selectedTextColor = MintGreen)
                    )
                    NavigationBarItem(
                        selected = mainTab == ConsultantHomeTab.CHAT,
                        onClick = { mainTab = ConsultantHomeTab.CHAT },
                        icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        label = { Text("Tin nh\u1EAFn") },
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
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Ch\u1EDD nh\u1EADn (${pending.size})") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("\u0110ang ph\u1EE5 tr\u00E1ch (${assigned.size})") })
                    }

                    val baseList = if (selectedTab == 0) pending else assigned
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ConsultantDateQuickFilter.values()) { option ->
                            QuickFilterPill(
                                label = consultantDateFilterLabel(option),
                                count = baseList.count { matchesConsultantDateFilter(it.startAt, option) },
                                selected = dateFilter == option,
                                onClick = { dateFilter = option }
                            )
                        }
                    }

                    val list = baseList.filter { matchesConsultantDateFilter(it.startAt, dateFilter) }
                    if (list.isEmpty()) {
                        OperationalEmptyState(
                            icon = Icons.Default.EventAvailable,
                            title = if (selectedTab == 0) "Kh\u00F4ng c\u00F3 l\u1ECBch ch\u1EDD nh\u1EADn" else "Ch\u01B0a c\u00F3 l\u1ECBch ph\u00F9 h\u1EE3p",
                            subtitle = if (selectedTab == 0) "C\u00E1c l\u1ECBch m\u1EDBi c\u1EA7n t\u01B0 v\u1EA5n s\u1EBD xu\u1EA5t hi\u1EC7n t\u1EA1i \u0111\u00E2y." else "Th\u1EED \u0111\u1ED5i b\u1ED9 l\u1ECDc ng\u00E0y \u0111\u1EC3 xem l\u1ECBch kh\u00E1c."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.id }) { appointment ->
                                ConsultantAppointmentCard(
                                    appointment = appointment,
                                    packageImageUrl = spaPackageImages[appointment.spaPackageId].orEmpty(),
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
                                                if (result.isSuccess) "\u0110\u00E3 nh\u1EADn l\u1ECBch" else result.exceptionOrNull()?.message ?: "Nh\u1EADn l\u1ECBch th\u1EA5t b\u1EA1i"
                                            )
                                        }
                                    },
                                    onCheckIn = {
                                        scope.launch {
                                            val result = runCatching { checkInAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "\u0110\u00E3 check-in kh\u00E1ch" else result.exceptionOrNull()?.message ?: "Check-in th\u1EA5t b\u1EA1i"
                                            )
                                        }
                                    },
                                    onStartService = {
                                        scope.launch {
                                            val result = runCatching { startServiceAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "\u0110\u00E3 b\u1EAFt \u0111\u1EA7u d\u1ECBch v\u1EE5" else result.exceptionOrNull()?.message ?: "C\u1EADp nh\u1EADt th\u1EA5t b\u1EA1i"
                                            )
                                        }
                                    },
                                    onComplete = {
                                        scope.launch {
                                            val result = runCatching { completeAppointment(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "\u0110\u00E3 c\u1EADp nh\u1EADt l\u1ECBch" else result.exceptionOrNull()?.message ?: "C\u1EADp nh\u1EADt th\u1EA5t b\u1EA1i"
                                            )
                                        }
                                    },
                                    onNoShow = {
                                        scope.launch {
                                            val result = runCatching { markAppointmentNoShow(db, appointment.id, user!!) }
                                            snackbarHostState.showSnackbar(
                                                if (result.isSuccess) "\u0110\u00E3 \u0111\u00E1nh d\u1EA5u kh\u00E1ch kh\u00F4ng \u0111\u1EBFn" else result.exceptionOrNull()?.message ?: "C\u1EADp nh\u1EADt th\u1EA5t b\u1EA1i"
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
        OperationalEmptyState(
            icon = Icons.Default.Chat,
            title = "Ch\u01B0a c\u00F3 cu\u1ED9c tr\u00F2 chuy\u1EC7n",
            subtitle = "Khi b\u1EA1n nh\u1EADn l\u1ECBch, tin nh\u1EAFn c\u1EE7a kh\u00E1ch s\u1EBD \u0111\u01B0\u1EE3c gom \u1EDF \u0111\u00E2y."
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Tin nh\u1EAFn kh\u00E1ch h\u00E0ng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "M\u1ED7i kh\u00E1ch c\u00F3 m\u1ED9t cu\u1ED9c tr\u00F2 chuy\u1EC7n ch\u00EDnh. H\u1ED3 s\u01A1 m\u1EDF t\u1EEB header chat.",
                color = Color(0xFF6C8F87),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        items(threads, key = { it.id }) { thread ->
            val contextLabel = when {
                thread.consultantId.isBlank() -> "Kh\u00E1ch \u0111ang ch\u1EDD t\u01B0 v\u1EA5n"
                thread.treatmentPlanId.isNotBlank() -> "Cu\u1ED9c tr\u00F2 chuy\u1EC7n li\u1EC7u tr\u00ECnh"
                thread.appointmentId.isNotBlank() -> "Cu\u1ED9c tr\u00F2 chuy\u1EC7n l\u1ECBch spa"
                else -> "Cu\u1ED9c tr\u00F2 chuy\u1EC7n ch\u00EDnh"
            }
            val lastActive = formatChatActivity(thread.lastMessageAt.takeIf { it > 0L } ?: thread.updatedAt)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenThread(thread) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = MintGreen)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                thread.userName.ifBlank { thread.userEmail.ifBlank { "Kh\u00E1ch h\u00E0ng" } },
                                color = Color(0xFF1A4A40),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(lastActive, color = Color(0xFF8ACABA), fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(contextLabel, color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            thread.lastMessage.ifBlank { "Ch\u01B0a c\u00F3 tin nh\u1EAFn. M\u1EDF chat \u0111\u1EC3 trao \u0111\u1ED5i." },
                            color = Color(0xFF6C8F87),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OperationalEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color(0xFF1A4A40), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF8ACABA), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun consultantDateFilterLabel(filter: ConsultantDateQuickFilter): String = when (filter) {
    ConsultantDateQuickFilter.ALL -> "T\u1EA5t c\u1EA3 ng\u00E0y"
    ConsultantDateQuickFilter.TODAY -> "H\u00F4m nay"
    ConsultantDateQuickFilter.UPCOMING -> "S\u1EAFp t\u1EDBi"
    ConsultantDateQuickFilter.PAST -> "\u0110\u00E3 qua"
}

private fun formatChatActivity(timeMillis: Long): String {
    if (timeMillis <= 0L) return "M\u1EDBi"
    return SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).format(java.util.Date(timeMillis))
}

private fun chatSortTime(thread: ConsultationChatThread): Long =
    thread.lastMessageAt.takeIf { it > 0L } ?: thread.updatedAt

private fun dedupeChatThreadsByCustomer(threads: List<ConsultationChatThread>): List<ConsultationChatThread> =
    threads
        .groupBy { it.userId.ifBlank { it.userEmail.ifBlank { it.id } } }
        .values
        .mapNotNull { group ->
            group.maxWithOrNull(
                compareBy<ConsultationChatThread> { if (it.userId.isNotBlank() && it.id == customerConsultationThreadId(it.userId)) 1 else 0 }
                    .thenBy { chatSortTime(it) }
            )
        }
        .sortedByDescending { chatSortTime(it) }

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
                throw IllegalStateException("L\u1ECBch n\u00E0y \u0111\u00E3 \u0111\u01B0\u1EE3c t\u01B0 v\u1EA5n vi\u00EAn kh\u00E1c nh\u1EADn")
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
            val appointmentUserId = snap.getString("userId") ?: ""
            val threadId = appointmentUserId.takeIf { it.isNotBlank() }?.let { customerConsultationThreadId(it) }
                ?: planDoc?.getString("chatThreadId")?.takeIf { it.isNotBlank() }
                ?: appointmentId
            planDoc?.let { plan ->
                transaction.update(
                    plan.reference,
                    mapOf(
                        "consultantId" to user.uid,
                        "consultantEmail" to user.email.orEmpty(),
                        "consultantName" to consultantName,
                        "status" to TreatmentPlanStatus.ACTIVE,
                        "revenueRecognizedAt" to (plan.getLong("revenueRecognizedAt") ?: now),
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
                    "userId" to appointmentUserId,
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
                throw IllegalStateException("Ch\u1EC9 t\u01B0 v\u1EA5n vi\u00EAn ph\u1EE5 tr\u00E1ch m\u1EDBi \u0111\u01B0\u1EE3c check-in l\u1ECBch n\u00E0y")
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
                throw IllegalStateException("Ch\u1EC9 l\u1ECBch \u0111\u00E3 check-in m\u1EDBi \u0111\u01B0\u1EE3c b\u1EAFt \u0111\u1EA7u d\u1ECBch v\u1EE5")
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
                throw IllegalStateException("Ch\u1EC9 l\u1ECBch \u0111ang l\u00E0m d\u1ECBch v\u1EE5 m\u1EDBi \u0111\u01B0\u1EE3c ho\u00E0n th\u00E0nh")
            }
            if (startAt > now) {
                throw IllegalStateException("Ch\u01B0a t\u1EDBi gi\u1EDD h\u1EB9n, kh\u00F4ng th\u1EC3 ho\u00E0n th\u00E0nh l\u1ECBch t\u01B0\u01A1ng lai")
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
                throw IllegalStateException("Ch\u1EC9 t\u01B0 v\u1EA5n vi\u00EAn ph\u1EE5 tr\u00E1ch m\u1EDBi \u0111\u01B0\u1EE3c \u0111\u00E1nh d\u1EA5u kh\u00E1ch kh\u00F4ng \u0111\u1EBFn")
            }
            val now = System.currentTimeMillis()
            if (now < startAt + graceMillis) {
                throw IllegalStateException("Ch\u01B0a qua th\u1EDDi gian ch\u1EDD kh\u00E1ch tr\u1EC5")
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
                Text("Xin ch\u00E0o,", color = Color.White.copy(0.78f), fontSize = 12.sp)
                Text(userName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("T\u01B0 v\u1EA5n vi\u00EAn spa", color = Color.White.copy(0.72f), fontSize = 11.sp)
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
    packageImageUrl: String,
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE4F3EF)),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(width = 86.dp, height = 96.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFEAF9F5)),
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
                        Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            appointment.spaPackageName,
                            color = Color(0xFF173F37),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(status = appointment.status, label = meta.label)
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoLine(Icons.Default.EventAvailable, "${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}")
                    InfoLine(Icons.Default.Person, appointment.userName.ifBlank { appointment.userEmail.ifBlank { "Khách hàng" } })
                    InfoLine(Icons.Default.Schedule, "${appointment.durationMinutes} phút")
                }
            }
            if (appointment.phoneNumber.isNotBlank()) InfoLine(Icons.Default.CheckCircle, appointment.phoneNumber)
            if (appointment.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF7FB))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(appointment.note, color = Color(0xFF6C5B66), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
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
                    Text("Nh\u1EADn l\u1ECBch", fontWeight = FontWeight.Bold)
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
                        Text("Kh\u00F4ng \u0111\u1EBFn", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                    Text("B\u1EAFt \u0111\u1EA7u d\u1ECBch v\u1EE5", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                appointment.status == AppointmentStatus.IN_SERVICE -> Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) {
                    Text("Ho\u00E0n th\u00E0nh", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            if (!showConfirm) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintGreen, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("M\u1EDF b\u1EA3ng v\u1EADn h\u00E0nh", color = MintGreen, fontWeight = FontWeight.SemiBold)
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
            Text("Kh\u00F4ng c\u00F3 quy\u1EC1n t\u01B0 v\u1EA5n vi\u00EAn", color = Color(0xFF1A4A40), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("T\u00E0i kho\u1EA3n n\u00E0y ch\u01B0a \u0111\u01B0\u1EE3c g\u00E1n vai tr\u00F2 t\u01B0 v\u1EA5n vi\u00EAn.", color = Color(0xFF8ACABA), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MintGreen), shape = RoundedCornerShape(14.dp)) {
                Text("\u0110\u0103ng xu\u1EA5t")
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
