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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.ui.auth.LoginActivity
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
    var selectedTab by remember { mutableStateOf(0) }

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
        onDispose { regs.forEach { it.remove() } }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            isRoleLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }

            !isConsultant -> AccessDenied(onLogout = onLogout, modifier = Modifier.padding(padding))

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ConsultantHeader(userName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Consultant", onLogout = onLogout)

                TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = MintGreen) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Cho xac nhan (${pending.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Lich cua toi (${assigned.size})") })
                }

                val list = if (selectedTab == 0) pending else assigned
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (selectedTab == 0) "Khong co lich pending" else "Chua co lich duoc giao", color = Color(0xFF8ACABA), fontSize = 15.sp)
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
                                            if (result.isSuccess) "Da nhan lich va mo chat" else result.exceptionOrNull()?.message ?: "Nhan lich that bai"
                                        )
                                    }
                                },
                                onComplete = {
                                    scope.launch {
                                        val result = runCatching { completeAppointment(db, appointment.id, user!!) }
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) "Da cap nhat lich" else result.exceptionOrNull()?.message ?: "Cap nhat that bai"
                                        )
                                    }
                                },
                                onNoShow = {
                                    scope.launch {
                                        val result = runCatching { markAppointmentNoShow(db, appointment.id, user!!) }
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) "Da danh dau khach khong den" else result.exceptionOrNull()?.message ?: "Cap nhat that bai"
                                        )
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

private suspend fun confirmAppointment(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            if (status != AppointmentStatus.PENDING || consultantId.isNotBlank()) {
                throw IllegalStateException("Lich nay da duoc tu van vien khac nhan")
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
            val threadRef = db.collection("consultation_chat_threads").document(appointmentId)
            val thread = ConsultationChatThread(
                id = appointmentId,
                appointmentId = appointmentId,
                userId = snap.getString("userId") ?: "",
                userEmail = snap.getString("userEmail") ?: "",
                userName = snap.getString("userName") ?: "",
                consultantId = user.uid,
                consultantEmail = user.email.orEmpty(),
                consultantName = consultantName,
                status = ChatThreadStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
            transaction.set(threadRef, thread.toFirestoreMap(includeCreatedAt = true))
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
            if (status !in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED) || consultantId != user.uid) {
                throw IllegalStateException("Chi tu van vien da nhan lich moi duoc hoan thanh lich nay")
            }
            val now = System.currentTimeMillis()
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
    }
}

private suspend fun markAppointmentNoShow(db: FirebaseFirestore, appointmentId: String, user: com.google.firebase.auth.FirebaseUser) {
    withContext(Dispatchers.IO) {
        val ref = db.collection("appointments").document(appointmentId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val status = snap.getString("status") ?: AppointmentStatus.PENDING
            val consultantId = snap.getString("consultantId") ?: ""
            if (status !in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED) || consultantId != user.uid) {
                throw IllegalStateException("Chi tu van vien phu trach moi duoc danh dau khach khong den")
            }
            val now = System.currentTimeMillis()
            transaction.update(
                ref,
                mapOf(
                    "status" to AppointmentStatus.NO_SHOW,
                    "updatedAt" to now
                )
            )
            null
        }.await()
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
                Text("Xin chao,", color = Color.White.copy(0.78f), fontSize = 12.sp)
                Text(userName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Tu van vien spa", color = Color.White.copy(0.72f), fontSize = 11.sp)
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
            InfoLine(Icons.Default.Person, appointment.userName.ifBlank { appointment.userEmail.ifBlank { "Khach hang" } })
            InfoLine(Icons.Default.Schedule, "${appointment.durationMinutes} phut")
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
                    Text("Nhan lich", fontWeight = FontWeight.Bold)
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
                        Text("Khong den", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                    ) {
                        Text("Hoan thanh", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
            if (!showConfirm) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintGreen, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mo chi tiet / chat / lieu trinh", color = MintGreen, fontWeight = FontWeight.SemiBold)
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
            Text("Khong co quyen tu van vien", color = Color(0xFF1A4A40), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Tai khoan nay chua duoc gan role consultant.", color = Color(0xFF8ACABA), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MintGreen), shape = RoundedCornerShape(14.dp)) {
                Text("Dang xuat")
            }
        }
    }
}

private fun statusColor(status: String): Color = when (status) {
    AppointmentStatus.PENDING -> Color(0xFFE8A44A)
    AppointmentStatus.ASSIGNED -> Color(0xFF7B61D1)
    AppointmentStatus.CONFIRMED -> Color(0xFF4A90D9)
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
    AppointmentStatus.COMPLETED -> Color(0xFFEAF9F5)
    AppointmentStatus.CANCELLED -> Color(0xFFFFECEC)
    AppointmentStatus.NO_SHOW -> Color(0xFFFFF3E0)
    AppointmentStatus.RESCHEDULED -> Color(0xFFE8F0FB)
    else -> Color(0xFFF5F5F5)
}
