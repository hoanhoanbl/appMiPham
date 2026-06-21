package com.example.appbanmypham.ui.order

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.OrderEntity
import com.example.appbanmypham.model.OrderItem
import com.example.appbanmypham.model.ReviewEntity
import com.example.appbanmypham.ui.review.StarRow
import com.example.appbanmypham.ui.review.WriteReviewDialog
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.example.appbanmypham.ui.admin.RETURN_STATUS_LIST
import com.example.appbanmypham.ui.admin.getReturnStatus
import com.example.appbanmypham.ui.admin.firestoreDocToReturnRequest
import com.example.appbanmypham.model.ReturnRequest

class OrderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    OrderScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parse itemsJson → List<OrderItem>
// ─────────────────────────────────────────────────────────────────────────────
fun parseOrderItems(json: String): List<OrderItem> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OrderItem(
                productId = o.optString("productId"),
                name      = o.optString("name"),
                brandName = o.optString("brandName"),
                imageUrl  = o.optString("imageUrl"),
                price     = o.optDouble("price", 0.0),
                quantity  = o.optInt("quantity", 1)
            )
        }
    }.getOrDefault(emptyList())
}

// ─────────────────────────────────────────────────────────────────────────────
// Firestore document → OrderEntity
// ─────────────────────────────────────────────────────────────────────────────
@Suppress("UNCHECKED_CAST")
fun firestoreDocToEntity(doc: com.google.firebase.firestore.DocumentSnapshot): OrderEntity? {
    return runCatching {
        val rawItems  = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
        val itemsJson = JSONArray().apply {
            rawItems.forEach { m ->
                put(JSONObject().apply {
                    put("productId", m["productId"] as? String ?: "")
                    put("name",      m["name"]      as? String ?: "")
                    put("brandName", m["brandName"] as? String ?: "")
                    put("imageUrl",  m["imageUrl"]  as? String ?: "")
                    put("price",     (m["price"]    as? Number)?.toDouble() ?: 0.0)
                    put("quantity",  (m["quantity"] as? Number)?.toInt()    ?: 1)
                })
            }
        }.toString()
        OrderEntity(
            id            = doc.id,
            userId        = doc.getString("userId")        ?: "",
            totalPrice    = doc.getDouble("totalPrice")    ?: 0.0,
            address       = doc.getString("address")       ?: "",
            phoneNumber   = doc.getString("phoneNumber")   ?: "",
            receiverName  = doc.getString("receiverName")  ?: "",
            status        = doc.getString("status")        ?: "pending",
            paymentMethod = doc.getString("paymentMethod") ?: "COD",
            itemsJson     = itemsJson,
            createdAt     = doc.getLong("createdAt")       ?: System.currentTimeMillis()
        )
    }.getOrNull()
}

// ─────────────────────────────────────────────────────────────────────────────
// Status config
// ─────────────────────────────────────────────────────────────────────────────
data class OrderStatusInfo(
    val key     : String,
    val label   : String,
    val emoji   : String,
    val desc    : String,
    val color   : Color,
    val bgColor : Color,
    val step    : Int
)

val ORDER_STATUS_LIST = listOf(
    OrderStatusInfo("pending",   "Chờ xác nhận", "⏳", "Đơn đang chờ shop xác nhận",      Color(0xFFE8A44A), Color(0xFFFFF3E0), 0),
    OrderStatusInfo("confirmed", "Đã xác nhận",  "✅", "Shop đã xác nhận, đang chuẩn bị",  Color(0xFF4A90D9), Color(0xFFE8F0FB), 1),
    OrderStatusInfo("shipping",  "Đang giao",    "🚚", "Đơn hàng đang trên đường đến bạn", Color(0xFF9B59B6), Color(0xFFF3E8FB), 2),
    OrderStatusInfo("done",      "Hoàn thành",   "🎉", "Bạn đã nhận được hàng thành công", MintGreen,         Color(0xFFEAF9F5), 3),
    OrderStatusInfo("returned",  "Đã trả hàng",  "↩", "Đơn hàng đã hoàn tất trả hàng",    Color(0xFF7B61D1), Color(0xFFF0ECFF), 4),
    OrderStatusInfo("cancelled", "Đã hủy",       "❌", "Đơn hàng đã bị hủy",              Color(0xFFE57373), Color(0xFFFFECEC), -1)
)
val RETURN_REASONS = listOf(
    "Sản phẩm bị lỗi/hư hỏng",
    "Không đúng mô tả/hình ảnh",
    "Giao sai sản phẩm",
    "Không còn nhu cầu sử dụng",
    "Lý do khác"
)
fun getOrderStatus(key: String) = ORDER_STATUS_LIST.find { it.key == key }
    ?: OrderStatusInfo(key, key, "📦", "", Color(0xFF8ACABA), Color(0xFFEAF9F5), 0)

