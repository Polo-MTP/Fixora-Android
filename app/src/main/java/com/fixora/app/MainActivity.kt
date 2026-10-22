package com.fixora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.navigation.FixoraNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixoraTheme {
                FixoraNavHost()
            }
        }
    }
}
