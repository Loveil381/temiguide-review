package com.example.temiguide.ai

import android.util.Log
import com.example.temiguide.ai.gemini.GeminiProvider
import com.example.temiguide.ai.tools.ToolRegistry
import com.example.temiguide.utils.DevLog
import kotlinx.coroutines.withTimeoutOrNull
import com.example.temiguide.core.AppConstants
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlin.text.RegexOption

/**
 * ReAct (Reasoning + Acting) 执行引擎。
 * 
 * 循环流程:
 * 1. 用户输入 → 发给 AI
 * 2. AI 返回 tool_call → 执行 Tool → 把结果反馈给 AI
 * 3. AI 再次返回 tool_call → 继续执行...
 * 4. AI 返回纯文本（无 tool_call） → 循环结束，返回文本
 * 5. 最多循环 maxIterations 次防止无限循环
 */
class ReActEngine(
    private val geminiProvider: GeminiProvider,
    private val toolRegistry: ToolRegistry,
    private val maxIterations: Int = AppConstants.REACT_MAX_ITERATIONS
) {
    /**
     * 执行 ReAct 循环。
     * @param userInput 用户的原始输入
     * @return AI 的最终回复结果
     */
    suspend fun run(userInput: String, systemPromptOverride: String? = null, customerContext: String = ""): ReActResult {
        return try {
            val fullInput = if (customerContext.isNotBlank()) {
                "$customerContext\n\nユーザーの発言: $userInput"
            } else {
                userInput
            }
            var currentPrompt = fullInput
            var iterationCount = 0
            val executedTools = mutableListOf<String>()
            val conversationHistory = mutableListOf<Content>()

            DevLog.add("REACT", "Starting ReAct loop for input: '$userInput'")

            while (iterationCount < maxIterations) {
                iterationCount++
                DevLog.add("REACT", "Iteration $iterationCount started")

                // 1. 调用 AI (增加 12 秒单次超时)
                val response = withTimeoutOrNull(AppConstants.REACT_SINGLE_CALL_TIMEOUT_MS) {
                    geminiProvider.generateWithTools(
                        prompt = currentPrompt,
                        conversationHistory = conversationHistory,
                        systemPromptOverride = systemPromptOverride
                    )
                }

                if (response == null) {
                    Log.w("TemiGuide", "[ReAct] Single AI call timed out at iteration $iterationCount")
                    return ReActResult(
                        text = AppConstants.MSG_TIMEOUT,
                        waitingForUser = false,
                        iterationCount = iterationCount,
                        executedTools = executedTools.toList()
                    )
                }

                // 将用户的 prompt 和 AI 的响应加入 historical（简化处理：仅用于内部循环上下文）
                conversationHistory.add(content("user") { text(currentPrompt) })
                
                // 2. 检查 whether there are tool calls
                if (response.toolCalls.isEmpty()) {
                    // 如果没有 toolCall，说明 AI 决定直接回复文本，循环结束
                    // 返回最终文本前，去掉 AI 的内部思考过程
                    var finalText = response.text ?: ""
                    finalText = finalText
                        .replace(Regex("^Thought:.*?(?=[ぁ-んァ-ヶ亜-熙一-龥])", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                    
                    // 如果清除后为空，使用 fallback
                    if (finalText.isBlank()) {
                        finalText = "ご案内が完了しました。他にお手伝いできることはありますか？"
                    }

                    DevLog.add("REACT", "No tool calls. Returning final text: '$finalText'")
                    return ReActResult(
                        text = finalText,
                        waitingForUser = false,
                        iterationCount = iterationCount,
                        executedTools = executedTools
                    )
                }

                DevLog.add("REACT", "toolCalls=${response.toolCalls.map { it.name }.joinToString()}")
                
                // 3. 逐个执行 Tool
                val toolResults = mutableListOf<String>()
                var shouldWaitForUser = false

                for ((index, call) in response.toolCalls.withIndex()) {
                    val toolName = call.name
                    
                    // 修改 2 — navigate 前自动插入 speak
                    if (toolName == "navigate") {
                        val hasSpeakBefore = response.toolCalls.take(index).any { it.name == "speak" }
                        val alreadySpoke = executedTools.any { it == "speak" } 
                        if (!hasSpeakBefore && !alreadySpoke) {
                            val location = call.arguments["location"]?.toString() ?: ""
                            val autoSpeakTool = toolRegistry.get("speak")
                            if (autoSpeakTool != null) {
                                val speakResult = autoSpeakTool.execute(
                                    mapOf("text" to "${location}売り場にご案内しますね。")
                                )
                                DevLog.add("REACT", "Auto-inserted speak before navigate: ${speakResult.message}")
                                executedTools.add("speak")
                            }
                        }
                    }

                    val tool = toolRegistry.get(toolName)
                    
                    val resultMsg: String
                    if (tool != null) {
                        val result = tool.execute(call.arguments)
                        resultMsg = result.message
                        executedTools.add(toolName)
                        DevLog.add("REACT", "tool=$toolName, result=${result.message}")

                        if (result.shouldWaitForUser) {
                            shouldWaitForUser = true
                        }
                    } else {
                        resultMsg = "Unknown tool: $toolName"
                        DevLog.add("REACT", "tool=$toolName, result=Unknown tool")
                    }
                    
                    toolResults.add("- $toolName: $resultMsg")
                }

                // 4. 如果有 Tool 需要等待用户（如 ask_user）
                if (shouldWaitForUser) {
                    // 返回前，同样去掉 AI 的内部思考过程
                    var partialText = response.text ?: ""
                    partialText = partialText
                        .replace(Regex("^Thought:.*?(?=[ぁ-んァ-ヶ亜-熙一-龥])", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                    
                    if (partialText.isBlank()) {
                        partialText = "ご案内が完了しました。他にお手伝いできることはありますか？"
                    }

                    DevLog.add("REACT", "Tool requested waiting for user. Pausing loop. Partial text: '$partialText'")
                    return ReActResult(
                        text = partialText,
                        waitingForUser = true,
                        iterationCount = iterationCount,
                        executedTools = executedTools
                    )
                }

                // 将 AI 的 function call 加入历史，并构造 function response 作为下一个 prompt
                conversationHistory.add(content("model") { 
                    // In a real robust implementation, we should add actual FunctionCall/FunctionResponse parts here.
                    // For simplicity as requested, we append it as text.
                    text("Thought: I need to use tools.\n" + response.toolCalls.joinToString { "${it.name}(${it.arguments})" }) 
                })

                // 5. 把所有 Tool 执行结果拼成字符串，作为新的 prompt 发给 AI
                currentPrompt = "Tool execution results:\n" +
                        toolResults.joinToString("\n") +
                        "\n\nBased on these results, decide your next action or respond to the customer."
            }

            // 7. 如果达到 maxIterations 仍有 tool_call，返回兜底文本
            DevLog.add("REACT", "Reached max iterations ($maxIterations). Returning fallback text.")
            ReActResult(
                text = "申し訳ございません。少々お待ちください。",
                waitingForUser = false,
                iterationCount = maxIterations,
                executedTools = executedTools
            )
        } catch (e: Exception) {
            Log.e("TemiGuide", "[ReAct] Unexpected error: ${e.message}", e)
            ReActResult(
                text = AppConstants.MSG_ERROR,
                waitingForUser = false,
                iterationCount = 0,
                executedTools = emptyList()
            )
        }
    }
}

data class ReActResult(
    val text: String,
    val waitingForUser: Boolean = false,
    val iterationCount: Int = 0,
    val executedTools: List<String> = emptyList()
)
