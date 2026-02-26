package com.example.temiguide.persona

import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random

class BehaviorScheduler(private val config: PersonaConfig) {

    fun getCurrentEnergy(hour: Int): Float {
        // Find nearest hour
        var nearestHour = -1
        var minDiff = Int.MAX_VALUE
        for (h in config.energyByHour.keys) {
            val diff = abs(h - hour)
            if (diff < minDiff) {
                minDiff = diff
                nearestHour = h
            }
        }
        
        if (nearestHour == -1) return 0.5f
        return config.energyByHour[nearestHour] ?: 0.5f
    }

    fun getNextIdleBehavior(): IdleBehavior? {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val energy = getCurrentEnergy(currentHour)

        // Energy dictates probability of an idle behavior
        if (Random.nextFloat() > energy) {
            return null // Not active enough right now
        }

        if (config.idleBehaviors.isEmpty()) return null
        return config.idleBehaviors.random()
    }

    fun shouldInitiateApproach(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val energy = getCurrentEnergy(currentHour)
        return Random.nextFloat() <= energy // More likely to approach if energetic
    }
}
