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
import com.example.appbanmypham.model.ChatThreadStatus
import com.example.appbanmypham.model.ConsultationChatMessage
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.customerConsultationThreadId
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
import com.google.firebase.firestore.SetOptions
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
    val threadId = remember(user?.uid) { user?.uid?.let { customerConsultationThreadId(it) }.orEmpty() }

    var appointment by remember { mutableStateOf<SpaAppointment?>(null) }
    var thread by remember { mutableStateOf<ConsultationChatThread?>(null) }
    var messages by remember { mutableStateOf(listOf<ConsultationChatMessage>()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(user != null) }
    var isSending by remember { mutableStateOf(false) }

    val canChat = user != null && threadId.isNotBlank() && (thread == null || thread?.userId == user.uid)

    DisposableEffect(appointmentId, user?.uid) {
        if (appointmentId.isBlank() || user == null) {
            appointment = null
            return@DisposableEffect onDispose {}
        }
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("appointments").document(appointmentId)
            .addSnapshotListener { snap, _ ->
                appointment = snap?.takeIf { it.exists() }
                    ?.let { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                    ?.takeIf { it.userId == user.uid }
            }
        onDispose { regs.forEach { it.remove() } }
    }

    LaunchedEffect(threadId, appointment?.id) {
        val signedUser = user ?: run {
            isLoading = false
            return@LaunchedEffect
        }
        if (threadId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        runCatching { ensureCustomerConsultationThread(db, threadId, signedUser, appointment) }
        isLoading = false
    }

    DisposableEffect(threadId, user?.uid) {
        if (threadId.isBlank() || user == null) {
            thread = null
            messages = emptyList()
            isLoading = false
            return@DisposableEffect onDispose {}
        }
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("consultation_chat_threads").document(threadId)
            .addSnapshotListener { snap, _ ->
                thread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                isLoading = false
            }
        regs += db.collection("consultation_chat_messages")
            .whereEqualTo("threadId", threadId)
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
            Header(onBack = onBack, title = "Chat v\u1EDBi t\u01B0 v\u1EA5n vi\u00EAn")
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MintGreen) }
                user == null -> EmptyState("Vui l\u00F2ng \u0111\u0103ng nh\u1EADp \u0111\u1EC3 chat v\u1EDBi t\u01B0 v\u1EA5n vi\u00EAn.")
                else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    appointment?.let {
                        InfoCard(it)
                        Spacer(Modifier.height(12.dp))
                    }
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
                                Column(Modifier.weight(1f)) {
                                    Text("Trao \u0111\u1ED5i v\u1EDBi t\u01B0 v\u1EA5n vi\u00EAn", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        thread?.consultantName?.ifBlank { thread?.consultantEmail.orEmpty() }?.takeIf { it.isNotBlank() }
                                            ?: "T\u01B0 v\u1EA5n vi\u00EAn s\u1EBD ph\u1EA3n h\u1ED3i trong cu\u1ED9c tr\u00F2 chuy\u1EC7n n\u00E0y",
                                        color = Color(0xFF8ACABA),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (messages.isEmpty()) {
                                    Text("Ch\u01B0a c\u00F3 tin nh\u1EAFn n\u00E0o. H\u00E3y g\u1EEDi c\u00E2u h\u1ECFi \u0111\u1EA7u ti\u00EAn \u0111\u1EC3 \u0111\u01B0\u1EE3c t\u01B0 v\u1EA5n.", color = Color(0xFF8ACABA), fontSize = 13.sp)
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
                                    placeholder = { Text("Nh\u1EADp tin nh\u1EAFn...", color = Color(0xFFAAD8CE)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors()
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val text = messageText.trim()
                                        if (text.isBlank() || user == null) return@IconButton
                                        scope.launch {
                                            isSending = true
                                            val result = runCatching {
                                                sendAppointmentCustomerMessage(
                                                    db = db,
                                                    thread = thread,
                                                    threadId = threadId,
                                                    appointment = appointment,
                                                    senderId = user.uid,
                                                    senderName = user.displayName ?: user.email?.substringBefore("@") ?: "Kh\u00E1ch h\u00E0ng",
                                                    message = text
                                                )
                                            }
                                            if (result.isSuccess) messageText = ""
                                            snackbarHostState.showSnackbar(if (result.isSuccess) "\u0110\u00E3 g\u1EEDi" else "G\u1EEDi tin th\u1EA5t b\u1EA1i")
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
                    if (appointment.consultantName.isNotBlank() || appointment.consultantEmail.isNotBlank()) {
                        Text("T\u01B0 v\u1EA5n: ${appointment.consultantName.ifBlank { appointment.consultantEmail }}", color = MintGreen, fontSize = 12.sp)
                    }
                }
            }
            if (appointment.consultantNote.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Ghi ch\u00FA t\u01B0 v\u1EA5n", color = Color(0xFF8ACABA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    thread: ConsultationChatThread?,
    threadId: String,
    appointment: SpaAppointment?,
    senderId: String,
    senderName: String,
    message: String
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val activeThread = thread ?: ConsultationChatThread(
            id = threadId,
            appointmentId = appointment?.id.orEmpty(),
            userId = senderId,
            userEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
            userName = senderName,
            consultantId = appointment?.consultantId.orEmpty(),
            consultantEmail = appointment?.consultantEmail.orEmpty(),
            consultantName = appointment?.consultantName.orEmpty(),
            status = ChatThreadStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        val chatMessage = ConsultationChatMessage(
            threadId = threadId,
            appointmentId = appointment?.id.orEmpty(),
            treatmentPlanId = activeThread.treatmentPlanId,
            userId = activeThread.userId.ifBlank { senderId },
            consultantId = activeThread.consultantId,
            senderId = senderId,
            senderName = senderName,
            senderRole = ChatSenderRole.CUSTOMER,
            message = message,
            createdAt = now
        )
        db.collection("consultation_chat_threads").document(threadId).set(
            activeThread.copy(lastMessage = message, lastMessageAt = now, updatedAt = now).toFirestoreMap(includeCreatedAt = activeThread.createdAt <= 0L),
            SetOptions.merge()
        ).await()
        db.collection("consultation_chat_messages").add(chatMessage.toFirestoreMap(includeCreatedAt = true)).await()
    }
}

private suspend fun ensureCustomerConsultationThread(
    db: FirebaseFirestore,
    threadId: String,
    user: com.google.firebase.auth.FirebaseUser,
    appointment: SpaAppointment?
) {
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val userName = user.displayName ?: user.email?.substringBefore("@") ?: "Kh\u00E1ch h\u00E0ng"
        val ref = db.collection("consultation_chat_threads").document(threadId)
        val exists = ref.get().await().exists()
        val base = mutableMapOf<String, Any>(
            "userId" to user.uid,
            "userEmail" to user.email.orEmpty(),
            "userName" to userName,
            "status" to ChatThreadStatus.ACTIVE,
            "updatedAt" to now
        )
        if (!exists) {
            base["consultantId"] = ""
            base["consultantEmail"] = ""
            base["consultantName"] = ""
        }
        appointment?.let {
            base["appointmentId"] = it.id
            if (it.consultantId.isNotBlank()) base["consultantId"] = it.consultantId
            if (it.consultantEmail.isNotBlank()) base["consultantEmail"] = it.consultantEmail
            if (it.consultantName.isNotBlank()) base["consultantName"] = it.consultantName
        }
        if (!exists) base["createdAt"] = now
        ref.set(base, SetOptions.merge()).await()
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
