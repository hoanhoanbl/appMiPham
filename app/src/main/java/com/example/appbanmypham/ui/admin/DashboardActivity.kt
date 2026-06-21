package com.example.appbanmypham.ui.admin

import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.model.AppRoles
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.theme.*
import com.example.appbanmypham.model.AppointmentStatus
import com.example.appbanmypham.model.SpaPackageType
import com.example.appbanmypham.model.TreatmentPlanStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    DashboardScreen()
                }
            }
        }
    }
}

data class DashboardStats(
    val totalSpaPackages: Int   = 0,
    val activeSpaPackages: Int  = 0,
    val totalProducts  : Int    = 0,
    val totalBrands    : Int    = 0,
    val totalOrders    : Int    = 0,
    val pendingOrders  : Int    = 0,
    val shippingOrders : Int    = 0,
    val doneOrders     : Int    = 0,
    val orderRevenue   : Double = 0.0,
    val monthlyOrderRevenue: Double = 0.0,
    val spaRevenue     : Double = 0.0,
    val monthlySpaRevenue: Double = 0.0,
    val totalRevenue   : Double = 0.0,
    val monthlyRevenue : Double = 0.0,
    val outOfStock     : Int    = 0,
    val totalReviews   : Int    = 0,
    val hiddenReviews  : Int    = 0,
    val totalReturns   : Int    = 0,   // ← MỚI
    val pendingReturns : Int    = 0,   // ← MỚI
    val totalSpaAppointments: Int = 0,
    val pendingSpaAppointments: Int = 0,
    val activeSpaAppointments: Int = 0
)

private fun DashboardStats.recalculateRevenueTotals(): DashboardStats =
    copy(
        totalRevenue = orderRevenue + spaRevenue,
        monthlyRevenue = monthlyOrderRevenue + monthlySpaRevenue
    )

private fun currentMonthRangeMillis(): LongRange {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.MONTH, 1)
    return start until calendar.timeInMillis
}

private fun Long.isInCurrentMonth(): Boolean =
    this > 0L && this in currentMonthRangeMillis()

private fun DocumentSnapshot.moneyValue(field: String): Double =
    (get(field) as? Number)?.toDouble() ?: 0.0

private fun DocumentSnapshot.millisValue(vararg fields: String): Long =
    fields.firstNotNullOfOrNull { field -> (get(field) as? Number)?.toLong()?.takeIf { it > 0L } } ?: 0L

private fun isRevenueTreatmentPlan(doc: DocumentSnapshot): Boolean {
    val status = doc.getString("status").orEmpty()
    val completedSessions = (doc.getLong("completedSessionCount") ?: 0L).toInt()
    return status == TreatmentPlanStatus.ACTIVE ||
        status == TreatmentPlanStatus.COMPLETED ||
        completedSessions > 0
}

private fun treatmentRevenueDate(doc: DocumentSnapshot): Long =
    doc.millisValue("revenueRecognizedAt", "createdAt", "completedAt", "updatedAt")

private enum class AdminHomeTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Tổng quan", Icons.Default.Dashboard),
    INVENTORY("Kho", Icons.Default.Inventory),
    SPA("Spa", Icons.Default.Spa),
    OPERATIONS("Xử lý", Icons.Default.ReceiptLong),
    USERS("Người dùng", Icons.Default.People)
}

private val visibleAdminTabs = listOf(
    AdminHomeTab.OVERVIEW,
    AdminHomeTab.INVENTORY,
    AdminHomeTab.SPA,
    AdminHomeTab.OPERATIONS
)

