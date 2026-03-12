package com.l10n.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.l10n.plugin.model.AiConfig
import com.l10n.plugin.model.AppSettings

@State(
    name = "L10nSettings",
    storages = [Storage("l10nEditor.xml")]
)
class L10nSettings : PersistentStateComponent<L10nSettings.State> {
    data class State(
        var aiProvider: String = "openai",
        var apiKey: String = "",
        var secretKey: String = "",
        var endpoint: String = "",
        var model: String = "",
        var defaultLanguages: List<String> = listOf("zh-CN", "en-US")
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getAiConfig(): AiConfig {
        return AiConfig(
            provider = state.aiProvider,
            apiKey = state.apiKey,
            secretKey = state.secretKey.ifBlank { null },
            endpoint = state.endpoint.ifBlank { null },
            model = state.model.ifBlank { null }
        )
    }

    fun setAiConfig(config: AiConfig) {
        state.aiProvider = config.provider
        state.apiKey = config.apiKey
        state.secretKey = config.secretKey ?: ""
        state.endpoint = config.endpoint ?: ""
        state.model = config.model ?: ""
    }

    companion object {
        fun getInstance(): L10nSettings {
            return ApplicationManager.getApplication().getService(L10nSettings::class.java)
        }
    }
}
