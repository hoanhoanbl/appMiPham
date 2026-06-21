package com.example.appbanmypham.ui.admin

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.DEFAULT_SPA_CAPACITY_SETTINGS_ID
import com.example.appbanmypham.model.SPA_CAPACITY_OVERRIDES_COLLECTION
import com.example.appbanmypham.model.SPA_CAPACITY_SETTINGS_COLLECTION
import com.example.appbanmypham.model.SpaCapacityOverride
import com.example.appbanmypham.model.SpaCapacitySettings
import com.example.appbanmypham.model.SpaWorkingWindow
import com.example.appbanmypham.model.firestoreDocToSpaCapacityOverride
import com.example.appbanmypham.model.firestoreDocToSpaCapacitySettings
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class ManageSpaCapacityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageSpaCapacityScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ManageSpaCapacityScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(SpaCapacitySettings()) }
    var overrides by remember { mutableStateOf(listOf<SpaCapacityOverride>()) }

    var concurrentBookings by remember { mutableStateOf(settings.defaultConcurrentBookings.toString()) }
    var slotMinutes by remember { mutableStateOf(settings.slotMinutes.toString()) }
    var morningStart by remember { mutableStateOf("09:00") }
    var morningEnd by remember { mutableStateOf("12:00") }
    var afternoonStart by remember { mutableStateOf("13:30") }
    var afternoonEnd by remember { mutableStateOf("17:00") }
    var bookingHorizon by remember { mutableStateOf(settings.bookingHorizonDays.toString()) }
    var noShowGrace by remember { mutableStateOf(settings.noShowGraceMinutes.toString()) }
    var closedWeekdays by remember { mutableStateOf(settings.closedWeekdays.toSet()) }

    var overrideDateKey by remember { mutableStateOf("") }
    var overrideCapacity by remember { mutableStateOf("") }
    var overrideClosed by remember { mutableStateOf(false) }
    var overrideNote by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val loaded = runCatching {
            val doc = db.collection(SPA_CAPACITY_SETTINGS_COLLECTION)
                .document(DEFAULT_SPA_CAPACITY_SETTINGS_ID)
                .get()
                .await()
            if (doc.exists()) firestoreDocToSpaCapacitySettings(doc) else SpaCapacitySettings()
        }.getOrDefault(SpaCapacitySettings())
        settings = loaded
        concurrentBookings = loaded.defaultConcurrentBookings.toString()
        slotMinutes = loaded.slotMinutes.toString()
        morningStart = loaded.workingWindows.getOrNull(0)?.start ?: "09:00"
        morningEnd = loaded.workingWindows.getOrNull(0)?.end ?: "12:00"
        afternoonStart = loaded.workingWindows.getOrNull(1)?.start ?: "13:30"
        afternoonEnd = loaded.workingWindows.getOrNull(1)?.end ?: "17:00"
        bookingHorizon = loaded.bookingHorizonDays.toString()
        noShowGrace = loaded.noShowGraceMinutes.toString()
        closedWeekdays = loaded.closedWeekdays.toSet()
        isLoading = false
    }

    DisposableEffect(Unit) {
        val reg = db.collection(SPA_CAPACITY_OVERRIDES_COLLECTION)
            .addSnapshotListener { snap, _ ->
                overrides = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaCapacityOverride(it) }.getOrNull() }
                    ?.sortedBy { it.dateKey }
                    ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    Scaffold(containerColor = BackgroundPrimary, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack = onBack)
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CapacityCard(title = "Cấu hình chung", icon = Icons.Default.Settings) {
                            NumberField("Số khách phục vụ cùng lúc", concurrentBookings) { concurrentBookings = it }
                            NumberField("Mỗi khung cách nhau bao nhiêu phút", slotMinutes) { slotMinutes = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                PlainField("Sáng từ", morningStart, Modifier.weight(1f)) { morningStart = it }
                                PlainField("Đến", morningEnd, Modifier.weight(1f)) { morningEnd = it }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                PlainField("Chiều từ", afternoonStart, Modifier.weight(1f)) { afternoonStart = it }
                                PlainField("Đến", afternoonEnd, Modifier.weight(1f)) { afternoonEnd = it }
                            }
                            NumberField("Khách được đặt trước tối đa bao nhiêu ngày", bookingHorizon) { bookingHorizon = it }
                            NumberField("Chờ khách trễ bao nhiêu phút", noShowGrace) { noShowGrace = it }
                            Text("Ngày spa nghỉ trong tuần", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            WeekdaySelector(closedWeekdays = closedWeekdays, onToggle = { day ->
                                closedWeekdays = if (day in closedWeekdays) closedWeekdays - day else closedWeekdays + day
                            })
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        val next = SpaCapacitySettings(
                                            defaultConcurrentBookings = concurrentBookings.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                                            slotMinutes = slotMinutes.toIntOrNull()?.coerceIn(15, 120) ?: 30,
                                            workingWindows = listOf(
                                                SpaWorkingWindow(morningStart.trim(), morningEnd.trim()),
                                                SpaWorkingWindow(afternoonStart.trim(), afternoonEnd.trim())
                                            ),
                                            closedWeekdays = closedWeekdays.sorted(),
                                            bookingHorizonDays = bookingHorizon.toIntOrNull()?.coerceIn(1, 120) ?: 31,
                                            noShowGraceMinutes = noShowGrace.toIntOrNull()?.coerceIn(0, 240) ?: 15,
                                            updatedBy = user?.uid.orEmpty()
                                        )
                                        val result = runCatching {
                                            db.collection(SPA_CAPACITY_SETTINGS_COLLECTION)
                                                .document(DEFAULT_SPA_CAPACITY_SETTINGS_ID)
                                                .set(next.toFirestoreMap(includeCreatedAt = true))
                                                .await()
                                        }
                                        isSaving = false
                                        snackbarHostState.showSnackbar(if (result.isSuccess) "Đã lưu cấu hình sức chứa" else "Lưu thất bại")
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lưu cấu hình", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        CapacityCard(title = "Điều chỉnh theo ngày", icon = Icons.Default.EventAvailable) {
                            PlainField("Ngày cần chỉnh (yyyy-MM-dd)", overrideDateKey, Modifier.fillMaxWidth()) { overrideDateKey = it }
                            NumberField("Sức chứa riêng (0 = dùng cấu hình chung)", overrideCapacity) { overrideCapacity = it }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = overrideClosed,
                                    onCheckedChange = { overrideClosed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = MintGreen)
                                )
                                Text("Ngưng nhận đặt lịch ngày này", color = Color(0xFF1A4A40), fontSize = 13.sp)
                            }
                            PlainField("Ghi chú cho nhân viên", overrideNote, Modifier.fillMaxWidth()) { overrideNote = it }
                            Button(
                                onClick = {
                                    val key = overrideDateKey.trim()
                                    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(key)) {
                                        scope.launch { snackbarHostState.showSnackbar("Nhập ngày theo dạng yyyy-MM-dd") }
                                        return@Button
                                    }
                                    scope.launch {
                                        val override = SpaCapacityOverride(
                                            id = key,
                                            dateKey = key,
                                            dateLabel = key,
                                            concurrentBookings = overrideCapacity.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                            closed = overrideClosed,
                                            note = overrideNote.trim(),
                                            updatedBy = user?.uid.orEmpty()
                                        )
                                        val result = runCatching {
                                            db.collection(SPA_CAPACITY_OVERRIDES_COLLECTION)
                                                .document(key)
                                                .set(override.toFirestoreMap(includeCreatedAt = true))
                                                .await()
                                        }
                                        snackbarHostState.showSnackbar(if (result.isSuccess) "Đã lưu điều chỉnh ngày" else "Lưu điều chỉnh thất bại")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90D9)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lưu điều chỉnh")
                            }
                        }
                    }

                    item {
                        Text("Ngày có điều chỉnh riêng", color = Color(0xFF1A4A40), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (overrides.isEmpty()) {
                        item {
                            Text("Chưa có ngày nào được điều chỉnh riêng.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                        }
                    } else {
                        items(overrides, key = { it.id.ifBlank { it.dateKey } }) { item ->
                            OverrideRow(
                                item = item,
                                onEdit = {
                                    overrideDateKey = item.dateKey
                                    overrideCapacity = item.concurrentBookings.toString()
                                    overrideClosed = item.closed
                                    overrideNote = item.note
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sức chứa spa", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Quản lý số khách spa có thể phục vụ cùng lúc", color = Color.White.copy(0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun CapacityCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MintGreen)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            content()
        }
    }
}

@Composable
private fun PlainField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = capacityFieldColors()
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = capacityFieldColors()
    )
}

@Composable
private fun WeekdaySelector(closedWeekdays: Set<Int>, onToggle: (Int) -> Unit) {
    val days = listOf(
        Calendar.MONDAY to "T2",
        Calendar.TUESDAY to "T3",
        Calendar.WEDNESDAY to "T4",
        Calendar.THURSDAY to "T5",
        Calendar.FRIDAY to "T6",
        Calendar.SATURDAY to "T7",
        Calendar.SUNDAY to "CN"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { (day, label) ->
            val selected = day in closedWeekdays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(0xFFFFECEC) else Color(0xFFEAF9F5))
                    .clickable { onToggle(day) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color(0xFFE57373) else MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OverrideRow(item: SpaCapacityOverride, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.closed) Icons.Default.Block else Icons.Default.Spa, contentDescription = null, tint = if (item.closed) Color(0xFFE57373) else MintGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.dateKey, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold)
                Text(
                    if (item.closed) "Ngưng nhận đặt lịch" else "Sức chứa: ${item.concurrentBookings.takeIf { it > 0 } ?: "dùng cấu hình chung"}",
                    color = Color(0xFF6C8F87),
                    fontSize = 12.sp
                )
                if (item.note.isNotBlank()) Text(item.note, color = Color(0xFF8ACABA), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun capacityFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
