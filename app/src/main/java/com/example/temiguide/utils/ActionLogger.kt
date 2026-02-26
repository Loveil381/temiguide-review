package com.example.temiguide.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActionLogger {
    fun logAction(context: Context, userText: String, aiReply: String, location: String?, action: String?, result: String) {
        try {
            val file = File(context.getExternalFilesDir(null), "guide_log.csv")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val writer = FileWriter(file, true)
            writer.append("$timestamp,\"$userText\",\"$aiReply\",\"$location\",\"$action\",\"$result\"\n")
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            Log.e("TemiGuide", "Log Error: ${e.message}")
        }
    }
}
