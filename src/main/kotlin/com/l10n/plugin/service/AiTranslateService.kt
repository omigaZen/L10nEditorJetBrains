package com.l10n.plugin.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.l10n.plugin.model.AiConfig
import com.l10n.plugin.settings.L10nSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AiTranslateService {
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
        val config = L10nSettings.getInstance().getAiConfig()
        return try {
            when (config.provider) {
                "openai" -> translateWithOpenAI(text, sourceLang, targetLang, config)
                "claude" -> translateWithClaude(text, sourceLang, targetLang, config)
                "baidu" -> translateWithBaidu(text, sourceLang, targetLang, config)
                else -> Result.failure(IllegalArgumentException("Unknown provider: ${config.provider}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun translateWithOpenAI(text: String, sourceLang: String, targetLang: String, config: AiConfig): Result<String> {
        val endpoint = config.endpoint ?: "https://api.openai.com/v1"
        val model = config.model ?: "gpt-4o-mini"
        val url = "$endpoint/chat/completions"

        val prompt = "Translate the following text from $sourceLang to $targetLang. Only output the translation result, nothing else.\n\nText: $text"

        val requestBody = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "temperature" to 0.3
        )).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("OpenAI API error (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val content = json.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
            return Result.success(content.trim())
        }
    }

    private fun translateWithClaude(text: String, sourceLang: String, targetLang: String, config: AiConfig): Result<String> {
        val endpoint = config.endpoint ?: "https://api.anthropic.com"
        val model = config.model ?: "claude-3-5-sonnet-20241022"
        val url = "$endpoint/v1/messages"

        val prompt = "Translate the following text from $sourceLang to $targetLang. Only output the translation result, nothing else.\n\nText: $text"

        val requestBody = gson.toJson(mapOf(
            "model" to model,
            "max_tokens" to 1024,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt))
        )).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("Claude API error (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val content = json.getAsJsonArray("content")
                .get(0).asJsonObject
                .get("text").asString
            return Result.success(content.trim())
        }
    }

    private fun translateWithBaidu(text: String, sourceLang: String, targetLang: String, config: AiConfig): Result<String> {
        val endpoint = config.endpoint ?: "https://fanyi-api.baidu.com/api/trans/vip/translate"
        val secretKey = config.secretKey ?: return Result.failure(Exception("Baidu requires Secret Key"))

        val appId = config.apiKey
        val salt = System.currentTimeMillis().toString()
        val signInput = "$appId$text$salt$secretKey"
        val sign = md5(signInput)

        val from = langToBaiduCode(sourceLang)
        val to = langToBaiduCode(targetLang)

        val url = "$endpoint?q=${java.net.URLEncoder.encode(text, "UTF-8")}&from=$from&to=$to&appid=$appId&salt=$salt&sign=$sign"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)

            if (json.has("error_code")) {
                val errorCode = json.get("error_code").asString
                val errorMsg = json.get("error_msg")?.asString ?: "Unknown error"
                return Result.failure(Exception("Baidu API error ($errorCode): $errorMsg. Please check your App ID and Secret Key."))
            }

            val result = json.getAsJsonArray("trans_result")
                .get(0).asJsonObject
                .get("dst").asString
            return Result.success(result)
        }
    }

    private fun langToBaiduCode(lang: String): String {
        return when (lang) {
            "zh-CN" -> "zh"
            "zh-TW" -> "cht"
            "en-US", "en-GB" -> "en"
            "ja-JP" -> "jp"
            "ko-KR" -> "kor"
            "fr-FR" -> "fra"
            "de-DE" -> "de"
            "es-ES" -> "spa"
            else -> lang
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        val instance = AiTranslateService()
    }
}