private data class AdminUserSummary(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: Int = AppRoles.CUSTOMER,
    val createdAt: Long = 0L
)

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser = auth.currentUser
    val adminName = remember(currentUser) {
        currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")
            ?: "Admin"
    }
    val adminInitial = adminName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"

    var stats by remember { mutableStateOf(DashboardStats()) }
    var users by remember { mutableStateOf(listOf<AdminUserSummary>()) }
    var selectedTab by remember { mutableStateOf(AdminHomeTab.OVERVIEW) }
    var selectedUserRole by remember { mutableStateOf<Int?>(null) }
    var roleTarget by remember { mutableStateOf<AdminUserSummary?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val regs = mutableListOf<ListenerRegistration>()
        var spaPackageTypesById = emptyMap<String, String>()
        var appointmentDocs = emptyList<DocumentSnapshot>()
        var treatmentPlanDocs = emptyList<DocumentSnapshot>()

        fun refreshSpaRevenue() {
            val treatmentAppointmentIds = treatmentPlanDocs
                .mapNotNull { it.getString("appointmentId")?.takeIf(String::isNotBlank) }
                .toSet()
            val revenueTreatmentPlans = treatmentPlanDocs.filter(::isRevenueTreatmentPlan)
            val treatmentRevenue = revenueTreatmentPlans.sumOf { it.moneyValue("totalPrice") }
            val monthlyTreatmentRevenue = revenueTreatmentPlans
                .filter { treatmentRevenueDate(it).isInCurrentMonth() }
                .sumOf { it.moneyValue("totalPrice") }

            val completedSpaAppointments = appointmentDocs.filter { doc ->
                val packageType = spaPackageTypesById[doc.getString("spaPackageId").orEmpty()]
                doc.getString("status") == AppointmentStatus.COMPLETED &&
                    doc.id !in treatmentAppointmentIds &&
                    packageType != SpaPackageType.TREATMENT_TEMPLATE
            }
            val appointmentRevenue = completedSpaAppointments.sumOf { it.moneyValue("spaPackagePrice") }
            val monthlyAppointmentRevenue = completedSpaAppointments
                .filter { it.millisValue("completedAt", "updatedAt", "createdAt").isInCurrentMonth() }
                .sumOf { it.moneyValue("spaPackagePrice") }

            stats = stats.copy(
                spaRevenue = treatmentRevenue + appointmentRevenue,
                monthlySpaRevenue = monthlyTreatmentRevenue + monthlyAppointmentRevenue
            ).recalculateRevenueTotals()
        }

        regs += db.collection("products").addSnapshotListener { snap, _ ->
            val productCount = snap?.size() ?: 0
            val outOfStock = snap?.documents?.count { (it.getLong("stock") ?: 0L) == 0L } ?: 0
            stats = stats.copy(totalProducts = productCount, outOfStock = outOfStock)
        }
        regs += db.collection("brands").addSnapshotListener { snap, _ ->
            stats = stats.copy(totalBrands = snap?.size() ?: 0)
        }
        regs += db.collection("orders").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            val pending = docs.count { it.getString("status") == "pending" }
            val shipping = docs.count { it.getString("status") == "shipping" }
            val done = docs.count { it.getString("status") == "done" }
            val completedOrders = docs.filter { it.getString("status") == "done" }
            val revenue = completedOrders.sumOf { it.moneyValue("totalPrice") }
            val monthlyRevenue = completedOrders
                .filter { it.millisValue("completedAt", "updatedAt", "createdAt").isInCurrentMonth() }
                .sumOf { it.moneyValue("totalPrice") }
            stats = stats.copy(
                totalOrders = docs.size,
                pendingOrders = pending,
                shippingOrders = shipping,
                doneOrders = done,
                orderRevenue = revenue,
                monthlyOrderRevenue = monthlyRevenue
            ).recalculateRevenueTotals()
        }
        regs += db.collection("reviews").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            stats = stats.copy(
                totalReviews = docs.size,
                hiddenReviews = docs.count { it.getBoolean("isHidden") == true }
            )
        }
        regs += db.collection("return_requests").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            stats = stats.copy(
                totalReturns = docs.size,
                pendingReturns = docs.count { it.getString("status") == "pending" }
            )
        }
        regs += db.collection("spa_packages").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            spaPackageTypesById = docs.associate { it.id to (it.getString("packageType") ?: SpaPackageType.SINGLE_SESSION) }
            stats = stats.copy(
                totalSpaPackages = docs.size,
                activeSpaPackages = docs.count { it.getBoolean("isActive") == true }
            )
            refreshSpaRevenue()
        }
        regs += db.collection("appointments").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            appointmentDocs = docs
            val pending = docs.count { it.getString("status") == AppointmentStatus.PENDING }
            val active = docs.count { AppointmentStatus.activeStatuses.contains(it.getString("status")) }
            stats = stats.copy(
                totalSpaAppointments = docs.size,
                pendingSpaAppointments = pending,
                activeSpaAppointments = active
            )
            refreshSpaRevenue()
        }
        regs += db.collection("treatment_plans").addSnapshotListener { snap, _ ->
            treatmentPlanDocs = snap?.documents ?: emptyList()
            refreshSpaRevenue()
        }
        regs += db.collection("users").addSnapshotListener { snap, _ ->
            users = snap?.documents?.map { doc ->
                AdminUserSummary(
                    uid = doc.id,
                    email = doc.getString("email") ?: "",
                    name = doc.getString("name") ?: doc.getString("displayName") ?: "",
                    role = (doc.getLong("role") ?: AppRoles.CUSTOMER.toLong()).toInt(),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }?.sortedWith(compareBy<AdminUserSummary> { it.role }.thenBy { it.email }) ?: emptyList()
        }

        onDispose { regs.forEach { it.remove() } }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text = { Text("Bạn có chắc muốn thoát khỏi trang quản trị?", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        auth.signOut()
                        context.startActivity(
                            Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Đăng xuất", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Hủy", color = MintGreen) }
            }
        )
    }

    roleTarget?.let { user ->
        ChangeUserRoleDialog(
            user = user,
            onDismiss = { roleTarget = null },
            onChangeRole = { newRole ->
                db.collection("users").document(user.uid).update("role", newRole)
                roleTarget = null
            }
        )
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                visibleAdminTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            AdminTabIcon(
                                tab = tab,
                                stats = stats,
                                selected = selectedTab == tab
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MintGreen,
                            selectedTextColor = MintGreen,
                            unselectedIconColor = Color(0xFFAAD8CE),
                            unselectedTextColor = Color(0xFFAAD8CE),
                            indicatorColor = Color(0xFFEAF9F5)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            AdminHeader(
                adminName = adminName,
                adminInitial = adminInitial,
                selectedTab = selectedTab,
                onLogoutClick = { showLogoutConfirm = true }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(BackgroundPrimary)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                when (selectedTab) {
                    AdminHomeTab.OVERVIEW -> AdminOverviewTab(stats = stats)
                    AdminHomeTab.INVENTORY -> AdminInventoryTab(context = context, stats = stats)
                    AdminHomeTab.SPA -> AdminSpaTab(context = context, stats = stats)
                    AdminHomeTab.OPERATIONS -> AdminOperationsTab(context = context, stats = stats)
                    AdminHomeTab.USERS -> AdminUsersTab(
                        users = users,
                        selectedRole = selectedUserRole,
                        onSelectRole = { selectedUserRole = it },
                        onChangeRole = { roleTarget = it }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LegacyDashboardScreen() {
    LegacyDashboardScreenBody()
}

@Composable
private fun LegacyDashboardScreenBody() {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }

    val currentUser = auth.currentUser
    var adminName   by remember {
        mutableStateOf(
            currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore("@")
                ?: "Admin"
        )
    }
    val adminInitial = adminName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"

    var stats             by remember { mutableStateOf(DashboardStats()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val regs = mutableListOf<ListenerRegistration>()
        var spaPackageTypesById = emptyMap<String, String>()
        var appointmentDocs = emptyList<DocumentSnapshot>()
        var treatmentPlanDocs = emptyList<DocumentSnapshot>()

        fun refreshSpaRevenue() {
            val treatmentAppointmentIds = treatmentPlanDocs
                .mapNotNull { it.getString("appointmentId")?.takeIf(String::isNotBlank) }
                .toSet()
            val revenueTreatmentPlans = treatmentPlanDocs.filter(::isRevenueTreatmentPlan)
            val treatmentRevenue = revenueTreatmentPlans.sumOf { it.moneyValue("totalPrice") }
            val monthlyTreatmentRevenue = revenueTreatmentPlans
                .filter { treatmentRevenueDate(it).isInCurrentMonth() }
                .sumOf { it.moneyValue("totalPrice") }
            val completedSpaAppointments = appointmentDocs.filter { doc ->
                val packageType = spaPackageTypesById[doc.getString("spaPackageId").orEmpty()]
                doc.getString("status") == AppointmentStatus.COMPLETED &&
                    doc.id !in treatmentAppointmentIds &&
                    packageType != SpaPackageType.TREATMENT_TEMPLATE
            }
            val appointmentRevenue = completedSpaAppointments.sumOf { it.moneyValue("spaPackagePrice") }
            val monthlyAppointmentRevenue = completedSpaAppointments
                .filter { it.millisValue("completedAt", "updatedAt", "createdAt").isInCurrentMonth() }
                .sumOf { it.moneyValue("spaPackagePrice") }

            stats = stats.copy(
                spaRevenue = treatmentRevenue + appointmentRevenue,
                monthlySpaRevenue = monthlyTreatmentRevenue + monthlyAppointmentRevenue
            ).recalculateRevenueTotals()
        }

        // Sản phẩm
        regs += db.collection("products").addSnapshotListener { snap, _ ->
            val productCount = snap?.size() ?: 0
            val outOfStock   = snap?.documents?.count { (it.getLong("stock") ?: 0L) == 0L } ?: 0
            stats = stats.copy(totalProducts = productCount, outOfStock = outOfStock)
        }

        // Thương hiệu
        regs += db.collection("brands").addSnapshotListener { snap, _ ->
            stats = stats.copy(totalBrands = snap?.size() ?: 0)
        }

        // Đơn hàng
        regs += db.collection("orders").addSnapshotListener { snap, _ ->
            val docs     = snap?.documents ?: emptyList()
            val pending  = docs.count { it.getString("status") == "pending" }
            val shipping = docs.count { it.getString("status") == "shipping" }
            val done     = docs.count { it.getString("status") == "done" }
            val completedOrders = docs.filter { it.getString("status") == "done" }
            val revenue = completedOrders.sumOf { it.moneyValue("totalPrice") }
            val monthlyRevenue = completedOrders
                .filter { it.millisValue("completedAt", "updatedAt", "createdAt").isInCurrentMonth() }
                .sumOf { it.moneyValue("totalPrice") }
            stats = stats.copy(
                totalOrders    = docs.size,
                pendingOrders  = pending,
                shippingOrders = shipping,
                doneOrders     = done,
                orderRevenue = revenue,
                monthlyOrderRevenue = monthlyRevenue
            ).recalculateRevenueTotals()
        }

        // Đánh giá
        regs += db.collection("reviews").addSnapshotListener { snap, _ ->
            val docs   = snap?.documents ?: emptyList()
            val hidden = docs.count { it.getBoolean("isHidden") == true }
            stats = stats.copy(totalReviews = docs.size, hiddenReviews = hidden)
        }

        // Trả hàng ← MỚI
        regs += db.collection("return_requests").addSnapshotListener { snap, _ ->
            val docs    = snap?.documents ?: emptyList()
            val pending = docs.count { it.getString("status") == "pending" }
            stats = stats.copy(totalReturns = docs.size, pendingReturns = pending)
        }

        regs += db.collection("spa_packages").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            spaPackageTypesById = docs.associate { it.id to (it.getString("packageType") ?: SpaPackageType.SINGLE_SESSION) }
            val active = docs.count { it.getBoolean("isActive") == true }
            stats = stats.copy(totalSpaPackages = docs.size, activeSpaPackages = active)
            refreshSpaRevenue()
        }

        regs += db.collection("appointments").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            appointmentDocs = docs
            val pending = docs.count { it.getString("status") == AppointmentStatus.PENDING }
            val active = docs.count { AppointmentStatus.activeStatuses.contains(it.getString("status")) }
            stats = stats.copy(
                totalSpaAppointments = docs.size,
                pendingSpaAppointments = pending,
                activeSpaAppointments = active
            )
            refreshSpaRevenue()
        }

        regs += db.collection("treatment_plans").addSnapshotListener { snap, _ ->
            treatmentPlanDocs = snap?.documents ?: emptyList()
            refreshSpaRevenue()
        }

        onDispose { regs.forEach { it.remove() } }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text  = { Text("Bạn có chắc muốn thoát khỏi trang quản trị?", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        auth.signOut()
                        context.startActivity(
                            Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Đăng xuất", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Hủy", color = MintGreen) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = AppGradients.mintHorizontal)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(adminInitial, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Xin chào,", color = Color.White.copy(0.8f), fontSize = 12.sp)
                    Text(adminName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Quản trị viên", color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(0.2f))
                    .clickable { showLogoutConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Logout, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(BackgroundPrimary)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            SectionLabel("TỔNG QUAN")
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalProducts}", "Sản phẩm",
                    Icons.Default.Inventory, Color(0xFFEAF9F5), MintGreen)
                StatCard(Modifier.weight(1f), "${stats.totalBrands}", "Thương hiệu",
                    Icons.Default.Category, Color(0xFFE8F0FB), Color(0xFF6B89CC))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalOrders}", "Tổng đơn",
                    Icons.Default.Receipt, Color(0xFFF3E8FB), Color(0xFF9B59B6))
                StatCard(Modifier.weight(1f), "${stats.outOfStock}", "Hết hàng",
                    Icons.Default.Warning, Color(0xFFFFECEC), Color(0xFFE57373))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalReviews}", "Đánh giá",
                    Icons.Default.Star, Color(0xFFFFFBE6), Color(0xFFFFB800))
                StatCard(Modifier.weight(1f), "${stats.hiddenReviews}", "Đã ẩn",
                    Icons.Default.VisibilityOff, Color(0xFFF5F5F5), Color(0xFF9E9E9E))
            }
            Spacer(Modifier.height(12.dp))

            // ← MỚI: Return stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalReturns}", "Trả hàng",
                    Icons.Default.AssignmentReturn, Color(0xFFFFECEC), Color(0xFFE57373))
                StatCard(Modifier.weight(1f), "${stats.pendingReturns}", "Chờ duyệt",
                    Icons.Default.HourglassEmpty, Color(0xFFFFF3E0), Color(0xFFE8A44A))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalSpaPackages}", "Goi Spa",
                    Icons.Default.Spa, Color(0xFFEAF9F5), MintGreen)
                StatCard(Modifier.weight(1f), "${stats.activeSpaPackages}", "Dang hien",
                    Icons.Default.CheckCircle, Color(0xFFE8F5E9), Color(0xFF5CAD6D))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.pendingSpaAppointments}", "Cho nhan",
                    Icons.Default.HourglassEmpty, Color(0xFFFFF3E0), Color(0xFFE8A44A))
                StatCard(Modifier.weight(1f), "${stats.activeSpaAppointments}", "Dang xu ly",
                    Icons.Default.EventAvailable, Color(0xFFE8F0FB), Color(0xFF4A90D9))
            }
            Spacer(Modifier.height(12.dp))

            RevenueCard(
                totalRevenue = stats.totalRevenue,
                monthlyRevenue = stats.monthlyRevenue,
                orderRevenue = stats.orderRevenue,
                spaRevenue = stats.spaRevenue
            )

            Spacer(Modifier.height(20.dp))

            // ── Trạng thái đơn hàng ───────────────────────────────────────────
            SectionLabel("TRẠNG THÁI ĐƠN HÀNG")
            Spacer(Modifier.height(10.dp))
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(18.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrderStatusRow("⏳", "Chờ xác nhận", stats.pendingOrders,  Color(0xFFE8A44A), Color(0xFFFFF3E0))
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                    OrderStatusRow("🚚", "Đang giao",    stats.shippingOrders, Color(0xFF9B59B6), Color(0xFFF3E8FB))
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                    OrderStatusRow("🎉", "Hoàn thành",   stats.doneOrders,     MintGreen,         Color(0xFFEAF9F5))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Menu chức năng ────────────────────────────────────────────────
            SectionLabel("CHỨC NĂNG")
            Spacer(Modifier.height(10.dp))

            DashboardCard(
                title    = "Quản lý Sản phẩm",
                subtitle = "Thêm, sửa, xoá — Kho: ${stats.totalProducts} sản phẩm",
                icon     = Icons.Default.Inventory,
                gradient = AppGradients.mintHorizontal,
                badge    = if (stats.outOfStock > 0) "${stats.outOfStock} hết hàng" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageProductActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quản lý Thương hiệu",
                subtitle = "Đang có ${stats.totalBrands} thương hiệu",
                icon     = Icons.Default.Category,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF6B89CC), Color(0xFF9BB5E8))),
                onClick  = { context.startActivity(Intent(context, ManageBrandActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quan ly Goi Spa",
                subtitle = "${stats.totalSpaPackages} goi - ${stats.activeSpaPackages} dang hien thi",
                icon     = Icons.Default.Spa,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF4DB6AC), Color(0xFF8FE3D5))),
                onClick  = { context.startActivity(Intent(context, ManageSpaPackageActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quan ly Lich Spa",
                subtitle = "${stats.totalSpaAppointments} lich - ${stats.pendingSpaAppointments} cho tu van",
                icon     = Icons.Default.EventAvailable,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF4A90D9), Color(0xFF8FBCEB))),
                badge    = if (stats.pendingSpaAppointments > 0) "${stats.pendingSpaAppointments} pending" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageSpaAppointmentActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quan ly Lieu Trinh",
                subtitle = "Theo doi sessions, no-show, anh tien trinh",
                icon     = Icons.Default.EventAvailable,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF7B61D1), Color(0xFFB8A7F2))),
                onClick  = { context.startActivity(Intent(context, ManageTreatmentActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quản lý Đơn hàng",
                subtitle = "Xử lý vận chuyển & doanh thu",
                icon     = Icons.Default.Receipt,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF9B59B6), Color(0xFFBB88D4))),
                badge    = if (stats.pendingOrders > 0) "${stats.pendingOrders} chờ duyệt" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageOrderActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quản lý Đánh giá",
                subtitle = "${stats.totalReviews} đánh giá  •  ${stats.hiddenReviews} đã ẩn",
                icon     = Icons.Default.RateReview,
                gradient = Brush.horizontalGradient(listOf(Color(0xFFE8A44A), Color(0xFFFFCC70))),
                onClick  = { context.startActivity(Intent(context, ManageReviewActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))

            // ← MỚI: Return management card
            DashboardCard(
                title    = "Quản lý Trả hàng",
                subtitle = "${stats.totalReturns} yêu cầu trả hàng",
                icon     = Icons.Default.AssignmentReturn,
                gradient = Brush.horizontalGradient(listOf(Color(0xFFE57373), Color(0xFFFFA8A8))),
                badge    = if (stats.pendingReturns > 0) "${stats.pendingReturns} chờ duyệt" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageReturnActivity::class.java)) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────
