package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.example.temiguide.core.AppState
import com.example.temiguide.core.StateManager

class CallStaffTool(
    private val stateManager: StateManager? = null
) : TemiTool {
    override val name: String = "call_staff"
    override val description: String = "店員を呼ぶ。ロボットでは対応できない専門的な要望や、試着・支払いなどの場合に使用。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("reason", ParamType.STRING, "店員を呼ぶ理由", false)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val reason = args["reason"] as? String ?: "お客様対応"
        
        // 状態遷移
        stateManager?.let {
            val ok = it.transition(AppState.StaffCall(reason))
            if (!ok) it.forceTransition(AppState.StaffCall(reason))
        }
        
        return ToolResult(true, "店員を呼びました。理由: $reason", shouldWaitForUser = true)
    }
}
