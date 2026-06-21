package com.example.appbanmypham.ui.consultant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.treatmentPlanStatusMeta
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

class ConsultantCustomerProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val userId = intent.getStringExtra("user_id").orEmpty()
        val threadId = intent.getStringExtra("thread_id").orEmpty()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ConsultantCustomerProfileScreen(userId = userId, threadId = threadId, onBack = { finish() })
                }
            }
        }
    }
}

private data class CustomerProfile(
    val name: String = "",
    val email: String = "",
    val phone: String = ""
)

@Composable
fun ConsultantCustomerProfileScreen(userId: String, threadId: String, onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val signedUser = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(CustomerProfile()) }
    var appointments by remember { mutableStateOf(listOf<SpaAppointment>()) }
    var plans by remember { mutableStateOf(listOf<TreatmentPlan>()) }
    var photos by remember { mutableStateOf(listOf<TreatmentProgressPhoto>()) }
    var isLoading by remember { mutableStateOf(userId.isNotBlank()) }
    var accessDenied by remember { mutableStateOf(false) }

    LaunchedEffect(userId, threadId, signedUser?.uid) {
        if (userId.isBlank() || threadId.isBlank() || signedUser == null) {
            accessDenied = true
            isLoading = false
            return@LaunchedEffect
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val threadDoc = db.collection("consultation_chat_threads").document(threadId).get().await()
                    val threadUserId = threadDoc.getString("userId").orEmpty()
                    val threadConsultantId = threadDoc.getString("consultantId").orEmpty()
                    if (!threadDoc.exists() || threadUserId != userId || threadConsultantId != signedUser.uid) {
                        throw IllegalStateException("Access denied")
                    }
                    val userDoc = db.collection("users").document(userId).get().await()
                    val loadedAppointments = db.collection("appointments")
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                        .filter { it.consultantId == signedUser.uid }
                        .sortedByDescending { it.startAt.takeIf { time -> time > 0L } ?: it.createdAt }
                    val loadedPlans = db.collection("treatment_plans")
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { runCatching { firestoreDocToTreatmentPlan(it) }.getOrNull() }
                        .filter { it.consultantId == signedUser.uid }
                        .sortedByDescending { it.createdAt }
                    val loadedPhotos = db.collection("treatment_progress_photos")
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { runCatching { firestoreDocToTreatmentProgressPhoto(it) }.getOrNull() }
                        .filter { !it.isHidden && it.consultantId == signedUser.uid }
                        .sortedByDescending { it.createdAt }
                    Triple(
                        CustomerProfile(
                            name = userDoc.getString("name") ?: userDoc.getString("displayName") ?: loadedAppointments.firstOrNull()?.userName.orEmpty(),
                            email = userDoc.getString("email") ?: loadedAppointments.firstOrNull()?.userEmail.orEmpty(),
                            phone = userDoc.getString("phone") ?: loadedAppointments.firstOrNull()?.phoneNumber.orEmpty()
                        ),
                        loadedAppointments,
                        loadedPlans to loadedPhotos
                    )
                }
            }
            result.getOrNull()?.let { loaded ->
                accessDenied = false
                profile = loaded.first
                appointments = loaded.second
                plans = loaded.third.first
                photos = loaded.third.second
            } ?: run { accessDenied = true }
            isLoading = false
        }
    }

    Scaffold(containerColor = BackgroundPrimary) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ProfileHeader(profile = profile, onBack = onBack)
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
            } else if (accessDenied) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("B\u1EA1n kh\u00F4ng c\u00F3 quy\u1EC1n xem h\u1ED3 s\u01A1 kh\u00E1ch n\u00E0y.", color = Color(0xFF8ACABA), fontSize = 14.sp)
                }
            } else {
                val noShowCount = appointments.count { it.status == AppointmentStatus.NO_SHOW }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.Person, "Th\u00F4ng tin kh\u00E1ch")
                            InfoText("T\u00EAn", profile.name.ifBlank { "Kh\u00E1ch h\u00E0ng" })
                            InfoText("Email", profile.email.ifBlank { "-" })
                            InfoText("S\u0110T", profile.phone.ifBlank { "-" })
                            InfoText("Kh\u00F4ng \u0111\u1EBFn", "$noShowCount l\u1EA7n")
                        }
                    }
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.Spa, "Li\u1EC7u tr\u00ECnh")
                            if (plans.isEmpty()) {
                                Text("Ch\u01B0a c\u00F3 li\u1EC7u tr\u00ECnh li\u00EAn quan.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    plans.forEach { plan -> PlanRow(plan) }
                                }
                            }
                        }
                    }
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.EventAvailable, "L\u1ECBch s\u1EED \u0111\u1EB7t l\u1ECBch")
                            if (appointments.isEmpty()) {
                                Text("Ch\u01B0a c\u00F3 l\u1ECBch h\u1EB9n.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    appointments.take(12).forEach { AppointmentRow(it) }
                                }
                            }
                        }
                    }
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.PhotoCamera, "\u1EA2nh ti\u1EBFn tr\u00ECnh")
                            if (photos.isEmpty()) {
                                Text("Ch\u01B0a c\u00F3 \u1EA3nh ti\u1EBFn tr\u00ECnh.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                            } else {
                                Text("${photos.size} \u1EA3nh \u0111\u00E3 \u0111\u01B0\u1EE3c c\u1EADp nh\u1EADt trong c\u00E1c li\u1EC7u tr\u00ECnh.", color = Color(0xFF4A7A70), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: CustomerProfile, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(38.dp).clip(CircleShape).background(Color.White.copy(0.23f))) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(profile.name.ifBlank { profile.email.ifBlank { "Kh\u00E1ch h\u00E0ng" } }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("H\u1ED3 s\u01A1 v\u00E0 l\u1ECBch s\u1EED \u0111\u1EB7t spa", color = Color.White.copy(0.78f), fontSize = 11.sp)
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
private fun PlanRow(plan: TreatmentPlan) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8FFFE)).padding(12.dp)) {
        Text(plan.packageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(3.dp))
        Text("${plan.completedSessionCount}/${plan.sessionCount} bu\u1ED5i - ${treatmentPlanStatusMeta(plan.status).label}", color = Color(0xFF5A8A80), fontSize = 12.sp)
    }
}

@Composable
private fun AppointmentRow(appointment: SpaAppointment) {
    val meta = appointmentStatusMeta(appointment.status)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8FFFE)).padding(12.dp)) {
        Text(appointment.spaPackageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color(0xFF5A8A80), fontSize = 12.sp)
        Text(meta.label, color = if (appointment.status == AppointmentStatus.NO_SHOW) Color(0xFFE8A44A) else MintGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
