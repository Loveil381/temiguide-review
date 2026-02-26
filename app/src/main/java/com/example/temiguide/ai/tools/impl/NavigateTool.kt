package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.example.temiguide.core.AppState
import com.example.temiguide.core.StateManager
import com.example.temiguide.ui.ScreenManager
import com.robotemi.sdk.Robot
import com.example.temiguide.core.AppConstants

class NavigateTool(
    private val robot: Robot,
    private val stateManager: StateManager,
    private val screenManager: ScreenManager? = null
) : TemiTool {
    override val name: String = "navigate"
    override val description: String = "保存済みの地点へ顧客を案内する。地点名が正確に分かる場合に使用。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("location", ParamType.STRING, "目的地（保存済みの地点名）", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val location = args["location"] as? String ?: return ToolResult(false, "location パラメータがありません")

        val locations = robot.locations
        if (!locations.contains(location)) {
            // Find ignoring case or contains
            val match = locations.firstOrNull { it.equals(location, ignoreCase = true) }
                ?: locations.firstOrNull { it.contains(location, ignoreCase = true) }
            
            if (match == null) {
                return ToolResult(false, "目的地 '$location' は存在しません")
            }
        }

        val navState = AppState.Navigating(destination = location, displayName = location)
        val transOk = stateManager.transition(navState)
        if (!transOk) {
            stateManager.forceTransition(navState)
        }

        // ナビゲーション画面に切り替え（目的地名を表示）
        screenManager?.showNavigationScreen(location, "案内中")

        robot.goTo(location)
        val arrived = NavigationAwaiter.awaitArrival(AppConstants.NAVIGATION_TIMEOUT_MS)

        return if (arrived) {
            // 到着画面に切り替え
            screenManager?.showScreen(ScreenManager.SCREEN_ARRIVAL)
            ToolResult(true, "目的地 '$location' に到着しました")
        } else {
            ToolResult(false, "目的地への移動に失敗またはタイムアウトしました")
        }
    }
}
