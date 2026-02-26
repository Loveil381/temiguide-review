package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.robotemi.sdk.Robot

class GetLocationsTool(private val robot: Robot) : TemiTool {
    override val name: String = "get_available_locations"
    override val description: String = "ロボットが案内できる全ての地点名を取得する。顧客の要望に合う地点を探す時に使用。"
    override val parameters: List<ToolParam> = emptyList()

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val locs = robot.locations
        return ToolResult(true, "利用可能な地点: ${locs.joinToString(", ")}")
    }
}
