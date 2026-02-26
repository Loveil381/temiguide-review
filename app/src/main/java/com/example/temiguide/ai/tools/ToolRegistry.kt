package com.example.temiguide.ai.tools

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema

/**
 * Tool 注册中心。
 * 管理所有可用 Tool，并能自动生成 Gemini API 需要的 FunctionDeclaration 列表。
 *
 * FunctionDeclaration の構造は GeminiProvider.kt の実装に合わせています：
 *   FunctionDeclaration(
 *       name = "...",
 *       description = "...",
 *       parameters = mapOf("key" to Schema.string("desc")),
 *       optionalParameters = listOf("optionalKey")   // 省略可能なパラメータがある場合のみ
 *   )
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, TemiTool>()

    fun register(tool: TemiTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): TemiTool? = tools[name]

    fun getAll(): List<TemiTool> = tools.values.toList()

    fun getAllNames(): List<String> = tools.keys.toList()

    /**
     * 将所有注册的 Tool 转换为 Gemini API 的 FunctionDeclaration 列表。
     *
     * 使用与 GeminiProvider.buildDefaultFunctionDeclarations() 相同的
     * FunctionDeclaration 构造方式：
     *   - name / description: 直接来自 TemiTool
     *   - parameters: mapOf(paramName to Schema.xxx(description))
     *   - optionalParameters: required=false のパラメータ名リスト
     */
    fun toFunctionDeclarations(): List<FunctionDeclaration> {
        return tools.values.map { tool ->
            val paramMap: Map<String, Schema> = tool.parameters.associate { param ->
                param.name to param.type.toSchema(param.description)
            }
            val optionalParams: List<String> = tool.parameters
                .filter { !it.required }
                .map { it.name }

            if (optionalParams.isEmpty()) {
                FunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = paramMap
                )
            } else {
                FunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = paramMap,
                    optionalParameters = optionalParams
                )
            }
        }
    }
}

/**
 * ParamType → Firebase AI Schema への変換。
 * GeminiProvider.kt の ParamSpec.toFirebaseSchema() と同じ対応関係を使用。
 *   STRING  → Schema.string()
 *   INTEGER → Schema.integer()
 *   NUMBER  → Schema.double()
 *   BOOLEAN → Schema.boolean()
 */
private fun ParamType.toSchema(description: String): Schema = when (this) {
    ParamType.STRING  -> Schema.string(description)
    ParamType.INTEGER -> Schema.integer(description)
    ParamType.NUMBER  -> Schema.double(description)
    ParamType.BOOLEAN -> Schema.boolean(description)
}
