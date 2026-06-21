package com.example.appbanmypham.ui.checkout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.Order
import com.example.appbanmypham.model.OrderEntity
import com.example.appbanmypham.model.OrderItem
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.BackgroundPrimary
import com.example.appbanmypham.ui.theme.MintGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

private const val PAYMENT_COD = "COD"
private const val PAYMENT_VIETQR = "VIETQR"
private const val VIETQR_BANK_ID = "MB"
private const val VIETQR_ACCOUNT_NO = "0779469148"
private const val VIETQR_ACCOUNT_NAME = "NGUYEN PHAN NHU HOANG"

class CheckoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val totalPrice = intent.getDoubleExtra("total_price", 0.0)
        val ids = intent.getStringArrayExtra("item_ids") ?: emptyArray()
        val names = intent.getStringArrayExtra("item_names") ?: emptyArray()
        val brands = intent.getStringArrayExtra("item_brands") ?: emptyArray()
        val images = intent.getStringArrayExtra("item_images") ?: emptyArray()
        val prices = intent.getDoubleArrayExtra("item_prices") ?: DoubleArray(0)
        val quantities = intent.getIntArrayExtra("item_quantities") ?: IntArray(0)

        val orderItems = ids.mapIndexed { i, id ->
            OrderItem(
                productId = id,
                name = names.getOrElse(i) { "" },
                brandName = brands.getOrElse(i) { "" },
                imageUrl = images.getOrElse(i) { "" },
                price = prices.getOrElse(i) { 0.0 },
                quantity = quantities.getOrElse(i) { 1 }
            )
        }

        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    CheckoutScreen(
                        orderItems = orderItems,
                        totalPrice = totalPrice,
                        onBack = { finish() },
                        onSuccess = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@CheckoutScreen
                            val db = FirebaseFirestore.getInstance()
                            db.collection("carts").document(uid).collection("items").get()
                                .addOnSuccessListener { snap ->
                                    snap.documents.forEach { it.reference.delete() }
                                }
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CheckoutScreen(
    orderItems: List<OrderItem>,
    totalPrice: Double,
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var receiverName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PAYMENT_COD) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val paymentCode = remember {
        "DH" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
    }
    val vietQrUrl = remember(totalPrice, paymentCode) {
        buildVietQrUrl(totalPrice = totalPrice, transferContent = paymentCode)
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("OK", fontSize = 32.sp, color = MintGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dat hang thanh cong!",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A4A40),
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    "Don hang da duoc ghi nhan.\nChung toi se lien he som nhat!",
                    color = Color(0xFF5A8A80),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onSuccess() },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ve trang chu", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text(
                    "Xac nhan dat hang",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Thong tin giao hang",
                        color = MintGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = receiverName,
                        onValueChange = { receiverName = it },
                        label = { Text("Ho va ten nguoi nhan") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = MintGreen) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = mintTextFieldColors()
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("So dien thoai") },
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = MintGreen) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = mintTextFieldColors()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dia chi giao hang") },
                        leadingIcon = { Icon(Icons.Default.Place, null, tint = MintGreen) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = mintTextFieldColors()
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Phuong thuc thanh toan",
                        color = MintGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    PaymentOptionRow(
                        title = "Thanh toan khi nhan hang (COD)",
                        subtitle = "Tra tien mat khi nhan hang",
                        icon = Icons.Default.LocalShipping,
                        selected = paymentMethod == PAYMENT_COD,
                        onClick = { paymentMethod = PAYMENT_COD }
                    )
                    PaymentOptionRow(
                        title = "Chuyen khoan QR MB Bank",
                        subtitle = "Quet ma QR dung so tien hoa don",
                        icon = Icons.Default.QrCode2,
                        selected = paymentMethod == PAYMENT_VIETQR,
                        onClick = { paymentMethod = PAYMENT_VIETQR }
                    )
                    if (paymentMethod == PAYMENT_VIETQR) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF8FFFD))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = vietQrUrl,
                                contentDescription = "QR thanh toan VietQR",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(220.dp)
                            )
                            Text(
                                "MB Bank - $VIETQR_ACCOUNT_NO",
                                color = Color(0xFF1A4A40),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(VIETQR_ACCOUNT_NAME, color = Color(0xFF5A8A80), fontSize = 13.sp)
                            Text(
                                "So tien: ${"%,.0f".format(totalPrice)}d",
                                color = MintGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Noi dung CK: $paymentCode",
                                color = Color(0xFF1A4A40),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "Sau khi chuyen khoan, bam xac nhan dat hang. Shop se doi chieu noi dung chuyen khoan truoc khi xu ly don.",
                                color = Color(0xFF8ACABA),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tom tat don hang", color = MintGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    orderItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${item.name} x${item.quantity}",
                                color = Color(0xFF1A4A40),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${"%,.0f".format(item.price * item.quantity)}d",
                                color = Color(0xFF1A4A40),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEAF9F5))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tong cong", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "${"%,.0f".format(totalPrice)}d",
                            color = MintGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = Color(0xFFE57373), fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (receiverName.isBlank()) {
                        errorMsg = "Vui long nhap ten nguoi nhan"
                        return@Button
                    }
                    if (phoneNumber.isBlank()) {
                        errorMsg = "Vui long nhap so dien thoai"
                        return@Button
                    }
                    if (address.isBlank()) {
                        errorMsg = "Vui long nhap dia chi giao hang"
                        return@Button
                    }

                    errorMsg = ""
                    isLoading = true

                    val uid = auth.currentUser?.uid ?: run {
                        isLoading = false
                        errorMsg = "Vui long dang nhap de dat hang"
                        return@Button
                    }
                    val orderId = UUID.randomUUID().toString()
                    val finalPaymentMethod = if (paymentMethod == PAYMENT_VIETQR) {
                        "VietQR - $paymentCode"
                    } else {
                        PAYMENT_COD
                    }

                    val order = Order(
                        id = orderId,
                        userId = uid,
                        items = orderItems,
                        totalPrice = totalPrice,
                        address = address,
                        phoneNumber = phoneNumber,
                        receiverName = receiverName,
                        status = "pending",
                        paymentMethod = finalPaymentMethod,
                        createdAt = System.currentTimeMillis()
                    )

                    db.collection("orders").document(orderId)
                        .set(order)
                        .addOnSuccessListener {
                            val itemsJson = JSONArray().apply {
                                orderItems.forEach { item ->
                                    put(JSONObject().apply {
                                        put("productId", item.productId)
                                        put("name", item.name)
                                        put("brandName", item.brandName)
                                        put("imageUrl", item.imageUrl)
                                        put("price", item.price)
                                        put("quantity", item.quantity)
                                    })
                                }
                            }.toString()

                            val entity = OrderEntity(
                                id = orderId,
                                userId = uid,
                                totalPrice = totalPrice,
                                address = address,
                                phoneNumber = phoneNumber,
                                receiverName = receiverName,
                                status = "pending",
                                paymentMethod = finalPaymentMethod,
                                itemsJson = itemsJson,
                                createdAt = System.currentTimeMillis()
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                AppDatabase.getInstance(context).orderDao().insert(entity)
                            }

                            // ✅ TRỪ KHO cho từng sản phẩm trong đơn (dùng transaction để tránh bán âm
                            //    khi nhiều người đặt mua cùng lúc). Vì admin đọc trực tiếp từ Firestore
                            //    real-time, sửa đúng ở đây thì trang Quản lý sản phẩm (admin) cũng tự
                            //    động giảm theo, không cần sửa gì thêm ở ManageProductActivity.
                            orderItems.forEach { item ->
                                val productRef = db.collection("products").document(item.productId)
                                db.runTransaction { tr ->
                                    val snap = tr.get(productRef)
                                    val currentStock = (snap.getLong("stock") ?: 0L).toInt()
                                    val newStock = (currentStock - item.quantity).coerceAtLeast(0)
                                    tr.update(productRef, "stock", newStock)
                                    newStock
                                }.addOnSuccessListener { newStock ->
                                    // Đồng bộ Room để admin/màn sản phẩm vẫn đúng dữ liệu khi offline
                                    CoroutineScope(Dispatchers.IO).launch {
                                        AppDatabase.getInstance(context).productDao()
                                            .updateStock(item.productId, newStock)
                                    }
                                }.addOnFailureListener {
                                    // Không chặn luồng đặt hàng nếu trừ kho lỗi (đơn đã tạo thành công),
                                    // nhưng nên log lại để theo dõi.
                                }
                            }

                            isLoading = false
                            showSuccess = true
                        }
                        .addOnFailureListener {
                            isLoading = false
                            errorMsg = "Dat hang that bai: ${it.message}"
                        }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Xac nhan dat hang",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaymentOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFEAF9F5) else Color(0xFFF9FBFA))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) MintGreen else Color(0xFF8ACABA), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF8ACABA), fontSize = 12.sp)
        }
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MintGreen))
    }
}

private fun buildVietQrUrl(totalPrice: Double, transferContent: String): String {
    val amount = totalPrice.toLong().coerceAtLeast(0L)
    val content = urlEncode(transferContent)
    val accountName = urlEncode(VIETQR_ACCOUNT_NAME)
    return "https://img.vietqr.io/image/$VIETQR_BANK_ID-$VIETQR_ACCOUNT_NO-compact2.png" +
            "?amount=$amount&addInfo=$content&accountName=$accountName"
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

@Composable
private fun mintTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MintGreen,
    unfocusedBorderColor = Color(0xFFCCEEE6),
    focusedLabelColor = MintGreen,
    unfocusedLabelColor = Color(0xFF8ACABA),
    cursorColor = MintGreen
)
