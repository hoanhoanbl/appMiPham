package com.example.appbanmypham

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.appbanmypham.ui.product.ProductActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chuyển thẳng sang ProductActivity, không giữ MainActivity trong stack
        startActivity(Intent(this, ProductActivity::class.java))
        finish()
    }
}
 