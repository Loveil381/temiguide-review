package com.example.temiguide.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interaction_logs")
data class InteractionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userInput: String,
    val aiResponse: String?,
    val toolsExecuted: String?,  // comma separated string
    val latencyMs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)