@Composable
private fun AdminHeader(
    adminName: String,
    adminInitial: String,
    selectedTab: AdminHomeTab,
    onLogoutClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = AppGradients.mintHorizontal)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(0.26f)),
                contentAlignment = Alignment.Center
            ) {
                Text(adminInitial, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Admin Center", color = Color.White.copy(0.78f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(selectedTab.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Xin chào, $adminName", color = Color.White.copy(0.76f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.2f))
                .clickable { onLogoutClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Đăng xuất", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AdminTabIcon(tab: AdminHomeTab, stats: DashboardStats, selected: Boolean) {
    val badge = when (tab) {
        AdminHomeTab.OPERATIONS -> stats.pendingOrders + stats.pendingReturns
        AdminHomeTab.SPA -> stats.pendingSpaAppointments
        else -> 0
    }
    BadgedBox(
        badge = {
            if (badge > 0) {
                Badge(containerColor = Color(0xFFE57373), contentColor = Color.White) {
                    Text(if (badge > 9) "9+" else badge.toString(), fontSize = 9.sp)
                }
            }
        }
    ) {
        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(if (selected) 24.dp else 22.dp))
    }
}

private data class StatUi(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color
)

@Composable
private fun StatGrid(items: List<StatUi>) {
    items.chunked(2).forEach { rowItems ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowItems.forEach { item ->
                StatCard(Modifier.weight(1f), item.value, item.label, item.icon, item.iconBg, item.iconTint)
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AdminOverviewTab(stats: DashboardStats) {
    SectionLabel("TỔNG QUAN")
    Spacer(Modifier.height(10.dp))
    StatGrid(
        listOf(
            StatUi("${stats.totalProducts}", "Sản phẩm", Icons.Default.Inventory, Color(0xFFEAF9F5), MintGreen),
            StatUi("${stats.totalBrands}", "Thương hiệu", Icons.Default.Category, Color(0xFFE8F0FB), Color(0xFF6B89CC)),
            StatUi("${stats.totalOrders}", "Tổng đơn", Icons.Default.Receipt, Color(0xFFF3E8FB), Color(0xFF9B59B6)),
            StatUi("${stats.outOfStock}", "Hết hàng", Icons.Default.Warning, Color(0xFFFFECEC), Color(0xFFE57373)),
            StatUi("${stats.pendingReturns}", "Trả hàng chờ", Icons.Default.AssignmentReturn, Color(0xFFFFECEC), Color(0xFFE57373)),
            StatUi("${stats.pendingSpaAppointments}", "Lịch chờ", Icons.Default.EventAvailable, Color(0xFFE8F0FB), Color(0xFF4A90D9))
        )
    )
    RevenueCard(
        totalRevenue = stats.totalRevenue,
        monthlyRevenue = stats.monthlyRevenue,
        orderRevenue = stats.orderRevenue,
        spaRevenue = stats.spaRevenue
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("VIỆC CẦN XỬ LÝ")
    Spacer(Modifier.height(10.dp))
    WorkQueueCard(stats = stats)
}

@Composable
private fun AdminInventoryTab(context: Context, stats: DashboardStats) {
    SectionLabel("KHO HÀNG")
    Spacer(Modifier.height(10.dp))
    DashboardCard(
        title = "Quản lý sản phẩm",
        subtitle = "Thêm, sửa, xoá - ${stats.totalProducts} sản phẩm",
        icon = Icons.Default.Inventory,
        gradient = AppGradients.mintHorizontal,
        badge = if (stats.outOfStock > 0) "${stats.outOfStock} hết hàng" else null,
        badgeRed = true,
        onClick = { context.startActivity(Intent(context, ManageProductActivity::class.java)) }
    )
    Spacer(Modifier.height(12.dp))
    DashboardCard(
        title = "Quản lý thương hiệu",
        subtitle = "Đang có ${stats.totalBrands} thương hiệu",
        icon = Icons.Default.Category,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF6B89CC), Color(0xFF9BB5E8))),
        onClick = { context.startActivity(Intent(context, ManageBrandActivity::class.java)) }
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("CHỈ SỐ KHO")
    Spacer(Modifier.height(10.dp))
    StatGrid(
        listOf(
            StatUi("${stats.totalProducts}", "Sản phẩm", Icons.Default.Inventory, Color(0xFFEAF9F5), MintGreen),
            StatUi("${stats.totalBrands}", "Thương hiệu", Icons.Default.Category, Color(0xFFE8F0FB), Color(0xFF6B89CC)),
            StatUi("${stats.outOfStock}", "Hết hàng", Icons.Default.Warning, Color(0xFFFFECEC), Color(0xFFE57373))
        )
    )
}

@Composable
private fun AdminSpaTab(context: Context, stats: DashboardStats) {
    SectionLabel("SPA")
    Spacer(Modifier.height(10.dp))
    DashboardCard(
        title = "Quản lý gói spa",
        subtitle = "${stats.totalSpaPackages} gói - ${stats.activeSpaPackages} đang hiển thị",
        icon = Icons.Default.Spa,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF4DB6AC), Color(0xFF8FE3D5))),
        onClick = { context.startActivity(Intent(context, ManageSpaPackageActivity::class.java)) }
    )
    Spacer(Modifier.height(12.dp))
    DashboardCard(
        title = "Quản lý lịch spa",
        subtitle = "${stats.totalSpaAppointments} lịch - ${stats.pendingSpaAppointments} chờ tư vấn",
        icon = Icons.Default.EventAvailable,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF4A90D9), Color(0xFF8FBCEB))),
        badge = if (stats.pendingSpaAppointments > 0) "${stats.pendingSpaAppointments} chờ" else null,
        badgeRed = true,
        onClick = { context.startActivity(Intent(context, ManageSpaAppointmentActivity::class.java)) }
    )
    Spacer(Modifier.height(12.dp))
    DashboardCard(
        title = "Suc chua va gio spa",
        subtitle = "Cau hinh so khach song song, gio lam viec va ngay dong cua",
        icon = Icons.Default.Settings,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF2E8A7A), Color(0xFF79D8C7))),
        onClick = { context.startActivity(Intent(context, ManageSpaCapacityActivity::class.java)) }
    )
    Spacer(Modifier.height(18.dp))
    DashboardCard(
        title = "Quan ly lieu trinh",
        subtitle = "Theo doi buoi, no-show va anh tien trinh",
        icon = Icons.Default.EventAvailable,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF7B61D1), Color(0xFFB8A7F2))),
        onClick = { context.startActivity(Intent(context, ManageTreatmentActivity::class.java)) }
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("TÌNH TRẠNG SPA")
    Spacer(Modifier.height(10.dp))
    StatGrid(
        listOf(
            StatUi("${stats.totalSpaPackages}", "Gói spa", Icons.Default.Spa, Color(0xFFEAF9F5), MintGreen),
            StatUi("${stats.activeSpaPackages}", "Đang hiện", Icons.Default.CheckCircle, Color(0xFFE8F5E9), Color(0xFF5CAD6D)),
            StatUi("${stats.pendingSpaAppointments}", "Chờ nhận", Icons.Default.HourglassEmpty, Color(0xFFFFF3E0), Color(0xFFE8A44A)),
            StatUi("${stats.activeSpaAppointments}", "Đang xử lý", Icons.Default.EventAvailable, Color(0xFFE8F0FB), Color(0xFF4A90D9))
        )
    )
}

