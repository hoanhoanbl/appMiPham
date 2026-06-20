package com.example.appbanmypham.ui.product

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.Product
import com.example.appbanmypham.model.ProductCategories
import com.example.appbanmypham.model.SpaAppointment
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.appointmentStatusMeta
import com.example.appbanmypham.model.firestoreDocToSpaAppointment
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.cart.CartActivity
import com.example.appbanmypham.ui.order.OrderScreen          // ← import OrderScreen
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ProductScreen(
                        onGoCart   = { startActivity(Intent(this, CartActivity::class.java)) },
                        onGoLogin  = { startActivity(Intent(this, LoginActivity::class.java)) },
                        onGoSpaDetail = { spaPackage ->
                            val intent = Intent(this, SpaPackageDetailsActivity::class.java)
                            intent.putExtra("spa_package_id", spaPackage.id)
                            startActivity(intent)
                        },
                        onGoTreatmentPlans = {
                            startActivity(Intent(this, CustomerTreatmentPlanActivity::class.java))
                        },
                        onGoAppointmentChat = { appointment ->
                            startActivity(
                                Intent(this, CustomerAppointmentChatActivity::class.java)
                                    .putExtra("appointment_id", appointment.id)
                            )
                        },
                        onGoDetail = { product ->
                            val intent = Intent(this, ProductDetailsActivity::class.java)
                            intent.putExtra("product_id", product.id)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

enum class BottomTab { HOME, SPA, ORDERS, ACCOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onGoCart   : () -> Unit = {},
    onGoLogin  : () -> Unit = {},
    onGoSpaDetail: (SpaPackage) -> Unit = {},
    onGoTreatmentPlans: () -> Unit = {},
    onGoAppointmentChat: (SpaAppointment) -> Unit = {},
    onGoDetail : (Product) -> Unit = {}
) {
    val db   = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var userRole    by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            currentUser = fa.currentUser
            if (fa.currentUser == null) userRole = null
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run { userRole = null; return@LaunchedEffect }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userRole = when (doc.getLong("role")?.toInt()) {
                    1    -> "admin"
                    else -> "user"
                }
            }
            .addOnFailureListener { userRole = "user" }
    }

    val isLoggedIn = currentUser != null

    var products    by remember { mutableStateOf(listOf<Product>()) }
    var spaPackages by remember { mutableStateOf(listOf<SpaPackage>()) }
    var categories  by remember { mutableStateOf(listOf<String>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var isSpaLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var cartCount   by remember { mutableStateOf(0) }
    var isGridView  by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
    var addedToCart by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        var registration: ListenerRegistration? = null
        registration = db.collection("products")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val now  = System.currentTimeMillis()
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        Product(
                            id          = doc.id,
                            name        = doc.getString("name")        ?: "",
                            price       = doc.getDouble("price")       ?: 0.0,
                            stock       = (doc.getLong("stock")        ?: 0L).toInt(),
                            description = doc.getString("description") ?: "",
                            brandId     = doc.getString("brandId")     ?: "",
                            brandName   = doc.getString("brandName")   ?: "",
                            imageUrl    = doc.getString("imageUrl")    ?: "",
                            category    = doc.getString("category")    ?: "",
                            isHidden    = doc.getBoolean("isHidden")   ?: false,
                            createdAt   = doc.getLong("createdAt")     ?: now
                        )
                    }.getOrNull()
                } ?: emptyList()
                val visibleList = list.filter { !it.isHidden }
                products   = visibleList
                val visibleCategories = visibleList.map { it.category }.filter { it.isNotBlank() }.distinct()
                categories = ProductCategories.VALUES.filter { it in visibleCategories } +
                        visibleCategories.filter { it !in ProductCategories.VALUES }
                isLoading  = false
            }
        onDispose { registration?.remove() }
    }

    DisposableEffect(Unit) {
        val registration = db.collection("spa_packages")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    isSpaLoading = false
                    return@addSnapshotListener
                }
                spaPackages = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaPackage(it) }.getOrNull() }
                    ?.filter { it.isActive }
                    ?.sortedWith(compareBy<SpaPackage> { it.sortOrder }.thenByDescending { it.createdAt })
                    ?: emptyList()
                isSpaLoading = false
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run {
            cartCount = 0
            return@DisposableEffect onDispose {}
        }
        val registration = db.collection("carts").document(uid).collection("items")
            .addSnapshotListener { snap, _ -> cartCount = snap?.size() ?: 0 }
        onDispose { registration.remove() }
    }

    val filtered = products.filter { p ->
        val matchSearch = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.brandName.contains(searchQuery, ignoreCase = true) ||
                p.category.contains(searchQuery, ignoreCase = true)
        matchSearch
    }

    LaunchedEffect(addedToCart) {
        if (addedToCart) {
            snackbarHostState.showSnackbar("Đã thêm vào giỏ hàng ✓")
            addedToCart = false
        }
    }

    // ── Account Dialog ────────────────────────────────────────────────────────
    if (showAccountDialog && isLoggedIn) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text("Tài khoản", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40), fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(brush = AppGradients.mintHorizontal)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentUser?.displayName?.firstOrNull()?.toString()
                                ?: currentUser?.email?.firstOrNull()?.toString()
                                ?: "U").uppercase(),
                            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AccountInfoRow("Tên",   currentUser?.displayName ?: "Chưa cập nhật")
                    AccountInfoRow("Email", currentUser?.email       ?: "Chưa cập nhật")
                    AccountInfoRow("Role",  if (userRole == "admin") "Quản trị viên" else "Người dùng")
                    HorizontalDivider(color = Color(0xFFEAF9F5))
                    TextButton(
                        onClick = { showAccountDialog = false; showLogoutConfirm = true },
                        colors  = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đăng xuất", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Đóng", color = MintGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Logout Confirm Dialog ─────────────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text  = { Text("Bạn có chắc muốn đăng xuất?", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        auth.signOut()
                        selectedTab = BottomTab.HOME
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Đăng xuất", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Hủy", color = MintGreen)
                }
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ── TopBar chỉ hiện khi ở tab HOME (Orders & Account tự quản lý header) ──
            if (selectedTab == BottomTab.HOME) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brush = AppGradients.mintHorizontal)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Spa, contentDescription = "LUMIÈRE", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).height(52.dp),
                        placeholder = { Text("Tìm kiếm...", color = Color(0xFF7DB9AD), fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = Color(0xFF7DB9AD))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.96f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.96f),
                            focusedTextColor = Color(0xFF1A4A40),
                            unfocusedTextColor = Color(0xFF1A4A40),
                            cursorColor = MintGreen
                        ),
                        shape = RoundedCornerShape(17.dp)
                    )
                    HeaderIconButton(
                        icon = Icons.Default.ShoppingCart,
                        contentDescription = "Giỏ hàng",
                        onClick = { if (isLoggedIn) onGoCart() else onGoLogin() },
                        badgeCount = if (isLoggedIn) cartCount else 0
                    )
                    HeaderIconButton(
                        icon = if (isLoggedIn) Icons.Default.AccountCircle else Icons.Default.Login,
                        contentDescription = if (isLoggedIn) "Tài khoản" else "Đăng nhập",
                        onClick = { if (isLoggedIn) showAccountDialog = true else onGoLogin() }
                    )
                }
            }
            // Orders & Account tab tự quản lý header của mình → không render gì ở đây
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                BottomNavItem(
                    icon     = Icons.Default.Home,
                    label    = "Trang chủ",
                    selected = selectedTab == BottomTab.HOME,
                    onClick  = { selectedTab = BottomTab.HOME; selectedCat = null }
                )
                BottomNavItem(
                    icon     = Icons.Default.Spa,
                    label    = "Spa",
                    selected = selectedTab == BottomTab.SPA,
                    onClick  = { selectedTab = BottomTab.SPA }
                )
                BottomNavItem(
                    icon     = Icons.Default.Receipt,
                    label    = "Đơn hàng",
                    selected = selectedTab == BottomTab.ORDERS,
                    onClick  = { if (isLoggedIn) selectedTab = BottomTab.ORDERS else onGoLogin() }
                )
                BottomNavItem(
                    icon     = Icons.Default.Person,
                    label    = "Tài khoản",
                    selected = selectedTab == BottomTab.ACCOUNT,
                    onClick  = { if (isLoggedIn) selectedTab = BottomTab.ACCOUNT else onGoLogin() }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            BottomTab.HOME -> HomeTabContent(
                padding      = padding,
                isLoading    = isLoading,
                products     = products,
                filtered     = filtered,
                spaPackages  = spaPackages,
                categories   = categories,
                selectedCat  = selectedCat,
                onSelectCat  = { selectedCat = it },
                onCloseCategory = { selectedCat = null },
                isGridView   = isGridView,
                onToggleView = { isGridView = it },
                db           = db,
                auth         = auth,
                isLoggedIn   = isLoggedIn,
                onGoLogin    = onGoLogin,
                onGoDetail   = onGoDetail,
                onGoSpaDetail = onGoSpaDetail,
                onCartAdded  = { addedToCart = true }
            )


            BottomTab.SPA -> SpaTabContent(
                padding = padding,
                isLoading = isSpaLoading,
                packages = spaPackages,
                onPackageClick = onGoSpaDetail,
                onGoLogin = onGoLogin,
                onGoTreatmentPlans = onGoTreatmentPlans,
                onGoAppointmentChat = onGoAppointmentChat
            )

            BottomTab.ORDERS -> Box(modifier = Modifier.padding(padding)) {
                OrderScreen(
                    onBack = { selectedTab = BottomTab.HOME }  // nút back → về Home
                )
            }

            BottomTab.ACCOUNT -> AccountTabContent(
                padding  = padding,
                auth     = auth,
                userRole = userRole,
                onLogout = { showLogoutConfirm = true }
            )
        }
    }
}

