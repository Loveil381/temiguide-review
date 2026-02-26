package com.example.temiguide.ai.tools

/**
 * temi 机器人的 Tool 接口。
 * 每个 Tool 代表 AI 可以调用的一个能力（导航、说话、转向等）。
 * AI 通过 Function Calling 选择 Tool 并传入参数，execute() 执行并返回结果供 AI 观察。
 */
interface TemiTool {
    /** Tool 名称，必须与 FunctionDeclaration 的 name 一致 */
    val name: String

    /** Tool 描述，AI 据此判断何时使用 */
    val description: String

    /** 参数定义 */
    val parameters: List<ToolParam>

    /** 执行 Tool 并返回结果 */
    suspend fun execute(args: Map<String, Any?>): ToolResult
}

/**
 * Tool 参数定义
 */
data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)

enum class ParamType {
    STRING, INTEGER, NUMBER, BOOLEAN
}

/**
 * Tool 执行结果，返回给 AI 作为"观察"
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val shouldWaitForUser: Boolean = false
)
