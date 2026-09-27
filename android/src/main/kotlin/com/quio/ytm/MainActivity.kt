package com.quio.ytm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.quio.ytm.core.CoreInfo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text(text = "YouTube Music Pocket Player - Core v${CoreInfo.VERSION}")
        }
    }
}
