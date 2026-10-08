package com.example.blacksmithproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.blacksmithproject.ui.TinyBlacksmithApp
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlacksmithProjectTheme {
                TinyBlacksmithApp(vm)
            }
        }
    }
}
