package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.robotemi.sdk.Robot
import kotlinx.coroutines.delay

class TiltHeadTool(private val robot: Robot) : TemiTool {
    override val name: String = "tilt_head"
    override val description: String = "頭（画面）を上下に動かす。上を見る=正の値(最大55)、下を見る=負の値(最小-25)。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("degrees", ParamType.INTEGER, "傾ける角度（-25〜55）", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val num = args["degrees"] as? Number ?: return ToolResult(false, "degrees パラメータがありません")
        val degrees = num.toInt().coerceIn(-25, 55)

        robot.tiltAngle(degrees)
        delay(800)
        
        return ToolResult(true, "頭を${degrees}度に傾けました")
    }
}
