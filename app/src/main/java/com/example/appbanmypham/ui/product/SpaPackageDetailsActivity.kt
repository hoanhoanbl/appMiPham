package com.example.appbanmypham.ui.product

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.model.ProgressPhotoPolicy
import com.example.appbanmypham.model.SpaPackage
import com.example.appbanmypham.model.SpaPackageType
import com.example.appbanmypham.model.firestoreDocToSpaPackage
import com.example.appbanmypham.model.progressPhotoPolicyMeta
import com.example.appbanmypham.model.spaPackageTypeMeta
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SpaPackageDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageId = intent.getStringExtra("spa_package_id") ?: run { finish(); return }
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    SpaPackageDetailsScreen(
                        packageId = packageId,
                        onBack = { finish() },
                        onBook = {
                            val target = if (FirebaseAuth.getInstance().currentUser == null) {
                                Intent(this, LoginActivity::class.java)
                                    .putExtra("redirect_to_spa_booking", true)
                                    .putExtra("spa_package_id", packageId)
                            } else {
                                Intent(this, BookSpaAppointmentActivity::class.java)
                                    .putExtra("spa_package_id", packageId)
                            }
                            startActivity(target)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpaPackageDetailsScreen(packageId: String, onBack: () -> Unit = {}, onBook: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    var item by remember { mutableStateOf<SpaPackage?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(packageId) {
        db.collection("spa_packages").document(packageId).get()
            .addOnSuccessListener { doc ->
                val parsed = runCatching { firestoreDocToSpaPackage(doc) }.getOrNull()
                item = parsed?.takeIf { it.isActive }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Quay lai", tint = Color.White)
                }
                Text(
                    "Chi tiet goi spa",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        bottomBar = {
            item?.let {
                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gia goi", color = Color(0xFF8ACABA), fontSize = 11.sp)
                            Text("${"%,.0f".format(it.price)}d", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Button(
                            onClick = onBook,
                            enabled = true,
                            modifier = Modifier.height(50.dp).weight(1.4f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dat lich ngay", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }
            item == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Khong tim thay goi spa", color = Color(0xFF8ACABA), fontSize = 15.sp)
                }
            }
            else -> {
                val spa = item!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFFEAF9F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (spa.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = spa.imageUrl,
                                contentDescription = spa.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Spa, contentDescription = null, tint = MintGreen, modifier = Modifier.size(82.dp))
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChipText(spa.category.ifBlank { "Spa" })
                            Spacer(Modifier.width(8.dp))
                            ChipText(spaPackageTypeMeta(spa.packageType).label)
                            Spacer(Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF8ACABA), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${spa.durationMinutes} phut", color = Color(0xFF8ACABA), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(spa.name, color = Color(0xFF1A4A40), fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                        if (spa.shortDescription.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(spa.shortDescription, color = Color(0xFF5A8A80), fontSize = 14.sp, lineHeight = 21.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${"%,.0f".format(spa.price)}d", color = MintGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (spa.originalPrice > spa.price) {
                                Spacer(Modifier.width(10.dp))
                                Text("${"%,.0f".format(spa.originalPrice)}d", color = Color(0xFFAAD8CE), fontSize = 13.sp)
                            }
                        }
                    }

                    TreatmentDisclosureSection(spa)
                    DetailTextSection("Mo ta", spa.description)
                    DetailListSection("Loi ich", spa.benefits, Icons.Default.CheckCircle)
                    DetailListSection("Quy trinh", spa.steps, Icons.Default.Spa)
                    DetailListSection("Phu hop voi", spa.suitableFor, Icons.Default.FaceRetouchingNatural)

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun TreatmentDisclosureSection(spa: SpaPackage) {
    if (spa.packageType != SpaPackageType.TREATMENT_TEMPLATE) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White).padding(20.dp)) {
        SectionTitle("Thong tin lieu trinh")
        Spacer(Modifier.height(10.dp))
        TreatmentInfoRow(Icons.Default.EventAvailable, "${spa.sessionCount} buoi dieu tri", "So buoi co dinh theo goi spa admin da thiet lap")
        TreatmentInfoRow(Icons.Default.Schedule, "${spa.durationPerSessionMinutes.takeIf { it > 0 } ?: spa.durationMinutes} phut / buoi", "Cach nhau khoang ${spa.suggestedIntervalDays} ngay theo goi y")
        if (spa.requiresProgressPhotos && spa.photoPolicy != ProgressPhotoPolicy.NONE) {
            TreatmentInfoRow(Icons.Default.PhotoCamera, progressPhotoPolicyMeta(spa.photoPolicy).label, spa.photoGuide.ifBlank { "Tu van vien se cap nhat anh tien trinh cho khach xem." })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Khi dat goi nay, he thong tao san lieu trinh ${spa.sessionCount} buoi. Ban dat truoc buoi dau tien, cac buoi tiep theo se hien trong Lieu trinh cua toi de ban chon lich phu hop.",
            color = Color(0xFF5A8A80),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun TreatmentInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFEAF9F5)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color(0xFF6C8F87), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ChipText(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEAF9F5))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailTextSection(title: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White).padding(20.dp)) {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(value, color = Color(0xFF4A7A70), fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun DetailListSection(title: String, values: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    if (values.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White).padding(20.dp)) {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        values.forEachIndexed { index, value ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFEAF9F5)),
                    contentAlignment = Alignment.Center
                ) {
                    if (title == "Quy trinh") {
                        Text("${index + 1}", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(icon, contentDescription = null, tint = MintGreen, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(value, color = Color(0xFF4A7A70), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(MintGreen))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