private fun isReturnedOrder(order: OrderEntity, myReturn: ReturnRequest?): Boolean =
    order.status == "returned" || myReturn?.status == "completed"

private fun effectiveOrderStatusKey(order: OrderEntity, myReturn: ReturnRequest?): String =
    if (isReturnedOrder(order, myReturn)) "returned" else order.status

private fun displayOrderStatus(order: OrderEntity, myReturn: ReturnRequest?): OrderStatusInfo =
    getOrderStatus(effectiveOrderStatusKey(order, myReturn))

private fun orderStatusIcon(statusKey: String): ImageVector = when (statusKey) {
    "pending" -> Icons.Default.PendingActions
    "confirmed" -> Icons.Default.Inventory2
    "shipping" -> Icons.Default.LocalShipping
    "done" -> Icons.Default.CheckCircle
    "returned" -> Icons.Default.AssignmentReturn
    "cancelled" -> Icons.Default.Cancel
    else -> Icons.Default.ReceiptLong
}

data class OrderFilterTab(val key: String?, val label: String)

val ORDER_TABS = listOf(
    OrderFilterTab(null,        "Tất cả"),
    OrderFilterTab("pending",   "Chờ xác nhận"),
    OrderFilterTab("confirmed", "Đã xác nhận"),
    OrderFilterTab("shipping",  "Đang giao"),
    OrderFilterTab("done",      "Hoàn thành"),
    OrderFilterTab("returned",  "Đã trả hàng"),
    OrderFilterTab("cancelled", "Đã hủy")
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OrderScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }
    val uid     = auth.currentUser?.uid ?: ""

    val orderDao  = remember { AppDatabase.getInstance(context).orderDao() }
    val reviewDao = remember { AppDatabase.getInstance(context).reviewDao() }

    val orders  by orderDao.getOrdersByUser(uid).collectAsStateWithLifecycle(initialValue = emptyList())
    // Tất cả review của user này — dùng để kiểm tra đã review chưa
    val myReviews by reviewDao.getVisibleReviewsByProduct("").collectAsStateWithLifecycle(initialValue = emptyList())
    // Lấy toàn bộ review từ Room (không lọc theo productId) để check
    val allMyReviews by reviewDao.getAllReviews().collectAsStateWithLifecycle(initialValue = emptyList())

    var isSyncing by remember { mutableStateOf(false) }

    // Sync Firestore orders → Room
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose {}
        isSyncing = true
        var reg: ListenerRegistration? = null
        reg = db.collection("orders")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) { isSyncing = false; return@addSnapshotListener }
                CoroutineScope(Dispatchers.IO).launch {
                    snap?.documents?.forEach { doc ->
                        val entity = firestoreDocToEntity(doc) ?: return@forEach
                        orderDao.insert(entity)
                    }
                    isSyncing = false
                }
            }
        onDispose { reg?.remove() }
    }

    // Sync Firestore reviews → Room (để biết đã review đơn nào)
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("reviews")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    snap?.documents?.forEach { doc ->
                        reviewDao.insert(ReviewEntity(
                            id        = doc.id,
                            productId = doc.getString("productId") ?: "",
                            orderId   = doc.getString("orderId")   ?: "",
                            userId    = doc.getString("userId")    ?: "",
                            userName  = doc.getString("userName")  ?: "",
                            rating    = (doc.getLong("rating")     ?: 5L).toInt(),
                            comment   = doc.getString("comment")   ?: "",
                            createdAt = doc.getLong("createdAt")   ?: System.currentTimeMillis(),
                            isHidden  = doc.getBoolean("isHidden") ?: false
                        ))
                    }
                }
            }
        onDispose { reg.remove() }
    }

    var filterKey    by remember { mutableStateOf<String?>(null) }
    var detailOrder  by remember { mutableStateOf<OrderEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<OrderEntity?>(null) }

    // State cho dialog viết review từ màn hình đơn hàng
    var reviewTarget by remember { mutableStateOf<Pair<OrderEntity, OrderItem>?>(null) }

    var myReturns by remember { mutableStateOf(listOf<ReturnRequest>()) }
    var returnTarget by remember { mutableStateOf<OrderEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null }
    }

    val returnsByOrder = remember(myReturns) { myReturns.associateBy { it.orderId } }
    val filtered = orders.filter { order ->
        filterKey == null || effectiveOrderStatusKey(order, returnsByOrder[order.id]) == filterKey
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    detailOrder?.let { order ->
        OrderDetailDialog(
            order     = order,
            myReturn  = returnsByOrder[order.id],
            onDismiss = { detailOrder = null },
            onCancel  = if (order.status == "pending") { { cancelTarget = order; detailOrder = null } } else null
        )
    }
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose {}
        val reg = db.collection("return_requests")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                myReturns = snap?.documents?.mapNotNull { firestoreDocToReturnRequest(it) } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    fun submitReturnRequest(order: OrderEntity, reason: String, note: String) {
        val id = db.collection("return_requests").document().id
        val data = hashMapOf(
            "orderId"   to order.id,
            "userId"    to uid,
            "reason"    to reason,
            "note"      to note,
            "status"    to "pending",
            "adminNote" to "",
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("return_requests").document(id).set(data)
            .addOnSuccessListener { snackMsg = "📦 Đã gửi yêu cầu trả hàng" }
            .addOnFailureListener { snackMsg = "❌ Gửi yêu cầu thất bại" }
    }

    // ✅ Hủy đơn hàng + HOÀN LẠI TỒN KHO cho từng sản phẩm trong đơn.
    // Dùng Firestore Transaction để:
    //  (1) Đọc trạng thái đơn ngay tại thời điểm hủy — nếu đơn đã "cancelled" rồi thì bỏ qua,
    //      tránh trường hợp bấm hủy 2 lần làm cộng tồn kho 2 lần.
    //  (2) Cộng lại đúng số lượng đã đặt vào "stock" của từng sản phẩm và đổi status
    //      cùng lúc, đảm bảo tính nguyên tử (atomic), an toàn khi có nhiều thao tác đồng thời.
    fun cancelOrderAndRestoreStock(order: OrderEntity) {
        val items = parseOrderItems(order.itemsJson)
        val neededByProduct: Map<String, Int> = items
            .groupBy { it.productId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }

        val orderRef = db.collection("orders").document(order.id)

        db.runTransaction { transaction ->
            val orderSnap = transaction.get(orderRef)
            val currentStatus = orderSnap.getString("status") ?: ""

            // Đơn đã bị hủy từ trước (vd bấm hủy 2 lần liên tiếp) -> không cộng kho lại nữa
            if (currentStatus == "cancelled") {
                return@runTransaction null
            }

            // Đọc tồn kho hiện tại của từng sản phẩm có trong đơn
            val productRefs = neededByProduct.keys.associateWith { productId ->
                db.collection("products").document(productId)
            }
            val currentStocks = productRefs.mapValues { (_, ref) ->
                transaction.get(ref).getLong("stock") ?: 0L
            }

            // Cộng lại đúng số lượng đã đặt vào tồn kho từng sản phẩm
            neededByProduct.forEach { (productId, qty) ->
                val ref = productRefs[productId] ?: return@forEach
                val newStock = (currentStocks[productId] ?: 0L) + qty
                transaction.update(ref, "stock", newStock)
            }

            // Cập nhật trạng thái đơn hàng sang "cancelled"
            transaction.update(orderRef, "status", "cancelled")

            null
        }
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch { orderDao.updateStatus(order.id, "cancelled") }
                snackMsg = "❌ Đã hủy đơn hàng #${order.id.take(8).uppercase()} và hoàn lại tồn kho"
            }
            .addOnFailureListener {
                snackMsg = "Hủy đơn thất bại: ${it.message}"
            }
    }

    cancelTarget?.let { order ->
        CancelOrderDialog(
            order     = order,
            onDismiss = { cancelTarget = null },
            onConfirm = {
                cancelOrderAndRestoreStock(order)
                cancelTarget = null
            }
        )
    }

    // Dialog viết review từ màn đơn hàng
    reviewTarget?.let { (order, item) ->
        WriteReviewDialog(
            productId   = item.productId,
            orderId     = order.id,
            onDismiss   = { reviewTarget = null },
            onSubmitted = {
                reviewTarget = null
                snackMsg = "⭐ Cảm ơn bạn đã đánh giá!"
            }
        )
    }
    returnTarget?.let { order ->
        RequestReturnDialog(
            order     = order,
            onDismiss = { returnTarget = null },
            onSubmit  = { reason, note ->
                submitReturnRequest(order, reason, note)
                returnTarget = null
            }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Đơn hàng", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Theo dõi mua sắm của bạn", color = Color.White.copy(0.78f), fontSize = 11.sp)
                }
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp).align(Alignment.CenterEnd).padding(end = 16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrderFilterRow(
                    orders = orders,
                    returnsByOrder = returnsByOrder,
                    selected = filterKey,
                    onSelect = { filterKey = it }
                )
            }

            when {
                isSyncing && orders.isEmpty() -> item {
                    OrderInlineState(
                        icon = Icons.Default.Sync,
                        title = "Đang tải đơn hàng...",
                        description = "Dữ liệu đang được đồng bộ từ tài khoản của bạn.",
                        loading = true
                    )
                }
                !isSyncing && orders.isEmpty() -> item {
                    OrderInlineState(
                        icon = Icons.Default.ShoppingBag,
                        title = "Chưa có đơn hàng nào",
                        description = "Các đơn mỹ phẩm sau khi mua sẽ xuất hiện ở đây."
                    )
                }
                filtered.isEmpty() -> item {
                    OrderInlineState(
                        icon = Icons.Default.ReceiptLong,
                        title = "Không có đơn hàng nào",
                        description = "Thử chọn trạng thái khác để xem thêm đơn hàng."
                    )
                }
                else -> {
                    item {
                        Text(
                            "Danh sách đơn hàng",
                            color = Color(0xFF1A4A40),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    items(filtered, key = { it.id }) { order ->
                        OrderCard(
                            order           = order,
                            myReviews       = allMyReviews.filter { it.userId == uid },
                            myReturn        = myReturns.firstOrNull { it.orderId == order.id },
                            onViewDetail    = { detailOrder = order },
                            onCancel        = if (order.status == "pending") { { cancelTarget = order } } else null,
                            onReviewItem    = { item -> reviewTarget = Pair(order, item) },
                            onRequestReturn = { returnTarget = order }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OrderFilterRow(
    orders: List<OrderEntity>,
    returnsByOrder: Map<String, ReturnRequest>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ORDER_TABS) { tab ->
            val isSelected = tab.key == selected
            val count = if (tab.key == null) orders.size else orders.count { effectiveOrderStatusKey(it, returnsByOrder[it.id]) == tab.key }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) AppGradients.mintHorizontal
                        else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White, Color.White))
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else Color(0xFFEAF9F5),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(tab.key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tab.label, color = if (isSelected) Color.White else MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (count > 0) {
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(0.28f) else MintGreen.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$count", color = if (isSelected) Color.White else MintGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderInlineState(
    icon: ImageVector,
    title: String,
    description: String,
    loading: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                CircularProgressIndicator(color = MintGreen, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
            } else {
                Box(
                    modifier = Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFEAF9F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color(0xFF1A4A40), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(description, color = Color(0xFF8ACABA), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EmptyOrdersView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🛍️", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("Chưa có đơn hàng nào", color = Color(0xFF1A4A40), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Hãy mua sắm và quay lại đây!", color = Color(0xFF8ACABA), fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order Card — có phần đánh giá từng sản phẩm khi đơn "done"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OrderCard(
    order          : OrderEntity,
    myReviews      : List<ReviewEntity>,
    myReturn       : ReturnRequest?,
    onViewDetail   : () -> Unit,
    onCancel       : (() -> Unit)?,
    onReviewItem   : (OrderItem) -> Unit,
    onRequestReturn: () -> Unit
) {
    val info     = displayOrderStatus(order, myReturn)
    val statusKey = effectiveOrderStatusKey(order, myReturn)
    val items    = remember(order.itemsJson) { parseOrderItems(order.itemsJson) }
    val dateStr  = remember(order.createdAt) { SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(order.createdAt)) }
    var expanded by remember { mutableStateOf(false) }

    // Set productId đã được review trong đơn này
    val reviewedProductIds = remember(myReviews, order.id) {
        myReviews.filter { it.orderId == order.id }.map { it.productId }.toSet()
    }

    // Tự mở expand khi đơn done và còn sp chưa review
    val hasUnreviewed = statusKey == "done" && items.any { it.productId !in reviewedProductIds }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Mã đơn + trạng thái ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Đơn #${order.id.take(8).uppercase()}", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(dateStr, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(info.bgColor).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(orderStatusIcon(statusKey), contentDescription = null, tint = info.color, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(info.label, color = info.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Progress / cancelled ──────────────────────────────────────
            if (statusKey == "returned") {
                OrderProgressBar(currentStep = info.step, isReturned = true)
                Spacer(Modifier.height(8.dp))
                ReturnedOrderBanner(myReturn = myReturn)
            } else if (statusKey != "cancelled") {
                OrderProgressBar(currentStep = info.step)
            } else {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFECEC)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❌", fontSize = 14.sp); Spacer(Modifier.width(6.dp))
                        Text("Đơn hàng này đã bị hủy", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEAF9F5))

            // ── Người nhận ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = MintGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(order.receiverName, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.Phone, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(order.phoneNumber, color = Color(0xFF8ACABA), fontSize = 12.sp)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(13.dp).padding(top = 1.dp))
                Spacer(Modifier.width(3.dp))
                Text(order.address, color = Color(0xFF8ACABA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // ── Số sp + tổng tiền ─────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${items.size} sản phẩm", color = Color(0xFF8ACABA), fontSize = 12.sp)
                }
                Text("${"%,.0f".format(order.totalPrice)}đ", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // ── Banner nhắc đánh giá (chỉ khi done & còn sp chưa review) ─
            if (hasUnreviewed) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFFBE6))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hãy đánh giá sản phẩm!", color = Color(0xFF9A6D00), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Chia sẻ trải nghiệm của bạn với mọi người", color = Color(0xFFB8860B), fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Expand: danh sách sản phẩm + nút đánh giá ────────────────
            if (items.isNotEmpty()) {
                TextButton(
                    onClick        = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MintGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "Ẩn sản phẩm" else "Xem sản phẩm", color = MintGreen, fontSize = 12.sp)
                }

                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FFFE)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items.forEach { item ->
                            OrderItemRowWithReview(
                                item              = item,
                                isDone            = statusKey == "done",
                                alreadyReviewed   = item.productId in reviewedProductIds,
                                onReview          = { onReviewItem(item) },
                                myReview          = myReviews.firstOrNull { it.productId == item.productId && it.orderId == order.id }
                            )
                        }
                    }
                }
            }
            // ── Khu vực trả hàng — chỉ hiện khi đơn done ─────────────────
            if (statusKey == "done" || statusKey == "returned") {
                Spacer(Modifier.height(10.dp))
                if (statusKey == "done" && myReturn == null) {
                    OutlinedButton(
                        onClick  = onRequestReturn,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE8A44A)),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Icon(Icons.Default.AssignmentReturn, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Yêu cầu trả hàng", fontSize = 12.sp)
                    }
                } else {
                    val rInfo = myReturn?.let { getReturnStatus(it.status) } ?: getReturnStatus("completed")
                    val adminNote = myReturn?.adminNote.orEmpty()
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(rInfo.bgColor).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (statusKey == "returned") "Đơn hàng đã trả hàng" else "${rInfo.emoji} Yêu cầu trả hàng: ${rInfo.label}",
                            color = rInfo.color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (adminNote.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Phản hồi: $adminNote", color = rInfo.color.copy(0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEAF9F5))

            // ── Action buttons ────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick        = onViewDetail,
                    modifier       = Modifier.weight(1f).height(38.dp),
                    shape          = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors         = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                    border         = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chi tiết", fontSize = 12.sp)
                }

                if (onCancel != null) {
                    Box(
                        modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFECEC)).border(1.dp, Color(0xFFE57373).copy(0.35f), RoundedCornerShape(10.dp))
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cancel, null, tint = Color(0xFFE57373), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Hủy đơn", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(info.bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(info.label, color = info.color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnedOrderBanner(myReturn: ReturnRequest?) {
    val adminNote = myReturn?.adminNote.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0ECFF))
            .border(1.dp, Color(0xFF7B61D1).copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = Color(0xFF7B61D1), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Đã trả hàng", color = Color(0xFF7B61D1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (adminNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Phản hồi: $adminNote", color = Color(0xFF6D5BB7), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1 hàng sản phẩm trong đơn + nút / trạng thái đánh giá
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OrderItemRowWithReview(
    item            : OrderItem,
    isDone          : Boolean,
    alreadyReviewed : Boolean,
    onReview        : () -> Unit,
    myReview        : ReviewEntity?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(10.dp)
    ) {
        // Tên + giá
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProductOrderThumb(
                imageUrl = item.imageUrl,
                productName = item.name,
                modifier = Modifier.size(58.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.brandName.isNotEmpty()) Text(item.brandName, color = MintGreen, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("x${item.quantity}  ${"%,.0f".format(item.price * item.quantity)}đ", color = Color(0xFF4A7A70), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        // Phần review — chỉ khi đơn done
        if (isDone) {
            Spacer(Modifier.height(8.dp))
            if (alreadyReviewed && myReview != null) {
                // Đã review — hiển thị sao đã chọn + comment
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEAF9F5))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarRow(rating = myReview.rating, size = 14)
                        Spacer(Modifier.width(8.dp))
                        Text("Đánh giá của bạn", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (myReview.comment.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(myReview.comment, color = Color(0xFF4A7A70), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                // Chưa review — nút viết đánh giá
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(0xFFFFF8E1), Color(0xFFFFFBF0))
                            )
                        )
                        .border(1.dp, Color(0xFFFFD54F).copy(0.6f), RoundedCornerShape(8.dp))
                        .clickable { onReview() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.StarBorder, null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đánh giá sản phẩm này", color = Color(0xFF9A6D00), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress Bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProductOrderThumb(
    imageUrl: String,
    productName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF9F5)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Ảnh sản phẩm $productName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.ShoppingBag,
                contentDescription = null,
                tint = MintGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun OrderProgressBar(currentStep: Int, isReturned: Boolean = false) {
    val baseSteps = listOf(
        "Chờ\nxác nhận" to Icons.Default.PendingActions,
        "Đã\nxác nhận" to Icons.Default.Inventory2,
        "Đang\ngiao" to Icons.Default.LocalShipping,
        "Hoàn\nthành" to Icons.Default.CheckCircle
    )
    val steps = if (isReturned) baseSteps + ("Đã trả\nhàng" to Icons.Default.AssignmentReturn) else baseSteps
    val activeStep = if (isReturned) steps.lastIndex else currentStep.coerceIn(0, steps.lastIndex)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, (label, icon) ->
            val isDone    = index < activeStep
            val isCurrent = index == activeStep
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(
                        when {
                            isCurrent -> AppGradients.mintHorizontal
                            isDone    -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(MintGreen.copy(0.45f), MintGreen.copy(0.45f)))
                            else      -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFEAF9F5), Color(0xFFEAF9F5)))
                        }
                    ), contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isDone) Icons.Default.Check else icon,
                        null,
                        tint = if (isCurrent || isDone) Color.White else Color(0xFFAAD8CE),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, color = when { isCurrent -> MintGreen; isDone -> MintGreen.copy(0.55f); else -> Color(0xFFCCE8DF) },
                    fontSize = 9.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, lineHeight = 12.sp)
            }
            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(0.4f).padding(top = 14.dp).height(2.dp)
                    .background(if (index < activeStep) MintGreen.copy(0.45f) else Color(0xFFEAF9F5)))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order Detail Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OrderDetailDialog(
    order: OrderEntity,
    myReturn: ReturnRequest?,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)?
) {
    val info    = displayOrderStatus(order, myReturn)
    val statusKey = effectiveOrderStatusKey(order, myReturn)
    val items   = remember(order.itemsJson) { parseOrderItems(order.itemsJson) }
    val dateStr = remember(order.createdAt) { SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(Date(order.createdAt)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Chi tiết đơn hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("#${order.id.take(8).uppercase()}", color = Color(0xFFAAD8CE), fontSize = 12.sp) }
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(info.bgColor).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    orderStatusIcon(statusKey),
                                    contentDescription = null,
                                    tint = info.color,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(info.label, color = info.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp)); Text(info.desc, color = info.color.copy(0.8f), fontSize = 12.sp); Spacer(Modifier.height(14.dp))
                }
                if (statusKey == "returned") {
                    item {
                        OrderProgressBar(currentStep = info.step, isReturned = true)
                        Spacer(Modifier.height(10.dp))
                        ReturnedOrderBanner(myReturn = myReturn)
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (statusKey != "cancelled") {
                    item { OrderProgressBar(currentStep = info.step); Spacer(Modifier.height(16.dp)) }
                } else {
                    item { Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFECEC)).padding(12.dp)) { Text("❌  Đơn hàng này đã bị hủy", color = Color(0xFFE57373), fontSize = 13.sp, fontWeight = FontWeight.Medium) }; Spacer(Modifier.height(14.dp)) }
                }
                item {
                    DetailSectionLabel("Thông tin giao hàng")
                    DetailInfoRow(Icons.Default.Person,     "Người nhận",    order.receiverName)
                    DetailInfoRow(Icons.Default.Phone,      "Số điện thoại", order.phoneNumber)
                    DetailInfoRow(Icons.Default.LocationOn, "Địa chỉ",       order.address)
                    DetailInfoRow(Icons.Default.Payment,    "Thanh toán",    order.paymentMethod)
                    DetailInfoRow(Icons.Default.Schedule,   "Thời gian",     dateStr)
                    Spacer(Modifier.height(12.dp))
                }
                item { DetailSectionLabel("Sản phẩm (${items.size})") }
                items(items) { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductOrderThumb(
                            imageUrl = item.imageUrl,
                            productName = item.name,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.brandName.isNotEmpty()) Text(item.brandName, color = MintGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("x${item.quantity}", color = Color(0xFF8ACABA), fontSize = 12.sp)
                            Text("${"%,.0f".format(item.price * item.quantity)}đ", color = Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF9F5)).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tổng cộng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${"%,.0f".format(order.totalPrice)}đ", color = MintGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)) { Text("Đóng") }
                        if (onCancel != null) {
                            Box(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFECEC)).border(1.dp, Color(0xFFE57373).copy(0.35f), RoundedCornerShape(12.dp)).clickable { onCancel() }, contentAlignment = Alignment.Center) {
                                Text("Hủy đơn hàng", color = Color(0xFFE57373), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel Confirm Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CancelOrderDialog(order: OrderEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(20.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("😢", fontSize = 40.sp); Spacer(Modifier.height(8.dp))
                Text("Hủy đơn hàng?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40), fontSize = 17.sp)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Đơn #${order.id.take(8).uppercase()}", color = Color(0xFF8ACABA), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Bạn có chắc muốn hủy đơn hàng này?\nHành động này không thể hoàn tác.", color = Color(0xFF5A8A80), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cancel, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("Xác nhận hủy", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Giữ lại đơn", color = MintGreen, fontWeight = FontWeight.SemiBold) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Helpers
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DetailSectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(MintGreen))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Dialog yêu cầu trả hàng
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RequestReturnDialog(
    order     : OrderEntity,
    onDismiss : () -> Unit,
    onSubmit  : (reason: String, note: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(RETURN_REASONS.first()) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Yêu cầu trả hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Đơn #${order.id.take(8).uppercase()}", color = Color(0xFFAAD8CE), fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))

                Text("Lý do trả hàng", color = Color(0xFF8ACABA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                RETURN_REASONS.forEach { reason ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick  = { selectedReason = reason },
                            colors   = RadioButtonDefaults.colors(selectedColor = MintGreen)
                        )
                        Text(reason, color = Color(0xFF1A4A40), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = note,
                    onValueChange = { note = it },
                    placeholder   = { Text("Mô tả thêm (không bắt buộc)") },
                    modifier      = Modifier.fillMaxWidth().height(90.dp),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA)
                    )
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) { Text("Hủy") }
                    Button(
                        onClick  = { onSubmit(selectedReason, note) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = MintGreen)
                    ) { Text("Gửi yêu cầu", color = Color.White) }
                }
            }
        }
    }
}
@Composable
private fun DetailInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.weight(0.4f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp))
            Text(label, color = Color(0xFF8ACABA), fontSize = 12.sp)
        }
        Text(value, color = Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}