package com.example.temiguide.core

import android.util.Log

object TemiLog {
    var isDebug: Boolean = true  // release ビルドで false に

    fun d(message: String) {
        if (isDebug) Log.d(AppConstants.LOG_TAG, message)
    }

    fun w(message: String) {
        Log.w(AppConstants.LOG_TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(AppConstants.LOG_TAG, message, throwable)
    }
}
