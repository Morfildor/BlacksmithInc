package com.example.blacksmithproject

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.blacksmithproject.ui.TinyBlacksmithApp
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels { GameViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds only: `adb shell am start -n <pkg>/.MainActivity --el seed 42` starts a fixed-seed run when none is saved.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && savedInstanceState == null && intent.hasExtra(EXTRA_SEED)) vm.startSeededRun(intent.getLongExtra(EXTRA_SEED, 0L))
        setContent {
            BlacksmithProjectTheme {
                TinyBlacksmithApp(vm)
            }
        }
    }

    private companion object {
        const val EXTRA_SEED = "seed"
    }
}
