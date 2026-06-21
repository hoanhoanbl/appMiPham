package com.example.appbanmypham.ui.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.ReturnRequest
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class ManageReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageReturnScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Status config ──────────────────────────────────────────────────────────
data class ReturnStatusInfo(
    val key: String, val label: String, val emoji: String,
    val color: Color, val bgColor: Color
)

val RETURN_STATUS_LIST = listOf(
    ReturnStatusInfo("pending",   "Đang chờ duyệt", "⏳", Color(0xFFE8A44A), Color(0xFFFFF3E0)),
    ReturnStatusInfo("approved",  "Đã chấp nhận",   "✅", Color(0xFF4A90D9), Color(0xFFE8F0FB)),
    ReturnStatusInfo("rejected",  "Đã từ chối",     "❌", Color(0xFFE57373), Color(0xFFFFECEC)),
    ReturnStatusInfo("completed", "Hoàn tất",       "🎉", MintGreen,         Color(0xFFEAF9F5))
)

fun getReturnStatus(key: String) = RETURN_STATUS_LIST.find { it.key == key } ?: RETURN_STATUS_LIST[0]

fun firestoreDocToReturnRequest(doc: DocumentSnapshot): ReturnRequest? {
    return runCatching {
        ReturnRequest(
            id        = doc.id,
            orderId   = doc.getString("orderId")   ?: "",
            userId    = doc.getString("userId")    ?: "",
            reason    = doc.getString("reason")    ?: "",
            note      = doc.getString("note")      ?: "",
            status    = doc.getString("status")    ?: "pending",
            adminNote = doc.getString("adminNote") ?: "",
            createdAt = doc.getLong("createdAt")   ?: 0L,
            updatedAt = doc.getLong("updatedAt")   ?: 0L
        )
    }.getOrNull()
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun ManageReturnScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }

    var requests      by remember { mutableStateOf(listOf<ReturnRequest>()) }
    var isLoading     by remember { mutableStateOf(true) }
    var filterStatus  by remember { mutableStateOf<String?>(null) }
    var rejectTarget  by remember { mutableStateOf<ReturnRequest?>(null) }
    var detailTarget  by remember { mutableStateOf<ReturnRequest?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackMsg) { snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null } }

    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("return_requests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { isLoading = false; return@addSnapshotListener }
                requests = snap?.documents?.mapNotNull { firestoreDocToReturnRequest(it) } ?: emptyList()
                isLoading = false
            }
        onDispose { reg?.remove() }
    }

    val filtered = requests.filter { filterStatus == null || it.status == filterStatus }

    fun updateStatus(req: ReturnRequest, newStatus: String, adminNote: String = "") {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.update(
            db.collection("return_requests").document(req.id),
            mapOf(
                "status"    to newStatus,
                "adminNote" to adminNote,
                "updatedAt" to now
            )
        )
        if (newStatus == "completed") {
            batch.update(
                db.collection("orders").document(req.orderId),
                mapOf(
                    "status" to "returned",
                    "updatedAt" to now
                )
            )
        }
        batch.commit()
            .addOnSuccessListener {
                val info = getReturnStatus(newStatus)
                snackMsg = "${info.emoji} Đã cập nhật: ${info.label}"
            }
            .addOnFailureListener { snackMsg = "❌ Cập nhật thất bại" }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost   = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(top = 48.dp)) {
                    Text("QUẢN LÝ", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Yêu cầu trả hàng", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${requests.size} yêu cầu", color = Color.White.copy(0.8f), fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundPrimary)
            ) {
                Spacer(Modifier.height(12.dp))

                // Filter chips
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ReturnStatChip(
                            label    = "Tất cả",
                            count    = requests.size,
                            color    = MintGreen,
                            bgColor  = Color(0xFFEAF9F5),
                            selected = filterStatus == null,
                            onClick  = { filterStatus = null }
                        )
                    }
                    items(RETURN_STATUS_LIST) { info ->
                        ReturnStatChip(
                            label    = info.label,
                            count    = requests.count { it.status == info.key },
                            color    = info.color,
                            bgColor  = info.bgColor,
                            selected = filterStatus == info.key,
                            onClick  = { filterStatus = if (filterStatus == info.key) null else info.key }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MintGreen)
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Không có yêu cầu nào", color = Color(0xFF8ACABA), fontSize = 15.sp)
                        }
                    }
                    else -> LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { req ->
                            ReturnRequestCard(
                                request    = req,
                                onViewDetail = { detailTarget = req },
                                onApprove  = { updateStatus(req, "approved") },
                                onReject   = { rejectTarget = req },
                                onComplete = { updateStatus(req, "completed") }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // Dialog từ chối — nhập lý do
    rejectTarget?.let { req ->
        RejectReturnDialog(
            request   = req,
            onDismiss = { rejectTarget = null },
            onConfirm = { noteInput ->
                updateStatus(req, "rejected", noteInput)
                rejectTarget = null
            }
        )
    }

    // Dialog chi tiết
    detailTarget?.let { req ->
        ReturnDetailDialog(request = req, onDismiss = { detailTarget = null })
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────────
@Composable
private fun ReturnStatChip(
    label: String, count: Int, color: Color, bgColor: Color,
    selected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color else bgColor)
            .border(1.dp, if (selected) color else color.copy(0.3f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = if (selected) Color.White else color, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape)
                    .background(if (selected) Color.White.copy(0.3f) else color.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$count", color = if (selected) Color.White else color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Card ───────────────────────────────────────────────────────────────────────
@Composable
private fun ReturnRequestCard(
    request      : ReturnRequest,
    onViewDetail : () -> Unit,
    onApprove    : () -> Unit,
    onReject     : () -> Unit,
    onComplete   : () -> Unit
) {
    val info = getReturnStatus(request.status)
    val dateStr = remember(request.createdAt) {
        if (request.createdAt > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(request.createdAt)) else "--"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Đơn #${request.orderId.take(8).uppercase()}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(dateStr, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(info.bgColor).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("${info.emoji} ${info.label}", color = info.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEAF9F5))
            Spacer(Modifier.height(10.dp))

            Text("Lý do: ${request.reason}", color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (request.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Ghi chú khách: ${request.note}", color = Color(0xFF8ACABA), fontSize = 12.sp)
            }
            if (request.adminNote.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Phản hồi admin: ${request.adminNote}", color = Color(0xFFE57373), fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onViewDetail,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape    = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chi tiết", fontSize = 12.sp)
                }

                when (request.status) {
                    "pending" -> {
                        Box(
                            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(brush = AppGradients.mintHorizontal)
                                .clickable { onApprove() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✅ Chấp nhận", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFECEC))
                                .border(1.dp, Color(0xFFE57373).copy(0.35f), RoundedCornerShape(10.dp))
                                .clickable { onReject() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("❌ Từ chối", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    "approved" -> {
                        Box(
                            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(brush = AppGradients.mintHorizontal)
                                .clickable { onComplete() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎉 Hoàn tất", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(info.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${info.emoji} ${info.label}", color = info.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog từ chối ─────────────────────────────────────────────────────────────
@Composable
private fun RejectReturnDialog(
    request   : ReturnRequest,
    onDismiss : () -> Unit,
    onConfirm : (String) -> Unit
) {
    var noteInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Từ chối yêu cầu trả hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                Text("Đơn #${request.orderId.take(8).uppercase()}", color = Color(0xFF8ACABA), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder   = { Text("Lý do từ chối...") },
                    modifier      = Modifier.fillMaxWidth().height(90.dp),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteInput) },
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                shape   = RoundedCornerShape(12.dp)
            ) { Text("Xác nhận từ chối", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = MintGreen) }
        }
    )
}

// ── Dialog chi tiết ────────────────────────────────────────────────────────────
@Composable
private fun ReturnDetailDialog(request: ReturnRequest, onDismiss: () -> Unit) {
    val info = getReturnStatus(request.status)
    val dateStr = remember(request.createdAt) {
        if (request.createdAt > 0) SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(request.createdAt)) else "--"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Chi tiết yêu cầu", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(info.bgColor).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("${info.emoji} ${info.label}", color = info.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text("Mã đơn: #${request.orderId.take(8).uppercase()}", color = Color(0xFF1A4A40), fontSize = 13.sp)
                Text("Thời gian gửi: $dateStr", color = Color(0xFF8ACABA), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Lý do: ${request.reason}", color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (request.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Ghi chú khách: ${request.note}", color = Color(0xFF8ACABA), fontSize = 12.sp)
                }
                if (request.adminNote.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Phản hồi admin: ${request.adminNote}", color = Color(0xFFE57373), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = MintGreen) }
        }
    )
}
