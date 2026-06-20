package com.example.appbanmypham

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.appbanmypham.model.AppRoles
import com.example.appbanmypham.ui.admin.DashboardActivity
import com.example.appbanmypham.ui.consultant.ConsultantDashboardActivity
import com.example.appbanmypham.ui.product.ProductActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            open(ProductActivity::class.java)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val role = try {
                withTimeout(5_000L) {
                    withContext(Dispatchers.IO) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.uid)
                            .get()
                            .await()
                    }.getLong("role")?.toInt() ?: AppRoles.CUSTOMER
                }
            } catch (_: Exception) {
                AppRoles.CUSTOMER
            }

            open(
                when (role) {
                    AppRoles.ADMIN -> DashboardActivity::class.java
                    AppRoles.CONSULTANT -> ConsultantDashboardActivity::class.java
                    else -> ProductActivity::class.java
                }
            )
        }
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }
}
