package com.example.temiguide.ai.gemini

import android.util.Log
import com.example.temiguide.ai.*
import com.example.temiguide.core.AppConfig
import com.example.temiguide.models.locationInfo
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.content
import com.example.temiguide.ai.tools.ToolRegistry
// import com.google.firebase.ai.type.ThinkingLevel
// import com.google.firebase.ai.type.thinkingConfig


/**
 * Firebase AI Logic を使用する AiProvider 実装。
 *
 * Firebase.ai(backend = GenerativeBackend.googleAI()) 経由で Gemini モデルを呼び出す。
 * このプロバイダは **無状態** であり、会話履歴を内部保持しない。
 */
class GeminiProvider : AiProvider {

    var toolRegistry: ToolRegistry? = null

    override val providerName: String = "Gemini"

    companion object {
        private const val TAG = "GeminiProvider"
        // ★ SYSTEM_INSTRUCTION_TEMPLATE 已削除，所有 prompt 由 PersonaPromptBuilder 提供
    }

    // ==================== AiProvider Implementation ====================

    override suspend fun chat(
        userText: String,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        return chatWithFunctions(userText, emptyList(), conversationHistory, systemPromptOverride)
    }

    override suspend fun chatWithFunctions(
        userText: String,
        functions: List<FunctionSpec>,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        return try {
            val modelName = AppConfig.modelName.ifBlank { "gemini-3-flash-preview" }
            val systemInstruction = systemPromptOverride ?: buildSystemInstruction()

            val tools = if (functions.isEmpty()) {
                val registered = toolRegistry?.toFunctionDeclarations() ?: emptyList()
                if (registered.isNotEmpty()) listOf(Tool.functionDeclarations(registered)) else emptyList()
            } else {
                listOf(Tool.functionDeclarations(functions.map { it.toFirebaseFunctionDeclaration() }))
            }

            val generationConfig = generationConfig {
                temperature = AppConfig.temperature
                maxOutputTokens = AppConfig.maxTokens
                // thinkingConfig = thinkingConfig {
                //     thinkingLevel = AppConfig.getThinkingLevelEnum()
                // }
            }

            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig,
                    systemInstruction = content { text(systemInstruction) },
                    tools = tools
                )

            val history = convertHistory(conversationHistory)
            val chat = model.startChat(history)
            val response = chat.sendMessage(userText)

            AiResponse(
                text = response.text ?: "",
                actions = emptyList(),
                rawResponse = response.text ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API Error", e)
            AiResponse(text = "", actions = emptyList(), error = e.message ?: "Unknown error")
        }
    }

    /**
     * 发送消息给 AI，支持 Tool 调用。
     * 返回 AI 的文本回复和/或 Tool 调用请求。
     */
    suspend fun generateWithTools(
        prompt: String,
        conversationHistory: List<Content> = emptyList(),
        systemPromptOverride: String? = null
    ): GeminiToolResponse {
        return try {
            val modelName = AppConfig.modelName.ifBlank { "gemini-3-flash-preview" }
            val systemInstruction = systemPromptOverride ?: buildSystemInstruction()

            val toolDeclarations = toolRegistry?.toFunctionDeclarations() ?: emptyList()
            val toolsConfig = if (toolDeclarations.isNotEmpty()) {
                listOf(Tool.functionDeclarations(toolDeclarations))
            } else {
                emptyList()
            }

            val generationConfig = generationConfig {
                temperature = AppConfig.temperature
                maxOutputTokens = AppConfig.maxTokens
            }

            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig,
                    systemInstruction = content { text(systemInstruction) },
                    tools = toolsConfig
                )

            val chat = model.startChat(conversationHistory)
            val response = chat.sendMessage(prompt)

            val functionCalls = response.functionCalls
            val toolCalls = functionCalls.map { call ->
                val mappedArgs: Map<String, Any?> = call.args.mapValues { (_, element) ->
                    val primitive = element as? kotlinx.serialization.json.JsonPrimitive
                    if (primitive != null) {
                        if (primitive.isString) {
                            primitive.content
                        } else {
                            primitive.content.toBooleanStrictOrNull() 
                                ?: primitive.content.toIntOrNull() 
                                ?: primitive.content.toDoubleOrNull() 
                                ?: primitive.content
                        }
                    } else {
                        element.toString()
                    }
                }
                ToolCall(call.name, mappedArgs)
            }

            GeminiToolResponse(
                text = response.text,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API Error in generateWithTools", e)
            GeminiToolResponse(text = null, toolCalls = emptyList())
        }
    }

    override fun isAvailable(): Boolean = true  // Firebase handles auth; always available if google-services.json present

    // ==================== Private Helpers ====================

    private fun buildSystemInstruction(): String {
        val remotePrompt = com.example.temiguide.core.RemoteConfigManager.getString(
            com.example.temiguide.core.RemoteConfigManager.KEY_SYSTEM_PROMPT
        )
        val customPrompt = com.example.temiguide.core.AppConfig.systemPrompt
        
        return when {
            remotePrompt.isNotBlank() -> remotePrompt
            customPrompt.isNotBlank() -> customPrompt
            else -> {
                // Fallback: 最小限の指示のみ。PersonaPromptBuilder がオーバーライドする前提
                Log.w(TAG, "No system prompt configured; using minimal fallback")
                "あなたは商業施設の案内ロボットです。日本語で丁寧に応答してください。"
            }
        }
    }

    private fun convertHistory(history: List<Message>): List<Content> {
        return history.map { msg ->
            content(role = if (msg.role == "assistant") "model" else msg.role) {
                text(msg.content)
            }
        }
    }


}

// ==================== FunctionSpec → Firebase AI 変換 ====================

private fun FunctionSpec.toFirebaseFunctionDeclaration(): FunctionDeclaration {
    return FunctionDeclaration(
        name = this.name,
        description = this.description,
        parameters = this.parameters.mapValues { (_, paramSpec) -> paramSpec.toFirebaseSchema() },
        optionalParameters = this.parameters.filter { !it.value.required }.map { it.key }
    )
}

private fun ParamSpec.toFirebaseSchema(): Schema {
    return when (this.type.lowercase()) {
        "string" -> Schema.string(this.description)
        "integer", "int" -> Schema.integer(this.description)
        "number", "float", "double" -> Schema.double(this.description)
        "boolean", "bool" -> Schema.boolean(this.description)
        "array" -> Schema.array(this.items?.toFirebaseSchema() ?: Schema.string("item"))
        "object" -> Schema.obj(
            this.properties?.mapValues { (_, ps) -> ps.toFirebaseSchema() } ?: emptyMap()
        )
        else -> Schema.string(this.description)
    }
}

// ==================== Gemini Tool System Data Classes ====================

data class GeminiToolResponse(
    val text: String?,
    val toolCalls: List<ToolCall>
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any?>
)