// ── Bottom Nav Item ───────────────────────────────────────────────────────────
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SpaTabContent(
    padding: PaddingValues,
    isLoading: Boolean,
    packages: List<SpaPackage>,
    onPackageClick: (SpaPackage) -> Unit,
    onGoLogin: () -> Unit,
    onGoTreatmentPlans: () -> Unit,
    onGoAppointmentChat: (SpaAppointment) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser = auth.currentUser
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tat ca") }
    var appointments by remember { mutableStateOf(listOf<SpaAppointment>()) }
    var appointmentLoading by remember { mutableStateOf(currentUser != null) }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run {
            appointments = emptyList()
            appointmentLoading = false
            return@DisposableEffect onDispose {}
        }
        appointmentLoading = true
        val reg = db.collection("appointments")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                appointments = snap?.documents
                    ?.mapNotNull { runCatching { firestoreDocToSpaAppointment(it) }.getOrNull() }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                appointmentLoading = false
            }
        onDispose { reg.remove() }
    }

    val categories = remember(packages) {
        listOf("Tat ca") + packages.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val filtered = packages.filter { item ->
        val matchCategory = selectedCategory == "Tat ca" || item.category == selectedCategory
        val matchSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.shortDescription.contains(searchQuery, ignoreCase = true)
        matchCategory && matchSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = AppGradients.mintHorizontal)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("LUMIERE SPA", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Cham soc da va thu gian", color = Color.White.copy(0.78f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tim goi spa...", color = Color.White.copy(0.75f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
            }
        }

        if (categories.size > 1) {
            LazyRow(
                modifier = Modifier.background(Color.White).padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val selected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) MintGreen else Color(0xFFEAF9F5))
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            category,
                            color = if (selected) Color.White else MintGreen,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (packages.isEmpty()) "Chua co goi spa nao" else "Khong tim thay goi phu hop",
                        color = Color(0xFF8ACABA),
                        fontSize = 15.sp
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TreatmentPlanEntry(
                        isLoggedIn = currentUser != null,
                        onGoLogin = onGoLogin,
                        onOpen = onGoTreatmentPlans
                    )
                }
                item {
                    SpaAppointmentHistorySection(
                        isLoggedIn = currentUser != null,
                        isLoading = appointmentLoading,
                        appointments = appointments,
                        onGoLogin = onGoLogin,
                        onOpenChat = onGoAppointmentChat,
                        onCancel = { appointment ->
                            val uid = currentUser?.uid ?: return@SpaAppointmentHistorySection
                            if (appointment.userId == uid && appointment.status == AppointmentStatus.PENDING) {
                                val now = System.currentTimeMillis()
                                db.collection("appointments").document(appointment.id).update(
                                    mapOf(
                                        "status" to AppointmentStatus.CANCELLED,
                                        "cancelledAt" to now,
                                        "updatedAt" to now,
                                        "cancelReason" to "Customer cancelled"
                                    )
                                )
                            }
                        }
                    )
                }
                items(filtered, key = { it.id }) { item ->
                    SpaPackageCard(item = item, onClick = { onPackageClick(item) })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun TreatmentPlanEntry(
    isLoggedIn: Boolean,
    onGoLogin: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { if (isLoggedIn) onOpen() else onGoLogin() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintGreen)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Lieu trinh cua toi", color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isLoggedIn) "Xem tien do, anh dieu tri va chat voi tu van vien" else "Dang nhap de xem lieu trinh spa",
                    color = Color(0xFF8ACABA),
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFAAD8CE))
        }
    }
}

