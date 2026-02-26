package com.example.temiguide.voice

import com.example.temiguide.core.AsrResult

interface SttProvider {
    fun startListening(language: String, prompt: String = "", onResult: (AsrResult) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun isListening(): Boolean
    fun supportedLanguages(): List<String>
}
