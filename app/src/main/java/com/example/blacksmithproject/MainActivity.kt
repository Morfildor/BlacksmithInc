package com.example.blacksmithproject

import android.content.Intent
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
        if (debuggable) debugSql(intent)      // before the ViewModel is created, so the first load sees the edited rows
        if (debuggable && savedInstanceState == null && intent.hasExtra(EXTRA_SEED)) vm.startSeededRun(intent.getLongExtra(EXTRA_SEED, 0L))
        setContent {
            BlacksmithProjectTheme {
                TinyBlacksmithApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) debugSql(intent)
    }

    /**
     * Debug builds only, for the recovery checks on a device without a sqlite3 binary: `--es debug_sql "<statement>"`
     * runs one statement on the save database (damage a row, put it back). A release build never reads the extra.
     */
    private fun debugSql(intent: Intent) {
        val sql = intent.getStringExtra(EXTRA_DEBUG_SQL) ?: return
        openOrCreateDatabase("tiny_blacksmith.db", MODE_PRIVATE, null).use { it.execSQL(sql) }
    }

    private companion object {
        const val EXTRA_SEED = "seed"
        const val EXTRA_DEBUG_SQL = "debug_sql"
    }
}
