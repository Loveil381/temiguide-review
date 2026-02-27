package com.example.temiguide.robot

import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import com.example.temiguide.MainActivity
import com.example.temiguide.R
import com.example.temiguide.ui.ScreenManager
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState

class DialogActionHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val locationInfo: Map<String, String>,
    private val stateManager: StateManager
) {

    // ★ isGuideMode は NavigationHandler が管轄すべきだが、
    //   DetectionHandler・NavigationHandler 等 6 か所から参照されているため、
    //   移行期としてここに残す。Phase 4 で NavigationHandler へ移行する。
    var isGuideMode = false

    // ★ isNavigating は NavigationHandler.isNavigating へ完全移行済み。
    //   レガシー参照があれば NavigationHandler を参照する。
    @Deprecated("Use NavigationHandler.isNavigating instead", ReplaceWith("navigationHandler.isNavigating"))
    var isNavigating = false

    var isStaffCallActive = false

    // ★ pendingArrivalAnnouncement / pendingQueueCompletion は
    //   NavigationHandler.handleArrival() でのみ使用。
    //   現状 handleArrival 内で直接参照するため残すが、
    //   Phase 4 で NavigationHandler 内フィールドへ移行する。
    var pendingArrivalAnnouncement: String? = null
    var pendingQueueCompletion: (() -> Unit)? = null

    // ★ isQueueAskMode 削除済み（Phase 2 で onAsrResult 内の分岐を削除済み）
    //   以下は互換性のためゼロコスト stub を残す
    @Deprecated("Queue-ask mode removed in Phase 2")
    var isQueueAskMode: Boolean
        get() = false
        set(_) {}

    // ★ actionQueue 削除済み。互換性 stub。
    @Deprecated("ActionQueue removed in Phase 2")
    val actionQueue: Nothing? = null

    private var postSafelyInternal: ((Long, () -> Unit) -> Unit)? = null
    private var returnToHomeInternal: (() -> Unit)? = null
    private var speakAndListenInternal: ((String) -> Unit)? = null
    private var speakOnlyInternal: ((String, (() -> Unit)?) -> Unit)? = null

    fun setCallbacks(
        postSafely: (Long, () -> Unit) -> Unit,
        returnToHome: () -> Unit,
        speakAndListen: (String) -> Unit,
        speakOnly: (String, (() -> Unit)?) -> Unit,
        onSaveMemory: ((String, String) -> Unit)? = null,
        onMoveToZone: ((String) -> Unit)? = null,
        onNavStart: (() -> Unit)? = null
    ) {
        this.postSafelyInternal = postSafely
        this.returnToHomeInternal = returnToHome
        this.speakAndListenInternal = speakAndListen
        this.speakOnlyInternal = speakOnly
    }

    private fun postSafely(delay: Long, action: () -> Unit) {
        postSafelyInternal?.invoke(delay, action)
    }

    private fun returnToHome() {
        returnToHomeInternal?.invoke()
    }

    private fun speakOnly(text: String, onDone: (() -> Unit)? = null) {
        speakOnlyInternal?.invoke(text, onDone)
    }

    fun handleCallStaff(reply: String) {
        speakOnly(reply)
        screenManager.showScreen(ScreenManager.SCREEN_STAFF)
        isStaffCallActive = true
        
        postSafely(500) {
            if (!isStaffCallActive) return@postSafely
            val viewStaffProgress = activity.findViewById<View>(R.id.viewStaffProgress)
            val params = viewStaffProgress.layoutParams
            val parent = viewStaffProgress.parent as? View ?: return@postSafely
            val targetWidth = (parent.width * 0.66).toInt()
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = 15000
                addUpdateListener {
                    if (!isStaffCallActive) {
                        cancel()
                        return@addUpdateListener
                    }
                    params.width = it.animatedValue as Int
                    viewStaffProgress.layoutParams = params
                }
                start()
            }
        }
        
        postSafely(30000) {
            if (!isStaffCallActive) return@postSafely
            isStaffCallActive = false
            speakOnly(activity.getString(R.string.staff_not_found)) {
                returnToHome()
            }
        }
    }

    fun cancelAll() {
        pendingQueueCompletion = null
        isStaffCallActive = false
        pendingArrivalAnnouncement = null
    }
}
