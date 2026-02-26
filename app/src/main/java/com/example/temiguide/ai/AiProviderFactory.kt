package com.example.temiguide.ai

import com.example.temiguide.core.AiProviderType
import com.example.temiguide.core.AppConfig
import com.example.temiguide.ai.gemini.GeminiProvider
import com.example.temiguide.ai.openai.OpenAiProvider
import com.example.temiguide.ai.deepseek.DeepSeekProvider
import com.example.temiguide.ai.local.LocalProvider

/**
 * AppConfig.aiProvider の設定に基づいて適切な AiProvider を返すファクトリ。
 *
 * アプリ起動時に [init] を呼び出してプロバイダインスタンスを登録する。
 * 新しいプロバイダを追加する場合は、init() 内に1行追加するだけでよい。
 */
object AiProviderFactory {

    private val providers = mutableMapOf<AiProviderType, AiProvider>()

    /**
     * 全プロバイダを初期化・登録する。
     * MainActivity.onCreate 等で AppConfig.init() の後に呼び出すこと。
     */
    fun init() {
        providers[AiProviderType.GEMINI] = GeminiProvider()
        providers[AiProviderType.OPENAI] = OpenAiProvider()
        providers[AiProviderType.DEEPSEEK] = DeepSeekProvider()
        providers[AiProviderType.LOCAL] = LocalProvider()
    }

    /**
     * AppConfig で設定されている現在のプロバイダを取得する。
     *
     * @throws IllegalStateException 未知のプロバイダ種別が設定されている場合
     */
    fun getProvider(): AiProvider {
        val type = AppConfig.aiProvider
        return providers[type]
            ?: throw IllegalStateException("Unknown AI provider: $type")
    }

    /**
     * 指定した種別のプロバイダを取得する。
     *
     * @param type プロバイダ種別
     * @throws IllegalStateException 未知のプロバイダ種別の場合
     */
    fun getProvider(type: AiProviderType): AiProvider {
        return providers[type]
            ?: throw IllegalStateException("Unknown AI provider: $type")
    }

    /**
     * 登録済みの全プロバイダを取得する（DevMenu の一覧表示用）。
     */
    fun getAllProviders(): Map<AiProviderType, AiProvider> = providers.toMap()
}
