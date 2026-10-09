package com.example.blacksmithproject

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only (this source set is not part of a release build). For the recovery checks on a device without a
 * sqlite3 binary: runs one statement on the save database, then finishes without showing anything.
 *
 *     adb shell am force-stop com.example.blacksmithproject
 *     adb shell am start -n com.example.blacksmithproject/.DebugSqlActivity --es debug_sql "<statement>"
 *     adb shell am start -n com.example.blacksmithproject/.MainActivity
 *
 * Stop the game first: a running process holds the database open and its in-memory state would not see the edit.
 */
class DebugSqlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("debug_sql")?.let { sql -> openOrCreateDatabase("tiny_blacksmith.db", MODE_PRIVATE, null).use { it.execSQL(sql) } }
        finish()
    }
}
