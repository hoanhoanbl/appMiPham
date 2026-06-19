package com.example.appbanmypham.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    RegisterScreen(
                        onBack = { finish() },
                        onRegisterSuccess = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onBack: () -> Unit = {},
    onRegisterSuccess: () -> Unit = {}
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw    by remember { mutableStateOf(false) }
    var showCPw   by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    val emailError   = email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val pwError      = password.isNotEmpty() && password.length < 6
    val confirmError = confirmPw.isNotEmpty() && confirmPw != password
    val isValid      = email.isNotEmpty() && password.length >= 6
            && confirmPw == password && !emailError

    fun doRegister() {
        isLoading = true
        errorMsg = ""
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener

                // Lưu thông tin user vào Firestore với role = 0
                val db = FirebaseFirestore.getInstance()
                val userMap = hashMapOf(
                    "uid"   to uid,
                    "email" to email.trim(),
                    "role"  to 0,        // 0 = khách hàng, 1 = admin
                    "createdAt" to System.currentTimeMillis()
                )

                db.collection("users").document(uid)
                    .set(userMap)
                    .addOnSuccessListener {
                        isLoading = false
                        onRegisterSuccess()
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMsg = "Tạo tài khoản thất bại: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMsg = when {
                    e.message?.contains("already in use") == true  -> "Email đã được sử dụng"
                    e.message?.contains("badly formatted") == true -> "Email không hợp lệ"
                    e.message?.contains("network error") == true   -> "Lỗi kết nối mạng"
                    else                                            -> "Đăng ký thất bại"
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero Section ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(brush = AppGradients.mintHorizontal)
                .padding(horizontal = 28.dp, vertical = 28.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }

            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌿", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LUMIÈRE",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Tạo tài khoản",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "của bạn",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tham gia cộng đồng làm đẹp hôm nay",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp
                )
            }
        }

        // ── Form Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(BackgroundPrimary)
                .padding(horizontal = 28.dp, vertical = 28.dp)
        ) {
            Text(
                text = "ĐĂNG KÝ TÀI KHOẢN",
                color = MintGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Email
            RegisterFieldLabel("Email")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("yourname@email.com", color = Color(0xFFAAD8CE)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = emailError,
                supportingText = {
                    if (emailError) Text("Email không hợp lệ", color = Color(0xFFF09595), fontSize = 11.sp)
                },
                colors = mintTextFieldColors()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mật khẩu
            RegisterFieldLabel("Mật khẩu")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tối thiểu 6 ký tự", color = Color(0xFFAAD8CE)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = pwError,
                supportingText = {
                    if (pwError) Text("Mật khẩu phải có ít nhất 6 ký tự", color = Color(0xFFF09595), fontSize = 11.sp)
                },
                visualTransformation = if (showPw) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            imageVector = if (showPw) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFAAD8CE)
                        )
                    }
                },
                colors = mintTextFieldColors()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Xác nhận mật khẩu
            RegisterFieldLabel("Xác nhận mật khẩu")
            OutlinedTextField(
                value = confirmPw,
                onValueChange = { confirmPw = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nhập lại mật khẩu", color = Color(0xFFAAD8CE)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = confirmError,
                supportingText = {
                    if (confirmError) Text("Mật khẩu không khớp", color = Color(0xFFF09595), fontSize = 11.sp)
                },
                visualTransformation = if (showCPw) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCPw = !showCPw }) {
                        Icon(
                            imageVector = if (showCPw) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFAAD8CE)
                        )
                    }
                },
                colors = mintTextFieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Lỗi Firebase
            if (errorMsg.isNotEmpty()) {
                Text(
                    text = errorMsg,
                    color = Color(0xFFF09595),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Nút Tạo tài khoản
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = if (isValid && !isLoading) AppGradients.mintHorizontal
                        else androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(Color(0xFFB2E8DA), Color(0xFFD0F2EC))
                        )
                    )
                    .clickable(enabled = isValid && !isLoading) { doRegister() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Tạo tài khoản",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Đã có tài khoản? ", fontSize = 12.sp, color = Color(0xFF8ACABA))
                Text(
                    text = "Đăng nhập",
                    fontSize = 12.sp,
                    color = MintGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }
    }
}

@Composable
private fun RegisterFieldLabel(text: String) {
    Text(
        text = text,
        color = MintGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun mintTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor     = Color(0xFF1A4A40),
    unfocusedTextColor   = Color(0xFF1A4A40),
    cursorColor          = MintGreen,
    errorBorderColor     = Color(0xFFF09595),
    errorCursorColor     = Color(0xFFF09595)
)

@Preview(showBackground = true)
@Composable
fun RegisterPreview() {
    AppBanMyPhamTheme {
        RegisterScreen()
    }
}