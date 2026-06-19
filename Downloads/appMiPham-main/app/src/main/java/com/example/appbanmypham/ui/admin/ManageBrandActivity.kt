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
import androidx.compose.ui.window.Dialog
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore

class ManageBrandActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageBrandScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Model ─────────────────────────────────────────────────────────────────────
data class BrandData(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val origin: String = "",
    val productCount: Int = 0
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun ManageBrandScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var brands    by remember { mutableStateOf(listOf<BrandData>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<BrandData?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Realtime listener
    LaunchedEffect(Unit) {
        db.collection("brands").addSnapshotListener { snap, _ ->
            brands = snap?.documents?.map { doc ->
                BrandData(
                    id           = doc.id,
                    name         = doc.getString("name") ?: "",
                    description  = doc.getString("description") ?: "",
                    origin       = doc.getString("origin") ?: "",
                    productCount = (doc.getLong("productCount") ?: 0L).toInt()
                )
            } ?: emptyList()
            isLoading = false
        }
    }

    val filtered = brands.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.origin.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(brush = AppGradients.mintHorizontal)
                    .clickable { editTarget = null; showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(brush = AppGradients.mintHorizontal)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }

                // Stats badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("${brands.size} thương hiệu", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text("QUẢN LÝ", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Thương hiệu", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Search bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundPrimary)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    placeholder = { Text("Tìm thương hiệu...", color = Color(0xFFAAD8CE)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFFAAD8CE))
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor = Color(0xFF1A4A40),
                        unfocusedTextColor = Color(0xFF1A4A40),
                        cursorColor = MintGreen
                    )
                )
            }

            // ── Content ──
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
            } else if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isEmpty()) "Chưa có thương hiệu nào"
                            else "Không tìm thấy kết quả",
                            color = Color(0xFF8ACABA), fontSize = 15.sp
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Nhấn + để thêm thương hiệu mới", color = Color(0xFFAAD8CE), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { brand ->
                        BrandCard(
                            brand = brand,
                            onEdit = { editTarget = it; showDialog = true },
                            onDelete = {
                                db.collection("brands").document(it.id).delete()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showDialog) {
        BrandDialog(
            existing = editTarget,
            onDismiss = { showDialog = false },
            onSave = { brand ->
                val data = hashMapOf(
                    "name"        to brand.name,
                    "description" to brand.description,
                    "origin"      to brand.origin,
                    "updatedAt"   to System.currentTimeMillis()
                )
                if (brand.id.isEmpty()) {
                    data["createdAt"] = System.currentTimeMillis()
                    data["productCount"] = 0L
                    db.collection("brands").add(data)
                } else {
                    db.collection("brands").document(brand.id).update(data as Map<String, Any>)
                }
                showDialog = false
            }
        )
    }
}

// ── Brand Card ────────────────────────────────────────────────────────────────
@Composable
private fun BrandCard(
    brand: BrandData,
    onEdit: (BrandData) -> Unit,
    onDelete: (BrandData) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    // Generate avatar color from brand name
    val avatarColors = listOf(
        Color(0xFFEAF9F5) to MintGreen,
        Color(0xFFF0F4FF) to Color(0xFF6B89CC),
        Color(0xFFFFF3E0) to Color(0xFFE8A44A),
        Color(0xFFFCE4EC) to Color(0xFFE97B9A),
        Color(0xFFE8F5E9) to Color(0xFF5CAD6D)
    )
    val colorPair = avatarColors[brand.name.length % avatarColors.size]
    val initials = brand.name.take(2).uppercase()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorPair.first)
                    .border(1.5.dp, colorPair.second.copy(0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = colorPair.second, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(brand.name, color = Color(0xFF1A4A40), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (brand.origin.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(brand.origin, color = Color(0xFFAAD8CE), fontSize = 12.sp)
                    }
                }
                if (brand.description.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(brand.description, color = Color(0xFF8ACABA), fontSize = 11.sp, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEAF9F5))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("${brand.productCount} sản phẩm", color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onEdit(brand) },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF9F5))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF0F0))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = Color.White,
            title = { Text("Xoá thương hiệu", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text = { Text("Xoá \"${brand.name}\" sẽ không ảnh hưởng sản phẩm đã gán. Tiếp tục?", color = Color(0xFF4A7A70)) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete(brand) }) {
                    Text("Xoá", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Huỷ", color = MintGreen) }
            }
        )
    }
}

// ── Brand Dialog ──────────────────────────────────────────────────────────────
@Composable
private fun BrandDialog(
    existing: BrandData?,
    onDismiss: () -> Unit,
    onSave: (BrandData) -> Unit
) {
    var name        by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var origin      by remember { mutableStateOf(existing?.origin ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush = AppGradients.mintHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (existing == null) Icons.Default.Add else Icons.Default.Edit,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (existing == null) "Thêm thương hiệu" else "Sửa thương hiệu",
                        color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 17.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                BrandFieldLabel("Tên thương hiệu *")
                BrandTextField(value = name, onValueChange = { name = it }, placeholder = "VD: L'Oréal, The Body Shop...")

                Spacer(Modifier.height(10.dp))

                BrandFieldLabel("Xuất xứ")
                BrandTextField(value = origin, onValueChange = { origin = it }, placeholder = "VD: Pháp, Mỹ, Hàn Quốc...")

                Spacer(Modifier.height(10.dp))

                BrandFieldLabel("Mô tả")
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    placeholder = { Text("Mô tả về thương hiệu...", color = Color(0xFFAAD8CE)) },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor = Color(0xFF1A4A40),
                        unfocusedTextColor = Color(0xFF1A4A40),
                        cursorColor = MintGreen
                    )
                )

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) { Text("Huỷ") }

                    val isValid = name.isNotBlank()
                    Box(
                        modifier = Modifier
                            .weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = if (isValid) AppGradients.mintHorizontal
                                else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0xFFB2E8DA), Color(0xFFD0F2EC))
                                )
                            )
                            .clickable(enabled = isValid) {
                                onSave(BrandData(
                                    id          = existing?.id ?: "",
                                    name        = name.trim(),
                                    description = description.trim(),
                                    origin      = origin.trim(),
                                    productCount = existing?.productCount ?: 0
                                ))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lưu", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandFieldLabel(text: String) {
    Text(
        text = text,
        color = MintGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun BrandTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Color(0xFFAAD8CE)) },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MintGreen,
            unfocusedBorderColor = Color(0xFFB2E8DA),
            focusedTextColor = Color(0xFF1A4A40),
            unfocusedTextColor = Color(0xFF1A4A40),
            cursorColor = MintGreen
        )
    )
}