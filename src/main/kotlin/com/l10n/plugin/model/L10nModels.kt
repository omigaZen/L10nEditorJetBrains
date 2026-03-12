package com.l10n.plugin.model

data class L10nFile(
    val languages: MutableList<String> = mutableListOf("zh-CN", "en-US"),
    val entries: MutableList<Entry> = mutableListOf()
)

data class Entry(
    var key: String,
    val translations: MutableMap<String, Translation> = mutableMapOf()
)

data class Translation(
    var text: String = "",
    var source: String? = null // "ai" or null
)

data class AiConfig(
    val provider: String = "openai",
    val apiKey: String = "",
    val secretKey: String? = null,
    val endpoint: String? = null,
    val model: String? = null
)

data class AppSettings(
    val aiConfig: AiConfig = AiConfig(),
    val defaultLanguages: List<String> = listOf("zh-CN", "en-US"),
    val recentFiles: MutableList<String> = mutableListOf()
)

// Language display names
val LANGUAGE_NAMES = mapOf(
    "zh-CN" to "中文（简体）",
    "zh-TW" to "中文（繁體）",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "ja-JP" to "日本語",
    "ko-KR" to "한국어",
    "fr-FR" to "Français",
    "de-DE" to "Deutsch",
    "es-ES" to "Español",
    "ru-RU" to "Русский",
    "pt-BR" to "Português (BR)",
    "it-IT" to "Italiano",
    "ar-SA" to "العربية",
    "th-TH" to "ไทย",
    "vi-VN" to "Tiếng Việt"
)