@Composable
private fun AdminOperationsTab(context: Context, stats: DashboardStats) {
    SectionLabel("VẬN HÀNH")
    Spacer(Modifier.height(10.dp))
    DashboardCard(
        title = "Quản lý đơn hàng",
        subtitle = "Xử lý vận chuyển và doanh thu",
        icon = Icons.Default.Receipt,
        gradient = Brush.horizontalGradient(listOf(Color(0xFF7B61D1), Color(0xFFB8A7F2))),
        badge = if (stats.pendingOrders > 0) "${stats.pendingOrders} chờ" else null,
        badgeRed = true,
        onClick = { context.startActivity(Intent(context, ManageOrderActivity::class.java)) }
    )
    Spacer(Modifier.height(12.dp))
    DashboardCard(
        title = "Quản lý trả hàng",
        subtitle = "${stats.totalReturns} yêu cầu trả hàng",
        icon = Icons.Default.AssignmentReturn,
        gradient = Brush.horizontalGradient(listOf(Color(0xFFE86F73), Color(0xFFFFB1B4))),
        badge = if (stats.pendingReturns > 0) "${stats.pendingReturns} chờ" else null,
        badgeRed = true,
        onClick = { context.startActivity(Intent(context, ManageReturnActivity::class.java)) }
    )
    Spacer(Modifier.height(12.dp))
    DashboardCard(
        title = "Quản lý đánh giá",
        subtitle = "${stats.totalReviews} đánh giá - ${stats.hiddenReviews} đã ẩn",
        icon = Icons.Default.RateReview,
        gradient = Brush.horizontalGradient(listOf(Color(0xFFE8A44A), Color(0xFFFFCF7A))),
        onClick = { context.startActivity(Intent(context, ManageReviewActivity::class.java)) }
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("TRẠNG THÁI ĐƠN")
    Spacer(Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFEAF9F5))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OrderStatusLine(Icons.Default.HourglassEmpty, "Chờ xác nhận", stats.pendingOrders, Color(0xFFE8A44A), Color(0xFFFFF3E0))
            HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
            OrderStatusLine(Icons.Default.LocalShipping, "Đang giao", stats.shippingOrders, Color(0xFF7B61D1), Color(0xFFF0ECFF))
            HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
            OrderStatusLine(Icons.Default.CheckCircle, "Hoàn thành", stats.doneOrders, MintGreen, Color(0xFFEAF9F5))
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<AdminUserSummary>,
    selectedRole: Int?,
    onSelectRole: (Int?) -> Unit,
    onChangeRole: (AdminUserSummary) -> Unit
) {
    val filteredUsers = remember(users, selectedRole) {
        if (selectedRole == null) users else users.filter { it.role == selectedRole }
    }
    SectionLabel("NGƯỜI DÙNG")
    Spacer(Modifier.height(10.dp))
    StatGrid(
        listOf(
            StatUi("${users.count { it.role == AppRoles.CUSTOMER }}", "Khách hàng", Icons.Default.Person, Color(0xFFEAF9F5), MintGreen),
            StatUi("${users.count { it.role == AppRoles.CONSULTANT }}", "Tư vấn viên", Icons.Default.SupportAgent, Color(0xFFE8F0FB), Color(0xFF4A90D9)),
            StatUi("${users.count { it.role == AppRoles.ADMIN }}", "Admin", Icons.Default.AdminPanelSettings, Color(0xFFFFF3E0), Color(0xFFE8A44A))
        )
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RoleFilterChip("Tất cả", users.size, selectedRole == null) { onSelectRole(null) }
        RoleFilterChip("Khách", users.count { it.role == AppRoles.CUSTOMER }, selectedRole == AppRoles.CUSTOMER) { onSelectRole(AppRoles.CUSTOMER) }
        RoleFilterChip("Tư vấn", users.count { it.role == AppRoles.CONSULTANT }, selectedRole == AppRoles.CONSULTANT) { onSelectRole(AppRoles.CONSULTANT) }
        RoleFilterChip("Admin", users.count { it.role == AppRoles.ADMIN }, selectedRole == AppRoles.ADMIN) { onSelectRole(AppRoles.ADMIN) }
    }
    Spacer(Modifier.height(12.dp))
    if (filteredUsers.isEmpty()) {
        EmptyAdminState(
            icon = Icons.Default.People,
            title = "Chưa có tài khoản phù hợp",
            subtitle = "Danh sách sẽ tự cập nhật từ Firestore users."
        )
    } else {
        filteredUsers.forEach { user ->
            UserAdminCard(user = user, onChangeRole = { onChangeRole(user) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WorkQueueCard(stats: DashboardStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OrderStatusLine(Icons.Default.Receipt, "Đơn chờ duyệt", stats.pendingOrders, Color(0xFFE8A44A), Color(0xFFFFF3E0))
            HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
            OrderStatusLine(Icons.Default.AssignmentReturn, "Trả hàng chờ duyệt", stats.pendingReturns, Color(0xFFE57373), Color(0xFFFFECEC))
            HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
            OrderStatusLine(Icons.Default.EventAvailable, "Lịch spa chờ tư vấn", stats.pendingSpaAppointments, Color(0xFF4A90D9), Color(0xFFE8F0FB))
        }
    }
}

@Composable
private fun OrderStatusLine(icon: ImageVector, label: String, count: Int, color: Color, bgColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bgColor), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color(0xFF4A7A70), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text("$count", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoleFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MintGreen else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$label $count",
            color = if (selected) Color.White else MintGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun UserAdminCard(user: AdminUserSummary, onChangeRole: () -> Unit) {
    val roleColor = roleColor(user.role)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(roleColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(roleIcon(user.role), null, tint = roleColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name.ifBlank { user.email.substringBefore("@").ifBlank { "Người dùng" } },
                    color = Color(0xFF1A4A40),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    user.email.ifBlank { user.uid.take(16) },
                    color = Color(0xFF8ACABA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(roleColor.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(roleLabel(user.role), color = roleColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = onChangeRole) {
                Text("Đổi role", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyAdminState(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF8ACABA), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChangeUserRoleDialog(
    user: AdminUserSummary,
    onDismiss: () -> Unit,
    onChangeRole: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Đổi quyền tài khoản", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(user.email.ifBlank { user.uid }, color = Color(0xFF4A7A70), fontSize = 13.sp)
                RoleOption("Khách hàng", AppRoles.CUSTOMER, user.role, Icons.Default.Person, onChangeRole)
                RoleOption("Tư vấn viên", AppRoles.CONSULTANT, user.role, Icons.Default.SupportAgent, onChangeRole)
                RoleOption("Admin", AppRoles.ADMIN, user.role, Icons.Default.AdminPanelSettings, onChangeRole)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = MintGreen) }
        }
    )
}

@Composable
private fun RoleOption(label: String, role: Int, currentRole: Int, icon: ImageVector, onClick: (Int) -> Unit) {
    val selected = role == currentRole
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFEAF9F5) else Color(0xFFF8FFFE))
            .clickable { onClick(role) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) MintGreen else Color(0xFF8ACABA), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.CheckCircle, null, tint = MintGreen, modifier = Modifier.size(18.dp))
    }
}

private fun roleLabel(role: Int): String = when (role) {
    AppRoles.ADMIN -> "Admin"
    AppRoles.CONSULTANT -> "Tư vấn viên"
    else -> "Khách hàng"
}

private fun roleColor(role: Int): Color = when (role) {
    AppRoles.ADMIN -> Color(0xFFE8A44A)
    AppRoles.CONSULTANT -> Color(0xFF4A90D9)
    else -> MintGreen
}

private fun roleIcon(role: Int): ImageVector = when (role) {
    AppRoles.ADMIN -> Icons.Default.AdminPanelSettings
    AppRoles.CONSULTANT -> Icons.Default.SupportAgent
    else -> Icons.Default.Person
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, icon: ImageVector, iconBg: Color, iconTint: Color) {
    Card(
        modifier  = modifier, shape = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = Color(0xFF1A4A40), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, color = Color(0xFF8ACABA), fontSize = 11.sp)
            }
        }
    }
}

