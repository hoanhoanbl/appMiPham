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
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
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
import com.example.appbanmypham.model.TREATMENT_PLAN_STATUSES
import com.example.appbanmypham.model.TREATMENT_SESSION_STATUSES
import com.example.appbanmypham.model.TreatmentPlan
import com.example.appbanmypham.model.TreatmentProgressPhoto
import com.example.appbanmypham.model.TreatmentSession
import com.example.appbanmypham.model.firestoreDocToTreatmentPlan
import com.example.appbanmypham.model.firestoreDocToTreatmentProgressPhoto
import com.example.appbanmypham.model.firestoreDocToTreatmentSession
import com.example.appbanmypham.model.progressPhotoTypeMeta
import com.example.appbanmypham.model.treatmentPlanStatusMeta
import com.example.appbanmypham.model.treatmentSessionStatusMeta
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ManageTreatmentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageTreatmentScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ManageTreatmentScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }

    var plans by remember { mutableStateOf(listOf<TreatmentPlan>()) }
    var sessions by remember { mutableStateOf(listOf<TreatmentSession>()) }
    var photos by remember { mutableStateOf(listOf<TreatmentProgressPhoto>()) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var planStatusFilter by remember { mutableStateOf<String?>(null) }
    var sessionStatusFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var reassignTarget by remember { mutableStateOf<TreatmentPlan?>(null) }
    var newConsultantId by remember { mutableStateOf("") }
    var newConsultantEmail by remember { mutableStateOf("") }
    var newConsultantName by remember { mutableStateOf("") }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    DisposableEffect(Unit) {
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("treatment_plans").addSnapshotListener { snap, _ ->
            plans = snap?.documents
                ?.mapNotNull { runCatching { firestoreDocToTreatmentPlan(it) }.getOrNull() }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            if (selectedPlanId == null && plans.isNotEmpty()) selectedPlanId = plans.first().id
            isLoading = false
        }
        regs += db.collection("treatment_sessions").addSnapshotListener { snap, _ ->
            sessions = snap?.documents
                ?.mapNotNull { runCatching { firestoreDocToTreatmentSession(it) }.getOrNull() }
                ?.sortedWith(compareBy<TreatmentSession> { it.treatmentPlanId }.thenBy { it.sessionNumber })
                ?: emptyList()
        }
        regs += db.collection("treatment_progress_photos").addSnapshotListener { snap, _ ->
            photos = snap?.documents
                ?.mapNotNull { runCatching { firestoreDocToTreatmentProgressPhoto(it) }.getOrNull() }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        }
        onDispose { regs.forEach { it.remove() } }
    }

    val filteredPlans = plans.filter { plan ->
        val matchesStatus = planStatusFilter == null || plan.status == planStatusFilter
        val matchesSearch = searchQuery.isBlank() ||
                plan.packageName.contains(searchQuery, ignoreCase = true) ||
                plan.userName.contains(searchQuery, ignoreCase = true) ||
                plan.userEmail.contains(searchQuery, ignoreCase = true) ||
                plan.consultantName.contains(searchQuery, ignoreCase = true) ||
                plan.consultantEmail.contains(searchQuery, ignoreCase = true)
        matchesStatus && matchesSearch
    }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: filteredPlans.firstOrNull()
    val selectedSessions = sessions
        .filter { it.treatmentPlanId == selectedPlan?.id }
        .filter { sessionStatusFilter == null || it.status == sessionStatusFilter }
    val selectedPhotos = photos.filter { it.treatmentPlanId == selectedPlan?.id }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal).statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f)).align(Alignment.TopStart)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.padding(top = 52.dp)) {
                    Text("SPA", color = Color.White.copy(0.74f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Quan ly lieu trinh", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("${plans.size} lieu trinh - ${sessions.count { it.status == "no_show" }} no-show", color = Color.White.copy(0.8f), fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tim khach, tu van vien, goi spa...", color = Color(0xFFAAD8CE)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors()
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterPill("Tat ca", filteredPlans.size, planStatusFilter == null, MintGreen) { planStatusFilter = null } }
                        items(TREATMENT_PLAN_STATUSES) { status ->
                            FilterPill(status.label, plans.count { it.status == status.key }, planStatusFilter == status.key, MintGreen) {
                                planStatusFilter = if (planStatusFilter == status.key) null else status.key
                            }
                        }
                    }
                }
                when {
                    isLoading -> item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MintGreen) } }
                    filteredPlans.isEmpty() -> item { EmptyText("Khong co lieu trinh phu hop") }
                    else -> {
                        items(filteredPlans, key = { it.id }) { plan ->
                            AdminPlanCard(
                                plan = plan,
                                selected = selectedPlan?.id == plan.id,
                                onClick = { selectedPlanId = plan.id },
                                onReassign = {
                                    reassignTarget = plan
                                    newConsultantId = plan.consultantId
                                    newConsultantEmail = plan.consultantEmail
                                    newConsultantName = plan.consultantName
                                }
                            )
                        }
                    }
                }
                selectedPlan?.let { plan ->
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.EventAvailable, "Buoi dieu tri")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { FilterPill("Tat ca", sessions.count { it.treatmentPlanId == plan.id }, sessionStatusFilter == null, MintGreen) { sessionStatusFilter = null } }
                                items(TREATMENT_SESSION_STATUSES) { status ->
                                    FilterPill(status.label, sessions.count { it.treatmentPlanId == plan.id && it.status == status.key }, sessionStatusFilter == status.key, statusColor(status.key)) {
                                        sessionStatusFilter = if (sessionStatusFilter == status.key) null else status.key
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (selectedSessions.isEmpty()) EmptyText("Khong co buoi phu hop") else selectedSessions.forEach { session ->
                                SessionRow(session)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    item {
                        SectionCard {
                            SectionTitle(Icons.Default.Photo, "Anh tien trinh")
                            if (selectedPhotos.isEmpty()) {
                                EmptyText("Chua co anh tien trinh")
                            } else {
                                selectedPhotos.forEach { photo ->
                                    PhotoRow(
                                        photo = photo,
                                        onHide = {
                                            db.collection("treatment_progress_photos").document(photo.id).update(
                                                mapOf(
                                                    "isHidden" to true,
                                                    "hiddenReason" to "Admin hidden",
                                                    "hiddenAt" to System.currentTimeMillis(),
                                                    "updatedAt" to System.currentTimeMillis()
                                                )
                                            ).addOnSuccessListener { snackMsg = "Da an anh" }
                                                .addOnFailureListener { snackMsg = "An anh that bai" }
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

    reassignTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = { reassignTarget = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Doi tu van vien", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newConsultantId, { newConsultantId = it }, label = { Text("Consultant ID") }, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(newConsultantEmail, { newConsultantEmail = it }, label = { Text("Email") }, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(newConsultantName, { newConsultantName = it }, label = { Text("Ten hien thi") }, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val updates = mapOf(
                            "consultantId" to newConsultantId.trim(),
                            "consultantEmail" to newConsultantEmail.trim(),
                            "consultantName" to newConsultantName.trim(),
                            "updatedAt" to now
                        )
                        db.collection("treatment_plans").document(plan.id).update(updates)
                            .addOnSuccessListener {
                                db.collection("appointments").document(plan.appointmentId).update(updates)
                                db.collection("consultation_chat_threads").document(plan.chatThreadId.ifBlank { plan.appointmentId }).update(updates)
                                db.collection("treatment_sessions")
                                    .whereEqualTo("treatmentPlanId", plan.id)
                                    .get()
                                    .addOnSuccessListener { snap ->
                                        val batch = db.batch()
                                        snap.documents.forEach { batch.update(it.reference, updates) }
                                        batch.commit()
                                    }
                                db.collection("treatment_progress_photos")
                                    .whereEqualTo("treatmentPlanId", plan.id)
                                    .get()
                                    .addOnSuccessListener { snap ->
                                        val batch = db.batch()
                                        snap.documents.forEach { batch.update(it.reference, updates) }
                                        batch.commit()
                                    }
                                snackMsg = "Da doi tu van vien"
                            }
                            .addOnFailureListener { snackMsg = "Doi tu van vien that bai" }
                        reassignTarget = null
                    },
                    enabled = newConsultantId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                ) { Text("Luu") }
            },
            dismissButton = { TextButton(onClick = { reassignTarget = null }) { Text("Huy", color = MintGreen) } }
        )
    }
}

@Composable
private fun AdminPlanCard(plan: TreatmentPlan, selected: Boolean, onClick: () -> Unit, onReassign: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEAF9F5) else Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MintGreen.copy(0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.packageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plan.userName.ifBlank { plan.userEmail }, color = Color(0xFF5A8A80), fontSize = 12.sp)
                    Text("Tu van: ${plan.consultantName.ifBlank { plan.consultantEmail.ifBlank { "Chua ro" } }}", color = MintGreen, fontSize = 12.sp)
                    Text("Chat: ${plan.chatThreadId.ifBlank { plan.appointmentId }.ifBlank { "Chua co" }}", color = Color(0xFF8ACABA), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(treatmentPlanStatusMeta(plan.status).label, color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${plan.completedSessionCount}/${plan.sessionCount} buoi", color = Color(0xFF4A7A70), fontSize = 12.sp)
                TextButton(onClick = onReassign, contentPadding = PaddingValues(0.dp)) { Text("Doi tu van", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) color else Color(0xFFF8FFFE)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("$label ($count)", color = if (selected) Color.White else color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun SessionRow(session: TreatmentSession) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FFFE)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Buoi ${session.sessionNumber}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
        Column(Modifier.weight(1f)) {
            Text(session.dateLabel.ifBlank { "Chua sap lich" } + session.timeSlotLabel.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty(), color = Color(0xFF5A8A80), fontSize = 12.sp)
            Text(treatmentSessionStatusMeta(session.status).label, color = statusColor(session.status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhotoRow(photo: TreatmentProgressPhoto, onHide: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF9F5))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(progressPhotoTypeMeta(photo.photoType).label, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(photo.uploaderName.ifBlank { photo.uploadedBy }, color = Color(0xFF8ACABA), fontSize = 11.sp)
            if (photo.note.isNotBlank()) Text(photo.note, color = Color(0xFF5A8A80), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onHide) { Text("An", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = Color(0xFF8ACABA), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
}

private fun statusColor(status: String): Color = when (status) {
    "scheduled", "rescheduled" -> Color(0xFF4A90D9)
    "completed" -> MintGreen
    "cancelled" -> Color(0xFFE57373)
    "no_show" -> Color(0xFFE8A44A)
    else -> Color(0xFF8ACABA)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
