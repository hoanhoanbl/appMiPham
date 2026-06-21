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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
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
import com.example.appbanmypham.model.ChatSenderRole
import com.example.appbanmypham.model.ConsultationChatMessage
import com.example.appbanmypham.model.ConsultationChatThread
import com.example.appbanmypham.model.firestoreDocToConsultationChatMessage
import com.example.appbanmypham.model.firestoreDocToConsultationChatThread
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

class ConsultantChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val threadId = intent.getStringExtra("thread_id").orEmpty()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ConsultantChatScreen(threadId = threadId, onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ConsultantChatScreen(threadId: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var thread by remember { mutableStateOf<ConsultationChatThread?>(null) }
    var messages by remember { mutableStateOf(listOf<ConsultationChatMessage>()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(threadId.isNotBlank()) }
    var isSending by remember { mutableStateOf(false) }

    DisposableEffect(threadId) {
        if (threadId.isBlank()) {
            isLoading = false
            return@DisposableEffect onDispose {}
        }
        val reg = db.collection("consultation_chat_threads").document(threadId)
            .addSnapshotListener { snap, _ ->
                thread = snap?.takeIf { it.exists() }?.let { runCatching { firestoreDocToConsultationChatThread(it) }.getOrNull() }
                isLoading = false
            }
        onDispose { reg.remove() }
    }

    val currentThread = thread
    val canMessage = user != null && currentThread != null &&
            (currentThread.consultantId.isBlank() || currentThread.consultantId == user.uid)

    DisposableEffect(threadId, currentThread?.consultantId, user?.uid) {
        if (threadId.isBlank() || !canMessage) {
            messages = emptyList()
            return@DisposableEffect onDispose {}
        }
        val reg = db.collection("consultation_chat_messages")
            .whereEqualTo("threadId", threadId)
            .addSnapshotListener { snap, _ ->
                messages = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToConsultationChatMessage(it) }.getOrNull() }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ChatHeader(
                thread = currentThread,
                onBack = onBack,
                onOpenProfile = {
                    currentThread?.let {
                        context.startActivity(
                            Intent(context, ConsultantCustomerProfileActivity::class.java)
                                .putExtra("user_id", it.userId)
                                .putExtra("thread_id", it.id)
                        )
                    }
                }
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
                currentThread == null -> EmptyChat("Kh\u00F4ng t\u00ECm th\u1EA5y cu\u1ED9c tr\u00F2 chuy\u1EC7n.")
                !canMessage -> EmptyChat("B\u1EA1n kh\u00F4ng ph\u1EE5 tr\u00E1ch cu\u1ED9c tr\u00F2 chuy\u1EC7n n\u00E0y.")
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item { EmptyChat("Ch\u01B0a c\u00F3 tin nh\u1EAFn n\u00E0o.") }
                        } else {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message = message, mine = message.senderId == user?.uid)
                            }
                        }
                    }
                    Surface(color = Color.White, shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                enabled = canMessage && !isSending,
                                placeholder = { Text(if (canMessage) "Nh\u1EADp tin nh\u1EAFn..." else "Ch\u1EC9 t\u01B0 v\u1EA5n vi\u00EAn ph\u1EE5 tr\u00E1ch m\u1EDBi \u0111\u01B0\u1EE3c chat") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = chatFieldColors()
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val text = messageText.trim()
                                    val signedUser = user ?: return@IconButton
                                    val ownedThread = currentThread ?: return@IconButton
                                    if (text.isBlank()) return@IconButton
                                    scope.launch {
                                        isSending = true
                                        val result = runCatching {
                                            sendConsultantChatMessage(
                                                db = db,
                                                thread = ownedThread,
                                                senderId = signedUser.uid,
                                                senderName = signedUser.displayName ?: signedUser.email?.substringBefore("@") ?: "T\u01B0 v\u1EA5n vi\u00EAn",
                                                message = text
                                            )
                                        }
                                        if (result.isSuccess) messageText = ""
                                        snackbarHostState.showSnackbar(if (result.isSuccess) "\u0110\u00E3 g\u1EEDi" else result.exceptionOrNull()?.message ?: "G\u1EEDi tin th\u1EA5t b\u1EA1i")
                                        isSending = false
                                    }
                                },
                                enabled = canMessage && messageText.isNotBlank() && !isSending,
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(MintGreen)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    thread: ConsultationChatThread?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(38.dp).clip(CircleShape).background(Color.White.copy(0.23f))) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 46.dp)
                .clickable(enabled = thread != null) { onOpenProfile() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thread?.userName?.ifBlank { thread.userEmail } ?: "Kh\u00E1ch h\u00E0ng",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val context = when {
                    thread?.treatmentPlanId?.isNotBlank() == true -> "H\u1ED3 s\u01A1 li\u1EC7u tr\u00ECnh"
                    thread?.appointmentId?.isNotBlank() == true -> "H\u1ED3 s\u01A1 l\u1ECBch h\u1EB9n"
                    else -> "Ch\u1EA1m \u0111\u1EC3 xem h\u1ED3 s\u01A1"
                }
                Text(context, color = Color.White.copy(0.76f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ConsultationChatMessage, mine: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp))
                .background(if (mine) Color(0xFFEAF9F5) else Color.White)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(message.senderName.ifBlank { message.senderRole }, color = if (mine) MintGreen else Color(0xFF8ACABA), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(message.message, color = Color(0xFF1A4A40), fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun EmptyChat(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color(0xFF6C8F87), fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

private suspend fun sendConsultantChatMessage(
    db: FirebaseFirestore,
    thread: ConsultationChatThread,
    senderId: String,
    senderName: String,
    message: String
) {
    withContext(Dispatchers.IO) {
        if (thread.consultantId.isNotBlank() && thread.consultantId != senderId) {
            throw IllegalStateException("B\u1EA1n kh\u00F4ng ph\u1EE5 tr\u00E1ch cu\u1ED9c tr\u00F2 chuy\u1EC7n n\u00E0y")
        }
        val now = System.currentTimeMillis()
        val consultantEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        val chatMessage = ConsultationChatMessage(
            threadId = thread.id,
            appointmentId = thread.appointmentId,
            treatmentPlanId = thread.treatmentPlanId,
            userId = thread.userId,
            consultantId = thread.consultantId.ifBlank { senderId },
            senderId = senderId,
            senderName = senderName,
            senderRole = ChatSenderRole.CONSULTANT,
            message = message,
            createdAt = now
        )
        db.collection("consultation_chat_messages").add(chatMessage.toFirestoreMap(includeCreatedAt = true)).await()
        db.collection("consultation_chat_threads").document(thread.id).set(
            mapOf(
                "consultantId" to thread.consultantId.ifBlank { senderId },
                "consultantEmail" to thread.consultantEmail.ifBlank { consultantEmail },
                "consultantName" to thread.consultantName.ifBlank { senderName },
                "lastMessage" to message,
                "lastMessageAt" to now,
                "updatedAt" to now
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }
}

@Composable
private fun chatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
