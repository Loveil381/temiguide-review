package com.example.temiguide.voice

data class LiveVoiceConfig(val apiKey: String, val sampleRate: Int)

data class TemiToolCall(val name: String, val arguments: Map<String, Any>)

data class AiResponse(val replyText: String?, val toolCalls: List<TemiToolCall>?)

data class FunctionDeclaration(val name: String, val description: String)

interface LiveVoiceProvider {
    suspend fun connect(config: LiveVoiceConfig)
    suspend fun disconnect()
    fun isConnected(): Boolean
    
    fun onTranscript(callback: (String) -> Unit)
    fun onAiResponse(callback: (AiResponse) -> Unit)
    fun onAudioOutput(callback: (ByteArray) -> Unit)
    
    fun sendAudio(audioData: ByteArray)
    fun sendText(text: String)
    fun setSystemInstruction(instruction: String)
    fun setFunctions(functions: List<FunctionDeclaration>)
}
