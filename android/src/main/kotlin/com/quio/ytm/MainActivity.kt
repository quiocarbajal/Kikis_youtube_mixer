package com.quio.ytm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.quio.ytm.core.CoreInfo
import com.quio.ytm.ui.theme.BgMain
import com.quio.ytm.ui.theme.YouTubeMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeMusicPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgMain
                ) {
                    Text(text = "YouTube Music Pocket Player - Core v${CoreInfo.VERSION}")
                }
            }
        }
    }
}