@Composable
private fun SpaAppointmentHistorySection(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    appointments: List<SpaAppointment>,
    onGoLogin: () -> Unit,
    onOpenChat: (SpaAppointment) -> Unit,
    onCancel: (SpaAppointment) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lich spa cua toi", color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            when {
                !isLoggedIn -> {
                    Text("Dang nhap de xem va quan ly lich hen spa.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGoLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dang nhap")
                    }
                }
                isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MintGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Dang tai lich hen...", color = Color(0xFF8ACABA), fontSize = 13.sp)
                }
                appointments.isEmpty() -> Text("Ban chua co lich hen spa nao.", color = Color(0xFF8ACABA), fontSize = 13.sp)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appointments.forEach { appointment ->
                        CustomerAppointmentCard(
                            appointment = appointment,
                            onOpenChat = { onOpenChat(appointment) },
                            onCancel = { onCancel(appointment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerAppointmentCard(appointment: SpaAppointment, onOpenChat: () -> Unit, onCancel: () -> Unit) {
    val meta = appointmentStatusMeta(appointment.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FFFE))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appointment.spaPackageName, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("${appointment.appointmentDateLabel} - ${appointment.timeSlotLabel}", color = Color(0xFF5A8A80), fontSize = 12.sp)
                if (appointment.consultantName.isNotBlank() || appointment.consultantEmail.isNotBlank()) {
                    Text("Tu van: ${appointment.consultantName.ifBlank { appointment.consultantEmail }}", color = MintGreen, fontSize = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusBg(appointment.status))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(meta.label, color = statusColor(appointment.status), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (appointment.status == AppointmentStatus.PENDING) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Huy lich", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (appointment.consultantId.isNotBlank() && appointment.status in setOf(AppointmentStatus.ASSIGNED, AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED, AppointmentStatus.NO_SHOW)) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenChat, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = MintGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Chat tu van", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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

@Composable
private fun SpaPackageCard(item: SpaPackage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(38.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFEAF9F5))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(item.category.ifBlank { "Spa" }, color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${item.durationMinutes} phut", color = Color(0xFF8ACABA), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.name,
                    color = Color(0xFF1A4A40),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.shortDescription.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.shortDescription,
                        color = Color(0xFF7FAEA4),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${"%,.0f".format(item.price)}d", color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (item.originalPrice > item.price) {
                        Spacer(Modifier.width(8.dp))
                        Text("${"%,.0f".format(item.originalPrice)}d", color = Color(0xFFAAD8CE), fontSize = 11.sp)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFB2E8DA))
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon     : ImageVector,
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon     = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
        label    = {
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor   = MintGreen,
            selectedTextColor   = MintGreen,
            unselectedIconColor = Color(0xFFAAD8CE),
            unselectedTextColor = Color(0xFFAAD8CE),
            indicatorColor      = Color(0xFFEAF9F5)
        )
    )
}

// ── HOME Tab ──────────────────────────────────────────────────────────────────
@Composable
private fun HomeTabContent(
    padding      : PaddingValues,
    isLoading    : Boolean,
    products     : List<Product>,
    filtered     : List<Product>,
    spaPackages  : List<SpaPackage>,
    categories   : List<String>,
    selectedCat  : String?,
    onSelectCat  : (String) -> Unit,
    onCloseCategory: () -> Unit,
    isGridView   : Boolean,
    onToggleView : (Boolean) -> Unit,
    db           : FirebaseFirestore,
    auth         : FirebaseAuth,
    isLoggedIn   : Boolean,
    onGoLogin    : () -> Unit,
    onGoDetail   : (Product) -> Unit,
    onGoSpaDetail: (SpaPackage) -> Unit,
    onCartAdded  : () -> Unit
) {
    val categoryItems = remember(products, categories) {
        categories
            .filter { it.isNotBlank() }
            .map { category ->
                Triple(
                    category,
                    products.firstOrNull { it.category == category },
                    products.count { it.category == category }
                )
            }
    }
    val featuredProducts = remember(filtered) { filtered.take(8) }
    val bestSellerProducts = remember(filtered) { filtered.sortedByDescending { it.stock }.take(8) }
    val featuredSpaPackages = remember(spaPackages) { spaPackages.take(6) }

    fun addProduct(product: Product) {
        if (isLoggedIn) {
            addToCart(db, auth, product)
            onCartAdded()
        } else {
            onGoLogin()
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MintGreen)
        }
        selectedCat != null -> CategoryProductsScreen(
            padding = padding,
            category = selectedCat,
            products = products.filter { it.category == selectedCat },
            filteredProducts = filtered.filter { it.category == selectedCat },
            categories = categoryItems,
            isLoggedIn = isLoggedIn,
            onBack = onCloseCategory,
            onSelectCategory = onSelectCat,
            onGoDetail = onGoDetail,
            onAddProduct = { addProduct(it) }
        )
        filtered.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFF8ACABA), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Không tìm thấy sản phẩm", color = Color(0xFF8ACABA), fontSize = 15.sp)
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (categoryItems.isNotEmpty()) {
                item {
                    HomeSectionHeader(
                        title = "Danh mục"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categoryItems, key = { it.first }) { (category, product, count) ->
                            CategoryTile(
                                title = category,
                                product = product,
                                productCount = count,
                                onClick = { onSelectCat(category) }
                            )
                        }
                    }
                }
            }

            if (featuredSpaPackages.isNotEmpty()) {
                item {
                    HomeSectionHeader(title = "Gói spa nổi bật", actionText = "Xem spa")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(featuredSpaPackages, key = { it.id }) { spaPackage ->
                            FeaturedSpaCard(
                                spaPackage = spaPackage,
                                onClick = { onGoSpaDetail(spaPackage) }
                            )
                        }
                    }
                }
            }

            if (featuredProducts.isNotEmpty()) {
                item {
                    HomeSectionHeader(title = "Sản phẩm nổi bật", actionText = "${products.size} sản phẩm")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(featuredProducts, key = { it.id }) { product ->
                            FeaturedProductCard(
                                product = product,
                                onClick = { onGoDetail(product) },
                                onAddToCart = { addProduct(product) }
                            )
                        }
                    }
                }
            }

            if (bestSellerProducts.isNotEmpty()) {
                item {
                    HomeSectionHeader(title = "Bán chạy", actionText = "Gợi ý")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(bestSellerProducts, key = { "best-${it.id}" }) { product ->
                            FeaturedProductCard(
                                product = product,
                                onClick = { onGoDetail(product) },
                                onAddToCart = { addProduct(product) }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tất cả sản phẩm", color = Color(0xFF1A4A40), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} sản phẩm phù hợp", color = Color(0xFF8ACABA), fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAF9F5))) {
                        IconButton(
                            onClick = { onToggleView(true) },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isGridView) MintGreen else Color.Transparent)
                        ) {
                            Icon(Icons.Default.GridView, null,
                                tint = if (isGridView) Color.White else MintGreen, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onToggleView(false) },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (!isGridView) MintGreen else Color.Transparent)
                        ) {
                            Icon(Icons.Default.ViewList, null,
                                tint = if (!isGridView) Color.White else MintGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (isGridView) {
                items(filtered.chunked(2), key = { row -> row.joinToString("-") { it.id } }) { rowProducts ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowProducts.forEach { product ->
                            Box(modifier = Modifier.weight(1f)) {
                                ProductGridCard(
                                    product = product,
                                    isLoggedIn = isLoggedIn,
                                    onAddToCart = { addProduct(it) },
                                    onCardClick = { onGoDetail(product) }
                                )
                            }
                        }
                        if (rowProducts.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { product ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ProductListCard(
                            product = product,
                            isLoggedIn = isLoggedIn,
                            onAddToCart = { addProduct(it) },
                            onCardClick = { onGoDetail(product) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryProductsScreen(
    padding: PaddingValues,
    category: String,
    products: List<Product>,
    filteredProducts: List<Product>,
    categories: List<Triple<String, Product?, Int>>,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onGoDetail: (Product) -> Unit,
    onAddProduct: (Product) -> Unit
) {
    val heroProduct = remember(products) {
        products.firstOrNull { it.imageUrl.isNotBlank() } ?: products.firstOrNull()
    }
    val otherCategories = remember(categories, category) {
        categories.filter { it.first != category }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFE2F6F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFFE5FAF4), Color.White)
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.82f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!heroProduct?.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroProduct?.imageUrl,
                                    contentDescription = category,
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(Icons.Default.Category, contentDescription = null, tint = MintGreen, modifier = Modifier.size(44.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                category,
                                color = Color(0xFF0F4D42),
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = MintGreen, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }

        if (otherCategories.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(otherCategories, key = { it.first }) { (name, _, count) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .clickable { onSelectCategory(name) }
                                .padding(horizontal = 13.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = Color(0xFF0F4D42), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Text("$count", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sản phẩm", color = Color(0xFF0F4D42), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (filteredProducts.size == products.size) "${products.size} sản phẩm trong danh mục"
                        else "${filteredProducts.size}/${products.size} sản phẩm phù hợp",
                        color = Color(0xFF7DB9AD),
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Trang chủ", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (filteredProducts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFF8ACABA), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Không có sản phẩm phù hợp", color = Color(0xFF0F4D42), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Thử xoá bớt từ khoá tìm kiếm hoặc chọn danh mục khác.", color = Color(0xFF8ACABA), fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filteredProducts.chunked(2), key = { row -> row.joinToString("-") { it.id } }) { rowProducts ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowProducts.forEach { product ->
                        Box(modifier = Modifier.weight(1f)) {
                            ProductGridCard(
                                product = product,
                                isLoggedIn = isLoggedIn,
                                onAddToCart = onAddProduct,
                                onCardClick = { onGoDetail(product) }
                            )
                        }
                    }
                    if (rowProducts.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MintGreen)
            )
            Spacer(Modifier.width(9.dp))
            Text(title, color = Color(0xFF0F4D42), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        if (actionText != null) {
            TextButton(onClick = { onActionClick?.invoke() }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(actionText, color = Color(0xFF6EAFA1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF6EAFA1), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    product: Product?,
    productCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .height(172.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.9.dp,
            color = Color(0xFFDDF4EE)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFF3FCF9))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF4FBF9)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!product?.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = product?.imageUrl,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MintGreen, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text(
                    title,
                    color = Color(0xFF0F4D42),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFEAF9F5))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$productCount sản phẩm",
                        color = MintGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MintGreen.copy(alpha = 0.82f))
                )
            }
        }
    }
}

@Composable
private fun FeaturedSpaCard(
    spaPackage: SpaPackage,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(214.dp).height(188.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(104.dp).background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (spaPackage.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = spaPackage.imageUrl,
                        contentDescription = spaPackage.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(36.dp))
                }
                if (spaPackage.durationMinutes > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${spaPackage.durationMinutes} phút", color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(spaPackage.name, color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(spaPackage.category.ifBlank { "Chăm sóc spa" }, color = Color(0xFF8ACABA), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${"%,.0f".format(spaPackage.price)}đ", color = Color(0xFFFF7A1A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeaturedProductCard(
    product: Product,
    onClick: () -> Unit,
    onAddToCart: () -> Unit
) {
    val isNew = remember(product.createdAt) {
        (System.currentTimeMillis() - product.createdAt) < 7 * 24 * 60 * 60 * 1000L
    }
    val isOutOfStock = product.stock == 0
    Card(
        modifier = Modifier.width(168.dp).height(238.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFEAF9F5))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF4FBF9)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                        alpha = if (isOutOfStock) 0.55f else 1f
                    )
                } else {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(34.dp))
                }
                when {
                    isOutOfStock -> Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE0E0E0))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Hết hàng", color = Color(0xFF888888), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    isNew -> Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(20.dp)).background(brush = AppGradients.mintHorizontal)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("MỚI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(product.name, color = Color(0xFF1A4A40), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.brandName.ifBlank { product.category }, color = Color(0xFF7DB9AD), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${"%,.0f".format(product.price)}đ", color = Color(0xFFFF7A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (product.stock > 0) Text("Còn ${product.stock}", color = Color(0xFFAAD8CE), fontSize = 10.sp)
                    }
                    if (!isOutOfStock) {
                        IconButton(
                            onClick = onAddToCart,
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(MintGreen)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = "Thêm vào giỏ", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── ACCOUNT Tab ───────────────────────────────────────────────────────────────
@Composable
private fun AccountTabContent(
    padding  : PaddingValues,
    auth     : FirebaseAuth,
    userRole : String?,
    onLogout : () -> Unit
) {
    val user = auth.currentUser
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(brush = AppGradients.mintHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user?.displayName?.firstOrNull()?.toString()
                    ?: user?.email?.firstOrNull()?.toString() ?: "U").uppercase(),
                color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            user?.displayName ?: "Người dùng",
            color = Color(0xFF1A4A40), fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Text(user?.email ?: "", color = Color(0xFF8ACABA), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (userRole == "admin") Color(0xFFEAF9F5) else Color(0xFFF0F0F0))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                if (userRole == "admin") "⭐ Quản trị viên" else "👤 Người dùng",
                color      = if (userRole == "admin") MintGreen else Color(0xFF888888),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Thông tin tài khoản",
                    color      = MintGreen,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                AccountInfoRow("Họ và tên",   user?.displayName                  ?: "Chưa cập nhật")
                HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                AccountInfoRow("Email",        user?.email                       ?: "Chưa cập nhật")
                HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                AccountInfoRow("ID tài khoản", user?.uid?.take(14)?.plus("...") ?: "-")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onLogout,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFECEC))
        ) {
            Icon(Icons.Default.Logout, null, tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Đăng xuất", color = Color(0xFFE57373), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

// ── Account Info Row ──────────────────────────────────────────────────────────
@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF8ACABA), fontSize = 13.sp)
        Text(value, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Grid Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ProductGridCard(
    product     : Product,
    isLoggedIn  : Boolean,
    onAddToCart : (Product) -> Unit,
    onCardClick : () -> Unit
) {
    val isNew        = remember(product.createdAt) {
        (System.currentTimeMillis() - product.createdAt) < 7 * 24 * 60 * 60 * 1000L
    }
    val isOutOfStock = product.stock == 0

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFEAF9F5))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(142.dp)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF4FBF9)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = product.imageUrl,
                        contentDescription = product.name,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize().padding(8.dp),
                        alpha              = if (isOutOfStock) 0.5f else 1f
                    )
                } else {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(40.dp))
                }

                if (isNew && !isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(brush = AppGradients.mintHorizontal)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("MỚI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE0E0E0))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (product.stock in 1..5) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Còn ${product.stock}", color = Color(0xFFE8A44A), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (product.brandName.isNotBlank() || product.category.isNotBlank()) {
                    Text(
                        product.brandName.ifBlank { product.category },
                        color = MintGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    product.name,
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%,.0f".format(product.price)}đ",
                        color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFFFF7A1A),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                    if (!isOutOfStock) {
                        IconButton(
                            onClick  = { onAddToCart(product) },
                            modifier = Modifier.size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(brush = AppGradients.mintHorizontal)
                        ) {
                            Icon(
                                if (isLoggedIn) Icons.Default.ShoppingCart else Icons.Default.Login,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── List Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ProductListCard(
    product     : Product,
    isLoggedIn  : Boolean,
    onAddToCart : (Product) -> Unit,
    onCardClick : () -> Unit
) {
    val isNew        = remember(product.createdAt) {
        (System.currentTimeMillis() - product.createdAt) < 7 * 24 * 60 * 60 * 1000L
    }
    val isOutOfStock = product.stock == 0

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = product.imageUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        alpha              = if (isOutOfStock) 0.5f else 1f
                    )
                } else { Text("🌿", fontSize = 32.sp) }

                if (isNew && !isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart)
                            .clip(RoundedCornerShape(bottomEnd = 6.dp))
                            .background(brush = AppGradients.mintHorizontal)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("MỚI", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart)
                            .clip(RoundedCornerShape(bottomEnd = 6.dp))
                            .background(Color(0xFFE0E0E0))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(product.brandName, color = MintGreen, fontSize = 10.sp)
                Text(
                    product.name,
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                if (product.category.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(product.category, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${"%,.0f".format(product.price)}đ",
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            if (!isOutOfStock) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = AppGradients.mintHorizontal)
                        .clickable { onAddToCart(product) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isLoggedIn) Icons.Default.ShoppingCartCheckout else Icons.Default.Login,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Add to Cart ───────────────────────────────────────────────────────────────
private fun addToCart(db: FirebaseFirestore, auth: FirebaseAuth, product: Product) {
    val uid     = auth.currentUser?.uid ?: return
    val cartRef = db.collection("carts").document(uid)
        .collection("items").document(product.id)

    db.runTransaction { transaction ->
        val snap = transaction.get(cartRef)
        val qty  = (snap.getLong("quantity") ?: 0L).toInt()
        transaction.set(
            cartRef, hashMapOf(
                "productId" to product.id,
                "name"      to product.name,
                "price"     to product.price,
                "imageUrl"  to product.imageUrl,
                "brandName" to product.brandName,
                "quantity"  to qty + 1,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }
}
