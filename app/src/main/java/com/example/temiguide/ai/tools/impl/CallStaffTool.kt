package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult

class CallStaffTool : TemiTool {
    override val name: String = "call_staff"
    override val description: String = "店員を呼ぶ。ロボットでは対応できない専門的な要望や、試着・支払いなどの場合に使用。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("reason", ParamType.STRING, "店員を呼ぶ理由", false)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val reason = args["reason"] as? String
        return ToolResult(true, "店員を呼びました。理由: ${reason ?: "お客様対応"}")
    }
}
