package com.example.appbanmypham.ui.product

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.ChatSenderRole
import com.example.appbanmypham.model.ConsultationChatMessage
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.firestoreDocToConsultationChatMessage
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.toFirestoreMap
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

class CustomerAppointmentChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appointmentId = intent.getStringExtra("appointment_id").orEmpty()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    CustomerAppointmentChatScreen(appointmentId = appointmentId, onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun CustomerAppointmentChatScreen(appointmentId: String, onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var appointment by remember { mutableStateOf<SpaAppointment?>(null) }
    var thread by remember { mutableStateOf<ConsultationChatThread?>(null) }
    var messages by remember { mutableStateOf(listOf<ConsultationChatMessage>()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }

    val canChat = user != null &&
            appointment?.userId == user.uid &&
            appointment?.consultantId?.isNotBlank() == true &&
            appointment?.status != AppointmentStatus.PENDING &&
            thread != null

    DisposableEffect(appointmentId, user?.uid) {
        if (appointmentId.isBlank()) return@DisposableEffect onDispose {}
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("appointments").document(appointmentId)
            .addSnapshotListener { snap, _ ->
                appointment = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                isLoading = false
            }
        regs += db.collection("consultation_chat_threads").document(appointmentId)
            .addSnapshotListener { snap, _ ->
                thread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
            }
        regs += db.collection("consultation_chat_messages")
            .whereEqualTo("threadId", appointmentId)
            .addSnapshotListener { snap, _ ->
                messages = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatMessage(it) }.getOrNull() }
                    ?.filter { it.userId == user?.uid }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        onDispose { regs.forEach { it.remove() } }
    }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(onBack = onBack, title = appointment?.spaPackageName ?: "Chat tu van")
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MintGreen) }
                user == null || appointment?.userId != user.uid -> EmptyState("Ban khong co quyen xem lich nay.")
                appointment == null -> EmptyState("Khong tim thay lich hen.")
                appointment?.consultantId.isNullOrBlank() -> EmptyState("Tu van vien chua nhan lich. Chat se mo sau khi lich duoc phan cong.")
                else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    InfoCard(appointment!!)
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = MintGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Trao doi voi tu van vien", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (messages.isEmpty()) {
                                    Text("Chua co tin nhan nao.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                                } else {
                                    messages.takeLast(12).forEach { ChatBubble(it, user.uid) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier.weight(1f),
                                    enabled = canChat && !isSending,
                                    placeholder = { Text("Nhap tin nhan...", color = Color(0xFFAAD8CE)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors()
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val appt = appointment ?: return@IconButton
                                        val text = messageText.trim()
                                        if (text.isBlank() || user == null) return@IconButton
                                        scope.launch {
                                            isSending = true
                                            val result = runCatching {
                                                sendAppointmentCustomerMessage(
                                                    db = db,
                                                    appointment = appt,
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
                                    enabled = canChat && messageText.isNotBlank() && !isSending,
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

@Composable
private fun Header(onBack: () -> Unit, title: String) {
    Box(Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal).statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun InfoCard(appointment: SpaAppointment) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(appointment.spaPackageName, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color(0xFF5A8A80), fontSize = 12.sp)
                    Text("Tu van: ${appointment.consultantName.ifBlank { appointment.consultantEmail }}", color = MintGreen, fontSize = 12.sp)
                }
            }
            if (appointment.consultantNote.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Ghi chu tu van", color = Color(0xFF8ACABA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(appointment.consultantNote, color = Color(0xFF4A7A70), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ConsultationChatMessage, currentUserId: String) {
    val mine = message.senderId == currentUserId
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(if (mine) Color(0xFFEAF9F5) else Color(0xFFF7F7F7)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(message.senderName.ifBlank { message.senderRole }, color = if (mine) MintGreen else Color(0xFF8ACABA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(message.message, color = Color(0xFF1A4A40), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF8ACABA), fontSize = 14.sp, modifier = Modifier.padding(24.dp))
    }
}

private suspend fun sendAppointmentCustomerMessage(
    db: FirebaseFirestore,
    appointment: SpaAppointment,
    senderId: String,
    senderName: String,
    message: String
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val chatMessage = ConsultationChatMessage(
            threadId = appointment.id,
            appointmentId = appointment.id,
            userId = appointment.userId,
            consultantId = appointment.consultantId,
            senderId = senderId,
            senderName = senderName,
            senderRole = ChatSenderRole.CUSTOMER,
            message = message,
            createdAt = now
        )
        db.collection("consultation_chat_messages").add(chatMessage.toFirestoreMap(includeCreatedAt = true)).await()
        db.collection("consultation_chat_threads").document(appointment.id).update(
            mapOf("lastMessage" to message, "lastMessageAt" to now, "updatedAt" to now)
        ).await()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
