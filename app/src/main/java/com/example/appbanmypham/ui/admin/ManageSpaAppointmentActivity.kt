package com.example.appbanmypham.ui.admin

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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import com.example.appbanmypham.model.SPA_APPOINTMENT_STATUSES
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class ManageSpaAppointmentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageSpaAppointmentScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ManageSpaAppointmentScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }

    var appointments by remember { mutableStateOf(listOf<SpaAppointment>()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterStatus by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var cancelTarget by remember { mutableStateOf<SpaAppointment?>(null) }
    var reassignTarget by remember { mutableStateOf<SpaAppointment?>(null) }
    var newConsultantId by remember { mutableStateOf("") }
    var newConsultantEmail by remember { mutableStateOf("") }
    var newConsultantName by remember { mutableStateOf("") }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("appointments").addSnapshotListener { snap, _ ->
            appointments = snap?.documents
                ?.mapNotNull { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                ?.sortedWith(compareBy<SpaAppointment> { it.startAt }.thenByDescending { it.createdAt })
                ?: emptyList()
            isLoading = false
        }
        onDispose { reg?.remove() }
    }

    val filtered = appointments.filter { appointment ->
        val matchStatus = filterStatus == null || appointment.status == filterStatus
        val matchSearch = searchQuery.isBlank() ||
                appointment.spaPackageName.contains(searchQuery, ignoreCase = true) ||
                appointment.userEmail.contains(searchQuery, ignoreCase = true) ||
                appointment.userName.contains(searchQuery, ignoreCase = true) ||
                appointment.phoneNumber.contains(searchQuery, ignoreCase = true)
        matchStatus && matchSearch
    }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f)).align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(top = 48.dp)) {
                    Text("QUAN LY", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Lich hen spa", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${appointments.size} lich - ${appointments.count { it.status == AppointmentStatus.PENDING }} dang cho", color = Color.White.copy(0.8f), fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().offset(y = (-20).dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(BackgroundPrimary)
            ) {
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip("Tat ca", appointments.size, filterStatus == null, MintGreen) { filterStatus = null }
                    }
                    items(SPA_APPOINTMENT_STATUSES) { status ->
                        FilterChip(status.label, appointments.count { it.status == status.key }, filterStatus == status.key, statusColor(status.key)) {
                            filterStatus = if (filterStatus == status.key) null else status.key
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Tim khach, SDT, goi spa...", color = Color(0xFFAAD8CE)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor = Color(0xFF1A4A40),
                        unfocusedTextColor = Color(0xFF1A4A40),
                        cursorColor = MintGreen
                    )
                )
                Spacer(Modifier.height(10.dp))

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MintGreen)
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Khong co lich hen phu hop", color = Color(0xFF8ACABA), fontSize = 15.sp)
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { appointment ->
                            AdminSpaAppointmentCard(
                                appointment = appointment,
                                onReassign = {
                                    reassignTarget = appointment
                                    newConsultantId = appointment.consultantId
                                    newConsultantEmail = appointment.consultantEmail
                                    newConsultantName = appointment.consultantName
                                },
                                onCancel = { cancelTarget = appointment }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    cancelTarget?.let { appointment ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Huy lich hen?", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text = { Text("Lich ${appointment.spaPackageName} se duoc chuyen sang trang thai da huy.", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        db.collection("appointments").document(appointment.id).update(
                            mapOf(
                                "status" to AppointmentStatus.CANCELLED,
                                "cancelledAt" to now,
                                "updatedAt" to now,
                                "cancelReason" to "Admin cancelled"
                            )
                        ).addOnSuccessListener { snackMsg = "Da huy lich hen" }
                            .addOnFailureListener { snackMsg = "Huy lich that bai" }
                        cancelTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Huy lich", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text("Dong", color = MintGreen) }
            }
        )
    }

    reassignTarget?.let { appointment ->
        AlertDialog(
            onDismissRequest = { reassignTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Doi tu van vien", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newConsultantId,
                        onValueChange = { newConsultantId = it },
                        label = { Text("Consultant ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = adminFieldColors()
                    )
                    OutlinedTextField(
                        value = newConsultantEmail,
                        onValueChange = { newConsultantEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = adminFieldColors()
                    )
                    OutlinedTextField(
                        value = newConsultantName,
                        onValueChange = { newConsultantName = it },
                        label = { Text("Ten hien thi") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = adminFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val status = if (appointment.status == AppointmentStatus.PENDING) AppointmentStatus.ASSIGNED else appointment.status
                        val updates = mapOf(
                            "status" to status,
                            "consultantId" to newConsultantId.trim(),
                            "consultantEmail" to newConsultantEmail.trim(),
                            "consultantName" to newConsultantName.trim(),
                            "updatedAt" to now
                        )
                        db.collection("appointments").document(appointment.id).update(updates)
                            .addOnSuccessListener {
                                db.collection("consultation_chat_threads").document(appointment.id).set(
                                    mapOf(
                                        "appointmentId" to appointment.id,
                                        "userId" to appointment.userId,
                                        "userEmail" to appointment.userEmail,
                                        "userName" to appointment.userName,
                                        "consultantId" to newConsultantId.trim(),
                                        "consultantEmail" to newConsultantEmail.trim(),
                                        "consultantName" to newConsultantName.trim(),
                                        "status" to "active",
                                        "updatedAt" to now
                                    ),
                                    SetOptions.merge()
                                )
                                snackMsg = "Da doi tu van vien"
                            }
                            .addOnFailureListener { snackMsg = "Doi tu van vien that bai" }
                        reassignTarget = null
                    },
                    enabled = newConsultantId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) { Text("Luu") }
            },
            dismissButton = {
                TextButton(onClick = { reassignTarget = null }) { Text("Huy", color = MintGreen) }
            }
        )
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) color else Color.White).clickable { onClick() }.padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text("$label ($count)", color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AdminSpaAppointmentCard(appointment: SpaAppointment, onReassign: () -> Unit, onCancel: () -> Unit) {
    val meta = appointmentStatusMeta(appointment.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appointment.spaPackageName, color = Color(0xFF1A4A40), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color(0xFF5A8A80), fontSize = 13.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusBg(appointment.status)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(meta.label, color = statusColor(appointment.status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            AdminInfoLine(Icons.Default.Person, appointment.userName.ifBlank { appointment.userEmail.ifBlank { "Khach hang" } })
            AdminInfoLine(Icons.Default.Phone, appointment.phoneNumber.ifBlank { "-" })
            AdminInfoLine(Icons.Default.Spa, "${"%,.0f".format(appointment.spaPackagePrice)}d - ${appointment.durationMinutes} phut")
            if (appointment.consultantId.isNotBlank()) {
                AdminInfoLine(Icons.Default.EventAvailable, "Tu van: ${appointment.consultantName.ifBlank { appointment.consultantEmail }}")
            }
            if (appointment.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Ghi chu: ${appointment.note}", color = Color(0xFF4A7A70), fontSize = 13.sp)
            }
            if (appointment.status != AppointmentStatus.CANCELLED && appointment.status != AppointmentStatus.COMPLETED) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onReassign,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) {
                        Text("Doi tu van", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373))
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Huy lich", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun adminFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)

@Composable
private fun AdminInfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFF4A7A70), fontSize = 13.sp)
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
