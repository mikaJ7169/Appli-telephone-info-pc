package dev.vibe.pcmon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.vibe.pcmon.theme.PcmonTheme
import dev.vibe.pcmon.ui.PcmonApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PcmonTheme {
                PcmonApp()
            }
        }
    }
}
