package com.example.temiguide.ai.tools.impl

import android.util.Log
import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.example.temiguide.core.AppState
import com.example.temiguide.core.StateManager
import com.example.temiguide.robot.NavigationHandler
import com.example.temiguide.ui.ScreenManager
import com.robotemi.sdk.Robot
import com.example.temiguide.core.AppConstants

class NavigateTool(
    private val robot: Robot,
    private val stateManager: StateManager,
    private val screenManager: ScreenManager? = null,
    private val navigationHandler: NavigationHandler? = null
) : TemiTool {
    override val name: String = "navigate"
    override val description: String = "保存済みの地点へ顧客を案内する。地点名が正確に分かる場合に使用。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("location", ParamType.STRING, "目的地（保存済みの地点名）", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val location = args["location"] as? String ?: return ToolResult(false, "location パラメータがありません")

        // 地点名の解決（大文字小文字・部分一致）
        val locations = robot.locations
        val resolvedLocation = locations.firstOrNull { it.equals(location, ignoreCase = true) }
            ?: locations.firstOrNull { it.contains(location, ignoreCase = true) }
            ?: if (locations.contains(location)) location else null

        if (resolvedLocation == null) {
            return ToolResult(false, "目的地 '$location' は存在しません。利用可能: ${locations.joinToString(", ")}")
        }

        // 状態遷移
        val navState = AppState.Navigating(destination = resolvedLocation, displayName = resolvedLocation)
        val transOk = stateManager.transition(navState)
        if (!transOk) {
            stateManager.forceTransition(navState)
        }

        // ナビゲーション画面に切り替え
        screenManager?.showNavigationScreen(resolvedLocation, "案内中")

        // ★ 重要: NavigationHandler の isNavigating フラグを設定
        navigationHandler?.let {
            it.isNavigating = true
            Log.d("TemiGuide", "NavigateTool: isNavigating set to TRUE before goTo($resolvedLocation)")
        }

        // 移動開始 & 到着待ち
        robot.goTo(resolvedLocation)
        val arrived = NavigationAwaiter.awaitArrival(AppConstants.NAVIGATION_TIMEOUT_MS)

        return if (arrived) {
            Log.d("TemiGuide", "NavigateTool: arrived at $resolvedLocation")
            ToolResult(true, "目的地 '$resolvedLocation' に到着しました")
        } else {
            Log.d("TemiGuide", "NavigateTool: failed or timed out for $resolvedLocation")
            ToolResult(false, "目的地への移動に失敗またはタイムアウトしました")
        }
    }
}
