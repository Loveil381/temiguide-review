package com.example.temiguide.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.example.temiguide.R

class AnimationManager(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())

    private var catAvdCallback: Animatable2.AnimationCallback? = null

    private val idleAnimators = mutableListOf<ValueAnimator>()
    private val greetingAnimators = mutableListOf<ValueAnimator>()
    private val thinkingAnimators = mutableListOf<ValueAnimator>()
    private val listeningAnimators = mutableListOf<ValueAnimator>()
    private val guidanceAnimators = mutableListOf<ValueAnimator>()
    private val navAnimators = mutableListOf<ValueAnimator>()
    private val arrivalAnimators = mutableListOf<ValueAnimator>()
    private val staffAnimators = mutableListOf<ValueAnimator>()

    // ======== Views ========
    private val tvGreetingClock by lazy { activity.findViewById<TextView>(R.id.tvGreetingClock) }
    private val waveBars by lazy {
        listOf<View>(
            activity.findViewById(R.id.waveBar1), activity.findViewById(R.id.waveBar2),
            activity.findViewById(R.id.waveBar3), activity.findViewById(R.id.waveBar4),
            activity.findViewById(R.id.waveBar5), activity.findViewById(R.id.waveBar6),
            activity.findViewById(R.id.waveBar7), activity.findViewById(R.id.waveBar8),
            activity.findViewById(R.id.waveBar9), activity.findViewById(R.id.waveBar10),
            activity.findViewById(R.id.waveBar11), activity.findViewById(R.id.waveBar12)
        )
    }

    private val ivCatConcierge by lazy { activity.findViewById<ImageView>(R.id.ivCatConcierge) }
    private val viewIdleBgGlow by lazy { activity.findViewById<View>(R.id.viewIdleBgGlow) }
    private val idleHalo1 by lazy { activity.findViewById<View>(R.id.idleHalo1) }
    private val idleHalo2 by lazy { activity.findViewById<View>(R.id.idleHalo2) }
    private val idleHalo3 by lazy { activity.findViewById<View>(R.id.idleHalo3) }
    private val idleRobotContainer by lazy { activity.findViewById<View>(R.id.idleRobotContainer) }
    private val llIdleCta by lazy { activity.findViewById<View>(R.id.llIdleCta) }

    private val listeningRing1 by lazy { activity.findViewById<View>(R.id.listeningRing1) }
    private val listeningRing2 by lazy { activity.findViewById<View>(R.id.listeningRing2) }
    private val listeningRing3 by lazy { activity.findViewById<View>(R.id.listeningRing3) }
    private val ivListeningMicIcon by lazy { activity.findViewById<ImageView>(R.id.ivListeningMicIcon) }

    private val thinkingCore by lazy { activity.findViewById<View>(R.id.thinkingCore) }
    private val thinkingHalo by lazy { activity.findViewById<View>(R.id.thinkingHalo) }
    private val thinkingRipple1 by lazy { activity.findViewById<View>(R.id.thinkingRipple1) }

    private val tvGuidanceTitle by lazy { activity.findViewById<TextView>(R.id.tvGuidanceTitle) }
    private val tvGuidanceSubtitle by lazy { activity.findViewById<TextView>(R.id.tvGuidanceSubtitle) }

    private val ivNavSync by lazy { activity.findViewById<ImageView>(R.id.ivNavSync) }

    private val viewArrivalCheck by lazy { activity.findViewById<View>(R.id.viewArrivalCheck) }
    private val tvArrivalTitle by lazy { activity.findViewById<TextView>(R.id.tvArrivalTitle) }

    private val ivStaffSync by lazy { activity.findViewById<ImageView>(R.id.ivStaffSync) }
    private val staffRipple1 by lazy { activity.findViewById<View>(R.id.staffRipple1) }
    private val staffRipple2 by lazy { activity.findViewById<View>(R.id.staffRipple2) }

    fun startGreetingAnimation() {
        greetingAnimators.clear()
        tvGreetingClock.translationY = 30f
        tvGreetingClock.alpha = 0f
        tvGreetingClock.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100).start()
        
        waveBars.forEachIndexed { _, bar ->
            val delay = (Math.random() * 200).toLong()
            val anim = ObjectAnimator.ofFloat(bar, "scaleY", 1f, 1.8f, 0.8f, 1f).apply {
                duration = 800L + (Math.random() * 400).toLong()
                startDelay = delay
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
            greetingAnimators.add(anim)
        }
    }

    fun startGuidanceAnimation() {
        guidanceAnimators.clear()
        tvGuidanceTitle.translationY = 50f
        tvGuidanceTitle.alpha = 0f
        tvGuidanceTitle.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100)
            .setInterpolator(android.view.animation.OvershootInterpolator()).start()
            
        tvGuidanceSubtitle.translationY = 30f
        tvGuidanceSubtitle.alpha = 0f
        tvGuidanceSubtitle.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(200).start()
    }

    fun startNavigationAnimation() {
        navAnimators.clear()
        val rotationAnim = ObjectAnimator.ofFloat(ivNavSync, "rotation", 0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        navAnimators.add(rotationAnim)
    }

    fun startArrivalAnimation() {
        arrivalAnimators.clear()
        viewArrivalCheck.scaleX = 0f
        viewArrivalCheck.scaleY = 0f
        viewArrivalCheck.animate().scaleX(1f).scaleY(1f).setDuration(600).setStartDelay(100)
            .setInterpolator(android.view.animation.OvershootInterpolator()).start()
            
        tvArrivalTitle.translationY = 30f
        tvArrivalTitle.alpha = 0f
        tvArrivalTitle.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(200).start()
    }

    fun startThinkingAnimation() {
        thinkingAnimators.clear()
        // Core breathing
        val coreAnim = ValueAnimator.ofFloat(1f, 1.15f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                thinkingCore.scaleX = s
                thinkingCore.scaleY = s
            }
            start()
        }
        thinkingAnimators.add(coreAnim)

        // Halo breathing
        val haloAnim = ValueAnimator.ofFloat(1f, 1.2f).apply {
            duration = 1800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                thinkingHalo.scaleX = s
                thinkingHalo.scaleY = s
                thinkingHalo.alpha = 0.6f - (s - 1f) * 2f
            }
            start()
        }
        thinkingAnimators.add(haloAnim)

        // Ripple expand
        val rippleAnim = ValueAnimator.ofFloat(1f, 2.5f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                thinkingRipple1.scaleX = s
                thinkingRipple1.scaleY = s
                thinkingRipple1.alpha = 0.5f * (1f - (s - 1f) / 1.5f)
            }
            start()
        }
        thinkingAnimators.add(rippleAnim)
    }

    fun startListeningAnimation() {
        listeningAnimators.clear()
        val rings = listOf(listeningRing1, listeningRing2, listeningRing3)
        rings.forEachIndexed { i, ring ->
            val anim = ValueAnimator.ofFloat(1f, 1.3f - i * 0.05f).apply {
                duration = (2000 + i * 400).toLong()
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val s = it.animatedValue as Float
                    ring.scaleX = s
                    ring.scaleY = s
                }
                start()
            }
            listeningAnimators.add(anim)
        }
        
        val micAnimX = ObjectAnimator.ofFloat(ivListeningMicIcon, "scaleX", 1f, 1.15f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        val micAnimY = ObjectAnimator.ofFloat(ivListeningMicIcon, "scaleY", 1f, 1.15f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        listeningAnimators.add(micAnimX)
        listeningAnimators.add(micAnimY)
    }

    fun startStaffAnimation() {
        staffAnimators.clear()
        
        val syncAnim = ObjectAnimator.ofFloat(ivStaffSync, "rotation", 0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        staffAnimators.add(syncAnim)

        // Ripple 1
        val anim1 = ValueAnimator.ofFloat(1f, 1.5f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                staffRipple1.scaleX = s
                staffRipple1.scaleY = s
                staffRipple1.alpha = 0.6f * (1f - (s - 1f) / 0.5f)
            }
            start()
        }
        staffAnimators.add(anim1)
        // Ripple 2 delayed
        handler.postDelayed({
            val anim2 = ValueAnimator.ofFloat(1f, 1.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val s = it.animatedValue as Float
                    staffRipple2.scaleX = s
                    staffRipple2.scaleY = s
                    staffRipple2.alpha = 0.4f * (1f - (s - 1f) / 0.5f)
                }
                start()
            }
            staffAnimators.add(anim2)
        }, 500)
    }

    fun startIdleAnimations() {
        idleAnimators.clear()
        
        // Background Glow slow breathing rotation/scale
        val glowAnimX = ObjectAnimator.ofFloat(viewIdleBgGlow, "scaleX", 1.0f, 1.2f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val glowAnimY = ObjectAnimator.ofFloat(viewIdleBgGlow, "scaleY", 1.0f, 1.2f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        glowAnimX.start(); glowAnimY.start()
        idleAnimators.add(glowAnimX); idleAnimators.add(glowAnimY)

        // Halo rings expanding and fading
        val halos = listOf(idleHalo1, idleHalo2, idleHalo3)
        halos.forEachIndexed { i, halo ->
            val initDelay = (i * 1200).toLong()
            halo.alpha = 0f
            
            val scaleXAnim = ObjectAnimator.ofFloat(halo, "scaleX", 0.5f, 2.5f)
            val scaleYAnim = ObjectAnimator.ofFloat(halo, "scaleY", 0.5f, 2.5f)
            val alphaAnim = ObjectAnimator.ofFloat(halo, "alpha", 0.6f, 0f)
            
            listOf(scaleXAnim, scaleYAnim, alphaAnim).forEach { anim ->
                anim.duration = 3600
                anim.startDelay = initDelay
                anim.repeatCount = ValueAnimator.INFINITE
                anim.start()
                idleAnimators.add(anim)
            }
        }

        // Floating robot container animation (overall hover)
        val hoverAnim = ObjectAnimator.ofFloat(idleRobotContainer, "translationY", 0f, -20f, 0f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        idleAnimators.add(hoverAnim)
        
        // Start Cat Concierge AnimatedVectorDrawable and loop it
        val catDrawable = ivCatConcierge.drawable
        if (catDrawable is AnimatedVectorDrawable) {
            catDrawable.start()
            catAvdCallback = object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    handler.post { catDrawable.start() }
                }
            }
            catDrawable.registerAnimationCallback(catAvdCallback!!)
        }

        // Gentle CTA button pulsing
        val ctaScaleX = ObjectAnimator.ofFloat(llIdleCta, "scaleX", 1.0f, 1.05f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        val ctaScaleY = ObjectAnimator.ofFloat(llIdleCta, "scaleY", 1.0f, 1.05f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        idleAnimators.add(ctaScaleX); idleAnimators.add(ctaScaleY)
    }

    fun stopAllAnimations() {
        val catDrawable = ivCatConcierge.drawable
        if (catDrawable is AnimatedVectorDrawable) {
            catDrawable.stop()
            catAvdCallback?.let { catDrawable.unregisterAnimationCallback(it) }
            catAvdCallback = null
        }
        
        idleAnimators.forEach { it.cancel() }; idleAnimators.clear()
        greetingAnimators.forEach { it.cancel() }; greetingAnimators.clear()
        thinkingAnimators.forEach { it.cancel() }; thinkingAnimators.clear()
        listeningAnimators.forEach { it.cancel() }; listeningAnimators.clear()
        guidanceAnimators.forEach { it.cancel() }; guidanceAnimators.clear()
        navAnimators.forEach { it.cancel() }; navAnimators.clear()
        arrivalAnimators.forEach { it.cancel() }; arrivalAnimators.clear()
        staffAnimators.forEach { it.cancel() }; staffAnimators.clear()
    }
}
