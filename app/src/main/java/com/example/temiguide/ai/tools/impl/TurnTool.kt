package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.robotemi.sdk.Robot
import kotlinx.coroutines.delay
import kotlin.math.abs

class TurnTool(private val robot: Robot) : TemiTool {
    override val name: String = "turn"
    override val description: String = "指定した角度だけその場で回転する。方向を示す時に使用。正の値=右回転、負の値=左回転。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("degrees", ParamType.INTEGER, "回転する角度（度）", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val num = args["degrees"] as? Number ?: return ToolResult(false, "degrees パラメータがありません")
        val degrees = num.toInt()

        robot.turnBy(degrees, 1.0f)
        delay(abs(degrees) * 20L + 500)
        
        return ToolResult(true, "${degrees}度回転しました")
    }
}
