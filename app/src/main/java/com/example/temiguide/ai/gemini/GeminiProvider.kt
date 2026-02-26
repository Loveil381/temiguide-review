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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

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

        private val SYSTEM_INSTRUCTION_TEMPLATE = """
            あなたは商業施設の案内ロボット「temi」です。親切でプロフェッショナルな販売員として振る舞ってください。

            ## 基本ルール
            - 原則として日本語で会話を始めますが、ユーザーが日本語以外（中国語、英語など）で話しかけた場合、その言語に合わせて返答してください
            - [ユーザーは中国語を話しています] のような直接的な指示がある場合は、必ずその言語を使用してください
            - 敬語を使い、温かく丁寧な口調で話してください
            - 返答は短く簡潔に（TTS読み上げのため、1回の speak は50文字以内を目安）
            - 顧客の発言から意図を読み取り、最適な売り場を推測してください
            - 地点名が分からない場合は get_available_locations で確認してから案内してください
            - 「Thought:」などの内部メモを返答に含めないでください

            ## 売り場情報
            %s

            ## ツールの使い方
            - speak: 顧客に話しかける。1回50文字以内。長い説明は2回に分ける
            - navigate: 保存済みの地点名が正確に分かる時のみ使用。必ず speak の後に使う
            - ask_user: 顧客の好みや要望を確認したい時に使用
            - turn: 方向を示す時（右=正の値、左=負の値）
            - tilt_head: 商品棚を見せる時（下向き=負、上向き=正）
            - get_available_locations: 案内可能な地点一覧を取得
            - call_staff: ロボットでは対応できない場合（試着、支払い等）

            ## 行動パターン

            ### パターン1: 単一地点の案内
            speak（「○○売り場にご案内します、こちらへどうぞ」）→ navigate → 到着後 speak（売り場の簡単な紹介）

            ### パターン2: 複数地点の案内（重要）
            顧客が2つ以上の売り場を希望した場合：
            1. speak（「まず○○売り場、その後△△売り場にご案内します」）
            2. navigate（1つ目の地点）
            3. 到着後 speak（「○○売り場に到着しました」）← 短く、質問しない
            4. すぐに speak（「次は△△売り場に向かいます」）
            5. navigate（2つ目の地点）
            6. 最後の地点に到着後のみ speak（「すべてご案内しました。他にお手伝いできることはありますか？」）
            ※途中の地点で「ご覧になりますか？」等の確認はしない。すべて案内し終えてから質問する

            ### パターン3: 商品を探している場合
            get_available_locations → 最適な地点を推測 → speak + navigate

            ### パターン4: 会話終了
            顧客が「もういい」「ありがとう」「大丈夫」と言ったら → speak（短いお礼）のみ。navigate しない

            ## 禁止事項
            - navigate を呼ぶ前に speak なしで動き出すこと
            - 存在しない地点名で navigate を呼ぶこと
            - 1回の speak で100文字を超えること
            - 到着後の紹介で長々と話すこと（30文字以内で簡潔に）
        """.trimIndent()
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
                listOf(Tool.functionDeclarations(toolRegistry?.toFunctionDeclarations() ?: buildDefaultFunctionDeclarations()))
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

            parseResponse(response)
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

            val toolDeclarations = toolRegistry?.toFunctionDeclarations() ?: buildDefaultFunctionDeclarations()
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
                                ?: primitive.intOrNull 
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
        val remotePrompt = com.example.temiguide.core.RemoteConfigManager.getString(com.example.temiguide.core.RemoteConfigManager.KEY_SYSTEM_PROMPT)
        val customPrompt = AppConfig.systemPrompt
        val locationsList = locationInfo.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        
        return if (remotePrompt.isNotBlank()) {
            remotePrompt
        } else if (customPrompt.isNotBlank()) {
            customPrompt
        } else {
            String.format(SYSTEM_INSTRUCTION_TEMPLATE, locationsList)
        }
    }

    private fun convertHistory(history: List<Message>): List<Content> {
        return history.map { msg ->
            content(role = if (msg.role == "assistant") "model" else msg.role) {
                text(msg.content)
            }
        }
    }

    private fun parseResponse(response: com.google.firebase.ai.type.GenerateContentResponse): AiResponse {
        val functionCalls = response.functionCalls
        val actions = mutableListOf<TemiAction>()
        var mainReply = response.text ?: ""

        if (functionCalls.isNotEmpty()) {
            for (call in functionCalls) {
                val args = call.args
                val callActions = parseFunctionCall(call.name, args)
                actions.addAll(callActions)
                
                if (mainReply.isBlank() && callActions.isNotEmpty()) {
                    mainReply = extractTextFromAction(callActions.first()) ?: ""
                }
            }
        } else if (mainReply.isNotBlank()) {
            actions.add(TemiAction.Speak(mainReply))
        }

        if (mainReply.isBlank() && actions.isNotEmpty()) {
            mainReply = actions.firstNotNullOfOrNull { extractTextFromAction(it) } ?: "ご案内します"
        }

        return AiResponse(
            text = mainReply,
            actions = actions,
            confidence = 1.0f,
            rawResponse = response.text ?: "Function calls executed",
            provider = providerName
        )
    }

    private fun parseFunctionCall(name: String, args: Map<String, JsonElement>): List<TemiAction> {
        return try {
            when (name) {
                "navigate_to" -> {
                    val location = args["location"]?.jsonPrimitive?.content ?: return emptyList()
                    val announcement = args["announcement"]?.jsonPrimitive?.content
                    listOf(TemiAction.Navigate(location, announcement))
                }
                "navigate_queue" -> parseNavigateQueue(args)
                "speak" -> {
                    val text = args["text"]?.jsonPrimitive?.content ?: return emptyList()
                    listOf(TemiAction.Speak(text))
                }
                "ask_followup" -> {
                    val question = args["question"]?.jsonPrimitive?.content ?: return emptyList()
                    listOf(TemiAction.AskQuestion(question))
                }
                "call_staff" -> {
                    val reason = args["reason"]?.jsonPrimitive?.content ?: "スタッフ呼び出し"
                    listOf(TemiAction.CallStaff(reason))
                }
                "end_conversation" -> {
                    val reply = args["reply"]?.jsonPrimitive?.content ?: ""
                    listOf(TemiAction.EndConversation(reply))
                }
                "pause_robot" -> {
                    val message = args["message"]?.jsonPrimitive?.content ?: ""
                    listOf(TemiAction.Pause(message))
                }
                "wait_seconds" -> {
                    val seconds = args["seconds"]?.jsonPrimitive?.intOrNull ?: 3
                    listOf(TemiAction.Wait(seconds))
                }
                "move_to_zone" -> {
                    val location = args["location"]?.jsonPrimitive?.content ?: return emptyList()
                    listOf(TemiAction.MoveToZone(location))
                }
                "save_memory" -> {
                    val key = args["key"]?.jsonPrimitive?.content ?: return emptyList()
                    val value = args["value"]?.jsonPrimitive?.content ?: return emptyList()
                    listOf(TemiAction.SaveMemory(key, value))
                }
                "turn_by" -> {
                    val degrees = args["degrees"]?.jsonPrimitive?.intOrNull ?: return emptyList()
                    val speed = args["speed"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
                    listOf(TemiAction.TurnBy(degrees, speed))
                }
                "tilt_head" -> {
                    val angle = args["angle"]?.jsonPrimitive?.intOrNull ?: return emptyList()
                    listOf(TemiAction.TiltHead(angle))
                }
                "go_home" -> {
                    listOf(TemiAction.GoHome)
                }
                else -> {
                    Log.w(TAG, "Unknown function call: $name")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse function call: $name", e)
            emptyList()
        }
    }

    private fun parseNavigateQueue(args: Map<String, JsonElement>): List<TemiAction> {
        val actions = mutableListOf<TemiAction>()
        try {
            val locationsElement = args["locations"] ?: return emptyList()
            val locationsArray = if (locationsElement is kotlinx.serialization.json.JsonArray) {
                locationsElement 
            } else if (locationsElement is kotlinx.serialization.json.JsonPrimitive && locationsElement.isString) {
                kotlinx.serialization.json.Json.parseToJsonElement(locationsElement.content) as? kotlinx.serialization.json.JsonArray
            } else {
                null
            } ?: return emptyList()

            for (element in locationsArray) {
                val obj = element.jsonObject
                val location = obj["location"]?.jsonPrimitive?.content ?: continue
                val announcement = obj["announcement"]?.jsonPrimitive?.content
                actions.add(TemiAction.Navigate(location, announcement))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse navigate_queue", e)
        }
        return actions
    }

    private fun extractTextFromAction(action: TemiAction): String? = when (action) {
        is TemiAction.Speak -> action.text
        is TemiAction.Navigate -> action.announcement
        is TemiAction.EndConversation -> action.reply
        is TemiAction.Pause -> action.message
        is TemiAction.AskQuestion -> action.text
        else -> null
    }

    // ==================== Default Function Declarations ====================

    private fun buildDefaultFunctionDeclarations(): List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "move_to_zone",
            description = "Move voluntarily to a specific zone.",
            parameters = mapOf(
                "location" to Schema.string("Zone name")
            )
        ),
        FunctionDeclaration(
            name = "save_memory",
            description = "Save long-term memory about the user.",
            parameters = mapOf(
                "key" to Schema.string("Memory key"),
                "value" to Schema.string("Memory value")
            )
        ),
        FunctionDeclaration(
            name = "navigate_to",
            description = "Guide the user to a specific location in the store.",
            parameters = mapOf(
                "location" to Schema.string("Location name"),
                "announcement" to Schema.string("Announcement upon arrival")
            )
        ),
        FunctionDeclaration(
            name = "navigate_queue",
            description = "Guide the user to multiple locations in order.",
            parameters = mapOf(
                "locations" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "location" to Schema.string("Location name"),
                            "announcement" to Schema.string("Announcement upon arrival")
                        )
                    )
                )
            )
        ),
        FunctionDeclaration(
            name = "speak",
            description = "Speak a purely conversational message to the user.",
            parameters = mapOf(
                "text" to Schema.string("The message to speak")
            )
        ),
        FunctionDeclaration(
            name = "ask_followup",
            description = "Ask the user a question to clarify.",
            parameters = mapOf(
                "question" to Schema.string("The question to ask")
            )
        ),
        FunctionDeclaration(
            name = "call_staff",
            description = "Call a human staff member.",
            parameters = mapOf(
                "reason" to Schema.string("Reason for calling staff")
            )
        ),
        FunctionDeclaration(
            name = "end_conversation",
            description = "End the conversation.",
            parameters = mapOf(
                "reply" to Schema.string("Final reply before ending")
            )
        ),
        FunctionDeclaration(
            name = "pause_robot",
            description = "Pause the robot and wait.",
            parameters = mapOf(
                "message" to Schema.string("Message to speak while pausing")
            )
        ),
        FunctionDeclaration(
            name = "wait_seconds",
            description = "Wait for a number of seconds.",
            parameters = mapOf(
                "seconds" to Schema.integer("Seconds to wait")
            )
        ),
        FunctionDeclaration(
            name = "turn_by",
            description = "Turn the robot by specified degrees. Positive = clockwise, Negative = counter-clockwise.",
            parameters = mapOf(
                "degrees" to Schema.integer("Degrees to turn (-360 to 360)"),
                "speed" to Schema.double("Turn speed from 0.0 to 1.0")
            ),
            optionalParameters = listOf("speed")
        ),
        FunctionDeclaration(
            name = "tilt_head",
            description = "Tilt the robot's head. -30 = look down, 0 = forward, +50 = look up.",
            parameters = mapOf(
                "angle" to Schema.integer("Tilt angle from -30 to 50")
            )
        ),
        FunctionDeclaration(
            name = "go_home",
            description = "Return the robot to home base immediately.",
            parameters = mapOf<String, Schema>()
        )
    )
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
