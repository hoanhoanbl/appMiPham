package com.example.appbanmypham.ui.admin

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.appbanmypham.data.CloudinaryHelper
import com.example.appbanmypham.model.PROGRESS_PHOTO_POLICIES
import com.example.appbanmypham.model.ProgressPhotoPolicy
import com.example.appbanmypham.model.SPA_PACKAGE_TYPES
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.SpaPackageType
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.spaPackageTypeMeta
import com.example.appbanmypham.model.toFirestoreMap
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageSpaPackageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageSpaPackageScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ManageSpaPackageScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var packages by remember { mutableStateOf(listOf<SpaPackage>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SpaPackage?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("spa_packages").addSnapshotListener { snap, err ->
            if (err != null) {
                isLoading = false
                snackMsg = "Khong the tai goi spa"
                return@addSnapshotListener
            }
            packages = snap?.documents
                ?.mapNotNull { runCatching { firestoreDocToSpaPackage(it) }.getOrNull() }
                ?.sortedWith(compareBy<SpaPackage> { it.sortOrder }.thenByDescending { it.createdAt })
                ?: emptyList()
            isLoading = false
        }
        onDispose { reg?.remove() }
    }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    val filtered = packages.filter {
        searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(brush = AppGradients.mintHorizontal)
                    .clickable { editTarget = null; showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Them", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("${packages.size} goi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text("QUAN LY", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Goi Spa", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${packages.count { it.isActive }} dang hien thi", color = Color.White.copy(0.75f), fontSize = 13.sp)
                }
            }

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
                    placeholder = { Text("Tim ten goi hoac danh muc...", color = Color(0xFFAAD8CE)) },
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
                    colors = spaTextFieldColors()
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
                filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SPA", color = MintGreen, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isBlank()) "Chua co goi spa nao" else "Khong tim thay ket qua",
                            color = Color(0xFF8ACABA),
                            fontSize = 15.sp
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Nhan + de them goi spa moi", color = Color(0xFFAAD8CE), fontSize = 12.sp)
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        SpaPackageAdminCard(
                            item = item,
                            onEdit = { editTarget = it; showDialog = true },
                            onToggleActive = {
                                db.collection("spa_packages").document(it.id)
                                    .update(mapOf("isActive" to !it.isActive, "updatedAt" to System.currentTimeMillis()))
                                    .addOnSuccessListener { snackMsg = "Da cap nhat hien thi" }
                                    .addOnFailureListener { snackMsg = "Cap nhat that bai" }
                            },
                            onDelete = {
                                scope.launch {
                                    runCatching {
                                        it.imageUrl.takeIf { url -> url.isNotEmpty() }?.let { url ->
                                            CloudinaryHelper.getPublicIdFromUrl(url)?.let { pid ->
                                                CloudinaryHelper.deleteImage(pid)
                                            }
                                        }
                                        db.collection("spa_packages").document(it.id).delete().await()
                                    }.onSuccess {
                                        snackMsg = "Da xoa goi spa"
                                    }.onFailure {
                                        snackMsg = "Xoa that bai: ${it.message}"
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showDialog) {
        SpaPackageDialog(
            existing = editTarget,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showDialog = false },
            onSave = { item, imageUri ->
                scope.launch {
                    isSaving = true
                    runCatching {
                        saveSpaPackage(db, item, imageUri, context)
                    }.onSuccess {
                        showDialog = false
                        snackMsg = "Da luu goi spa"
                    }.onFailure {
                        snackMsg = "Luu that bai: ${it.message}"
                    }
                    isSaving = false
                }
            }
        )
    }
}

@Composable
private fun SpaPackageAdminCard(
    item: SpaPackage,
    onEdit: (SpaPackage) -> Unit,
    onToggleActive: (SpaPackage) -> Unit,
    onDelete: (SpaPackage) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.isActive) Color.White else Color(0xFFFAFAFA)),
        elevation = CardDefaults.cardElevation(if (item.isActive) 2.dp else 0.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        color = if (item.isActive) Color(0xFF1A4A40) else Color(0xFFAAAAAA),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (item.isActive) Color(0xFFEAF9F5) else Color(0xFFEEEEEE))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (item.isActive) "Hien" else "An",
                            color = if (item.isActive) MintGreen else Color(0xFF9E9E9E),
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(item.category.ifBlank { "Chua phan loai" }, color = MintGreen, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (item.packageType == SpaPackageType.TREATMENT_TEMPLATE) Color(0xFFFFF7E8) else Color(0xFFEAF9F5))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            spaPackageTypeMeta(item.packageType).label,
                            color = if (item.packageType == SpaPackageType.TREATMENT_TEMPLATE) Color(0xFFB77816) else MintGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (item.packageType == SpaPackageType.TREATMENT_TEMPLATE) {
                        Text("${item.sessionCount} buoi", color = Color(0xFF6C8F87), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${"%,.0f".format(item.price)}d", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEAF9F5))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${item.durationMinutes} phut", color = MintGreen, fontSize = 10.sp)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onToggleActive(item) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (item.isActive) Color(0xFFFFF0F0) else Color(0xFFEAF9F5))
                ) {
                    Icon(
                        if (item.isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (item.isActive) Color(0xFFE57373) else MintGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onEdit(item) },
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
            title = { Text("Xoa goi spa", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text = { Text("Ban co chac muon xoa \"${item.name}\"?", color = Color(0xFF4A7A70)) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete(item) }) {
                    Text("Xoa", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Huy", color = MintGreen) }
            }
        )
    }
}

@Composable
private fun SpaPackageDialog(
    existing: SpaPackage?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SpaPackage, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var packageType by remember { mutableStateOf(existing?.packageType ?: SpaPackageType.SINGLE_SESSION) }
    var price by remember { mutableStateOf(existing?.price?.takeIf { it > 0.0 }?.toLong()?.toString() ?: "") }
    var originalPrice by remember { mutableStateOf(existing?.originalPrice?.takeIf { it > 0.0 }?.toLong()?.toString() ?: "") }
    var duration by remember { mutableStateOf(existing?.durationMinutes?.takeIf { it > 0 }?.toString() ?: "") }
    var sessionCount by remember { mutableStateOf(existing?.sessionCount?.takeIf { it > 1 }?.toString() ?: "") }
    var durationPerSession by remember {
        mutableStateOf(
            existing?.durationPerSessionMinutes?.takeIf { it > 0 }?.toString()
                ?: existing?.durationMinutes?.takeIf { it > 0 }?.toString()
                ?: ""
        )
    }
    var suggestedIntervalDays by remember { mutableStateOf(existing?.suggestedIntervalDays?.takeIf { it > 0 }?.toString() ?: "7") }
    var requiresProgressPhotos by remember { mutableStateOf(existing?.requiresProgressPhotos ?: false) }
    var photoPolicy by remember { mutableStateOf(existing?.photoPolicy ?: ProgressPhotoPolicy.NONE) }
    var photoGuide by remember { mutableStateOf(existing?.photoGuide ?: "") }
    var sortOrder by remember { mutableStateOf(existing?.sortOrder?.toString() ?: "0") }
    var shortDescription by remember { mutableStateOf(existing?.shortDescription ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var benefits by remember { mutableStateOf(existing?.benefits?.joinToString("\n") ?: "") }
    var steps by remember { mutableStateOf(existing?.steps?.joinToString("\n") ?: "") }
    var suitableFor by remember { mutableStateOf(existing?.suitableFor?.joinToString("\n") ?: "") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it }
    }
    val parsedPrice = price.toDoubleOrNull()
    val parsedDuration = duration.toIntOrNull()
    val parsedSessionCount = sessionCount.toIntOrNull()
    val parsedDurationPerSession = durationPerSession.toIntOrNull()
    val parsedSuggestedInterval = suggestedIntervalDays.toIntOrNull()
    val isTreatmentTemplate = packageType == SpaPackageType.TREATMENT_TEMPLATE
    val isValid = name.isNotBlank() &&
            category.isNotBlank() &&
            description.isNotBlank() &&
            parsedPrice != null &&
            parsedPrice > 0.0 &&
            parsedDuration != null &&
            parsedDuration > 0 &&
            (!isTreatmentTemplate || (
                    parsedSessionCount != null &&
                            parsedSessionCount > 1 &&
                            parsedDurationPerSession != null &&
                            parsedDurationPerSession > 0 &&
                            parsedSuggestedInterval != null &&
                            parsedSuggestedInterval >= 0
                    )) &&
            (!requiresProgressPhotos || photoPolicy != ProgressPhotoPolicy.NONE)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(brush = AppGradients.mintHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (existing == null) Icons.Default.Add else Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (existing == null) "Them goi spa" else "Sua goi spa", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEAF9F5))
                        .border(1.dp, Color(0xFFB2E8DA), RoundedCornerShape(14.dp))
                        .clickable(enabled = !isSaving) { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageUri != null -> AsyncImage(model = imageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        existing?.imageUrl?.isNotEmpty() == true -> AsyncImage(model = existing.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Chon anh goi spa", color = MintGreen, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SpaDialogLabel("Ten goi *")
                SpaDialogTextField(name, { name = it }, "VD: Cham soc da chuyen sau")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Danh muc *")
                SpaDialogTextField(category, { category = it }, "VD: Cham soc da, Massage")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Loai goi *")
                SpaChoiceRow(
                    options = SPA_PACKAGE_TYPES.map { it.key to it.label },
                    selected = packageType,
                    onSelected = {
                        packageType = it
                        if (it == SpaPackageType.SINGLE_SESSION) {
                            requiresProgressPhotos = false
                            photoPolicy = ProgressPhotoPolicy.NONE
                        } else if (durationPerSession.isBlank()) {
                            durationPerSession = duration
                        }
                    }
                )
                Text(
                    spaPackageTypeMeta(packageType).description,
                    color = Color(0xFF6C8F87),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        SpaDialogLabel(if (isTreatmentTemplate) "Tong gia lieu trinh *" else "Gia *")
                        SpaDialogTextField(price, { price = it }, "0", KeyboardType.Number)
                    }
                    Column(Modifier.weight(1f)) {
                        SpaDialogLabel("Gia goc")
                        SpaDialogTextField(originalPrice, { originalPrice = it }, "0", KeyboardType.Number)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        SpaDialogLabel(if (isTreatmentTemplate) "Thoi luong lich dau *" else "Thoi luong *")
                        SpaDialogTextField(duration, { duration = it }, "60", KeyboardType.Number)
                    }
                    Column(Modifier.weight(1f)) {
                        SpaDialogLabel("Thu tu")
                        SpaDialogTextField(sortOrder, { sortOrder = it }, "0", KeyboardType.Number)
                    }
                }
                if (isTreatmentTemplate) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            SpaDialogLabel("So buoi *")
                            SpaDialogTextField(sessionCount, { sessionCount = it }, "3", KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            SpaDialogLabel("Phut / buoi *")
                            SpaDialogTextField(durationPerSession, { durationPerSession = it }, "60", KeyboardType.Number)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SpaDialogLabel("Khoang cach goi y giua cac buoi")
                    SpaDialogTextField(suggestedIntervalDays, { suggestedIntervalDays = it }, "7", KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FFFE))
                            .clickable(enabled = !isSaving) {
                                requiresProgressPhotos = !requiresProgressPhotos
                                if (!requiresProgressPhotos) photoPolicy = ProgressPhotoPolicy.NONE
                                if (requiresProgressPhotos && photoPolicy == ProgressPhotoPolicy.NONE) {
                                    photoPolicy = ProgressPhotoPolicy.AFTER_EACH_SESSION
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = requiresProgressPhotos,
                            onCheckedChange = {
                                requiresProgressPhotos = it
                                photoPolicy = if (it) ProgressPhotoPolicy.AFTER_EACH_SESSION else ProgressPhotoPolicy.NONE
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MintGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Can anh tien trinh", color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Dung cho lieu trinh can so sanh truoc/sau", color = Color(0xFF6C8F87), fontSize = 11.sp)
                        }
                    }
                    if (requiresProgressPhotos) {
                        Spacer(Modifier.height(10.dp))
                        SpaDialogLabel("Quy tac anh *")
                        SpaChoiceRow(
                            options = PROGRESS_PHOTO_POLICIES
                                .filter { it.key != ProgressPhotoPolicy.NONE }
                                .map { it.key to it.label },
                            selected = photoPolicy,
                            onSelected = { photoPolicy = it }
                        )
                        Text(
                            progressPhotoPolicyMeta(photoPolicy).description,
                            color = Color(0xFF6C8F87),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        SpaDialogLabel("Huong dan chup anh")
                        SpaDialogMultiline(photoGuide, { photoGuide = it }, "VD: Chup mat truoc anh sang tu nhien, cung goc chup moi buoi")
                    }
                }
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Mo ta ngan")
                SpaDialogTextField(shortDescription, { shortDescription = it }, "Tom tat hien tren card")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Mo ta chi tiet *")
                SpaDialogMultiline(description, { description = it }, "Noi dung chi tiet ve goi spa")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Loi ich (moi dong mot y)")
                SpaDialogMultiline(benefits, { benefits = it }, "Lam sach sau\nCap am")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Quy trinh (moi dong mot buoc)")
                SpaDialogMultiline(steps, { steps = it }, "Tu van\nLam sach\nCham soc")
                Spacer(Modifier.height(10.dp))
                SpaDialogLabel("Phu hop voi (moi dong mot y)")
                SpaDialogMultiline(suitableFor, { suitableFor = it }, "Da kho\nDa thieu suc song")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FFFE))
                        .clickable(enabled = !isSaving) { isActive = !isActive }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isActive, onCheckedChange = { isActive = it }, colors = CheckboxDefaults.colors(checkedColor = MintGreen))
                    Spacer(Modifier.width(8.dp))
                    Text("Hien thi cho khach hang", color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) { Text("Huy") }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = if (isValid && !isSaving) AppGradients.mintHorizontal
                                else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFB2E8DA), Color(0xFFD0F2EC)))
                            )
                            .clickable(enabled = isValid && !isSaving) {
                                onSave(
                                    SpaPackage(
                                        id = existing?.id ?: "",
                                        name = name.trim(),
                                        shortDescription = shortDescription.trim(),
                                        description = description.trim(),
                                        category = category.trim(),
                                        packageType = packageType,
                                        price = price.toDoubleOrNull() ?: 0.0,
                                        originalPrice = originalPrice.toDoubleOrNull() ?: 0.0,
                                        durationMinutes = duration.toIntOrNull() ?: 0,
                                        sessionCount = if (isTreatmentTemplate) (sessionCount.toIntOrNull() ?: 1) else 1,
                                        durationPerSessionMinutes = if (isTreatmentTemplate) {
                                            durationPerSession.toIntOrNull() ?: duration.toIntOrNull() ?: 0
                                        } else {
                                            duration.toIntOrNull() ?: 0
                                        },
                                        suggestedIntervalDays = if (isTreatmentTemplate) suggestedIntervalDays.toIntOrNull() ?: 7 else 0,
                                        requiresProgressPhotos = isTreatmentTemplate && requiresProgressPhotos,
                                        photoPolicy = if (isTreatmentTemplate && requiresProgressPhotos) photoPolicy else ProgressPhotoPolicy.NONE,
                                        photoGuide = if (isTreatmentTemplate && requiresProgressPhotos) photoGuide.trim() else "",
                                        imageUrl = existing?.imageUrl ?: "",
                                        benefits = linesToList(benefits),
                                        steps = linesToList(steps),
                                        suitableFor = linesToList(suitableFor),
                                        isActive = isActive,
                                        sortOrder = sortOrder.toIntOrNull() ?: 0,
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    ),
                                    imageUri
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Luu", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveSpaPackage(
    db: FirebaseFirestore,
    item: SpaPackage,
    imageUri: Uri?,
    context: android.content.Context
) {
    var finalImageUrl = item.imageUrl
    if (imageUri != null) {
        if (item.imageUrl.isNotEmpty()) {
            CloudinaryHelper.getPublicIdFromUrl(item.imageUrl)?.let { CloudinaryHelper.deleteImage(it) }
        }
        finalImageUrl = CloudinaryHelper.uploadImage(context, imageUri)
    }

    if (item.id.isBlank()) {
        db.collection("spa_packages").add(item.toFirestoreMap(finalImageUrl, includeCreatedAt = true)).await()
    } else {
        db.collection("spa_packages").document(item.id)
            .update(item.toFirestoreMap(finalImageUrl, includeCreatedAt = false) as Map<String, Any>)
            .await()
    }
}

private fun linesToList(value: String): List<String> =
    value.lines().map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun SpaChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            val isSelected = selected == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFEAF9F5) else Color(0xFFF8FFFE))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MintGreen else Color(0xFFB2E8DA),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelected(key) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) MintGreen else Color(0xFF6C8F87),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpaDialogLabel(text: String) {
    Text(text = text, color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun SpaDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Color(0xFFAAD8CE)) },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = spaTextFieldColors()
    )
}

@Composable
private fun SpaDialogMultiline(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(90.dp),
        placeholder = { Text(placeholder, color = Color(0xFFAAD8CE)) },
        shape = RoundedCornerShape(12.dp),
        maxLines = 4,
        colors = spaTextFieldColors()
    )
}

@Composable
private fun spaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor = Color(0xFF1A4A40),
    unfocusedTextColor = Color(0xFF1A4A40),
    cursorColor = MintGreen
)
