package com.example.temiguide.voice

data class VoiceInfo(val id: String, val name: String, val language: String, val gender: String)

interface TtsProvider {
    /**
     * @return true if speech completed successfully, false if cancelled/error
     */
    suspend fun speak(text: String, language: String = "ja-JP"): Boolean
    suspend fun speakInChunks(text: String, language: String = "ja-JP")
    fun stop()
    fun isSpeaking(): Boolean
    fun setVoice(voiceId: String)
    fun availableVoices(): List<VoiceInfo>
}
