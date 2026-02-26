package com.example.temiguide.voice

import com.example.temiguide.robot.RobotController
import com.example.temiguide.voice.temi.TemiSttProvider
import com.example.temiguide.voice.temi.TemiTtsProvider

import com.example.temiguide.core.VoiceMode

class VoiceProviderFactory(private val robotController: RobotController) {
    fun getSttProvider(mode: VoiceMode): SttProvider? = when (mode) {
        VoiceMode.TEMI_BUILTIN -> TemiSttProvider(robotController)
        else -> null 
    }

    fun getTtsProvider(mode: VoiceMode): TtsProvider? = when (mode) {
        VoiceMode.TEMI_BUILTIN -> TemiTtsProvider(robotController)
        else -> null
    }

    fun getLiveProvider(mode: VoiceMode): LiveVoiceProvider? = when (mode) {
        else -> null
    }
}