// ── Revenue Card ──────────────────────────────────────────────────────────────
@Composable
private fun RevenueCard(
    totalRevenue: Double,
    monthlyRevenue: Double,
    orderRevenue: Double,
    spaRevenue: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = AppGradients.mintHorizontal)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Paid, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Doanh thu", color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Đơn hoàn thành + spa/liệu trình đã ghi nhận", color = Color.White.copy(0.72f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Text(
                    formatCurrency(totalRevenue),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RevenueMiniMetric(
                        modifier = Modifier.weight(1f),
                        label = "Tháng này",
                        value = formatCurrency(monthlyRevenue)
                    )
                    RevenueMiniMetric(
                        modifier = Modifier.weight(1f),
                        label = "Tổng cộng",
                        value = formatCurrency(totalRevenue)
                    )
                }

                Text(
                    "Đơn: ${formatCurrency(orderRevenue)}  •  Spa: ${formatCurrency(spaRevenue)}",
                    color = Color.White.copy(0.74f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.TrendingUp,
                null,
                tint = Color.White.copy(0.12f),
                modifier = Modifier
                    .size(92.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 18.dp, y = 8.dp)
            )
        }
    }
}

@Composable
private fun RevenueMiniMetric(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White.copy(0.74f), fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatCurrency(value: Double): String = "%,.0fđ".format(value)

// ── Order Status Row ──────────────────────────────────────────────────────────
@Composable
private fun OrderStatusRow(emoji: String, label: String, count: Int, color: Color, bgColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color(0xFF4A7A70), fontSize = 13.sp)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("$count đơn", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Dashboard Card ────────────────────────────────────────────────────────────
@Composable
fun DashboardCard(title: String, subtitle: String, icon: ImageVector, gradient: Brush, badge: String? = null, badgeRed: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFEAF9F5))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(gradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = Color(0xFF1A4A40),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = Color(0xFF7CB9AD),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (badgeRed) Color(0xFFFFECEC) else Color(0xFFEAF9F5))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            badge,
                            color = if (badgeRed) Color(0xFFE57373) else MintGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB2E8DA), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFF6EAFA1), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
}
