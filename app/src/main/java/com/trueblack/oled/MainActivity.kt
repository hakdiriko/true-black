package com.trueblack.oled

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trueblack.oled.ui.MainScreen
import com.trueblack.oled.ui.TrueBlackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            TrueBlackTheme { MainScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        Engine.refreshAccess()
        // Cheap insurance: if the system replaced the compositor matrix meanwhile, put ours back.
        if (Engine.settings.value.enabled) Engine.reapplyAsync()
    }
}
