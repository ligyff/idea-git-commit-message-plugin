package com.github.aicommit.ai

import com.github.aicommit.settings.AiCommitSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.net.HttpConnectionUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

class OpenAiCompatibleClient(@Suppress("unused") project: Project) {
    @RequiresBackgroundThread
    fun generate(diff: String, indicator: ProgressIndicator): String {
        val settings = AiCommitSettings.getInstance().state
        val baseUrl = validateBaseUrl(settings.baseUrl)
        val model = settings.model.trim()

        require(model.isNotBlank()) { "请先在设置中填写模型 ID" }
        require(settings.codeSharingConfirmed) { "请先在设置中确认代码发送范围" }

        val apiKey = AiCommitSettings.getInstance().loadApiKey()
        require(apiKey.isNotBlank()) { "请先在设置中填写 API Key" }

        val system = CommitMessagePrompt.system(settings.language)
        val user = CommitMessagePrompt.user(diff)

        try {
            val response = post(
                "$baseUrl/chat/completions",
                chatCompletionsBody(model, system, user),
                apiKey,
                indicator,
            )
            return parseChatCompletions(response)
        } catch (e: HttpStatusException) {
            if (!e.isProtocolMismatch) throw e

            // Volcengine Agent Plan (and some gateways) expose only the Responses protocol for most models.
            try {
                val response = post("$baseUrl/responses", responsesBody(model, system, user), apiKey, indicator)
                return parseResponses(response)
            } catch (fallback: HttpStatusException) {
                throw IOException(
                    "两种协议均失败。chat/completions → HTTP ${e.status}；responses → HTTP ${fallback.status}：" +
                        fallback.detail,
                    fallback,
                )
            }
        }
    }

    private fun post(url: String, body: String, apiKey: String, indicator: ProgressIndicator): String {
        val connection = HttpConnectionUtils.openHttpConnection(url)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")

            indicator.checkCanceled()
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val status = connection.responseCode
            indicator.checkCanceled()

            val response = readResponse(connection, status, indicator)
            if (status !in 200..299) throw HttpStatusException(status, describeError(status, response))

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun chatCompletionsBody(model: String, system: String, user: String): String = buildString {
        append("{\"model\":").append(jsonString(model))
        append(",\"messages\":[")
        append("{\"role\":\"system\",\"content\":").append(jsonString(system)).append('}')
        append(",{\"role\":\"user\",\"content\":").append(jsonString(user)).append('}')
        append("]}")
    }

    private fun responsesBody(model: String, system: String, user: String): String = buildString {
        append("{\"model\":").append(jsonString(model))
        append(",\"instructions\":").append(jsonString(system))
        append(",\"input\":").append(jsonString(user))
        append('}')
    }

    private fun parseChatCompletions(response: String): String {
        val content = try {
            JsonParser.parseString(response)
                .asJsonObject["choices"].asJsonArray[0]
                .asJsonObject["message"].asJsonObject["content"].asString
        } catch (e: JsonSyntaxException) {
            throw IOException("模型返回了无效 JSON", e)
        } catch (e: RuntimeException) {
            throw IOException("模型响应缺少 choices[0].message.content", e)
        }

        return cleanMessage(content)
    }

    private fun parseResponses(response: String): String {
        val output = try {
            JsonParser.parseString(response).asJsonObject["output"].asJsonArray
        } catch (e: RuntimeException) {
            throw IOException("模型响应缺少 output", e)
        }

        val text = buildString {
            output.forEach { item ->
                val message = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                if (message.stringOrNull("type") != "message") return@forEach

                message.get("content")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { part ->
                    val content = part.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    if (content.stringOrNull("type") == "output_text") {
                        content.stringOrNull("text")?.let(::append)
                    }
                }
            }
        }

        return cleanMessage(text)
    }

    private fun cleanMessage(raw: String): String {
        val cleaned = raw
            .trim()
            .replace(CODE_FENCE_START, "")
            .removeSuffix("```")
            .trim()
        require(cleaned.isNotBlank()) { "模型返回了空提交信息" }

        return cleaned
    }

    private fun readResponse(connection: HttpURLConnection, status: Int, indicator: ProgressIndicator): String {
        val stream = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?: return ""

        return stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                indicator.checkCanceled()

                val count = reader.read(buffer)
                if (count < 0) break

                result.append(buffer, 0, count)
                if (result.length > MAX_RESPONSE_CHARS) throw IOException("模型响应过大，已中止")
            }
            result.toString()
        }
    }

    private fun describeError(status: Int, response: String): String {
        val detail = try {
            JsonParser.parseString(response)
                .asJsonObject["error"].asJsonObject["message"].asString
        } catch (_: RuntimeException) {
            response.take(300)
        }

        return "模型请求失败（HTTP $status）：$detail"
    }

    private fun validateBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Base URL 不是合法地址", e)
        }

        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") {
            "Base URL 必须以 http:// 或 https:// 开头"
        }
        if (scheme == "http") {
            require(uri.host in LOCAL_HOSTS) {
                "http:// 只能用于本机模型服务，远程地址请使用 https://"
            }
        }

        return trimmed
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private class HttpStatusException(val status: Int, val detail: String) : IOException(detail) {
        /** 404/405/5xx usually mean this endpoint does not serve the protocol, not a bad request body. */
        val isProtocolMismatch: Boolean get() = status == 404 || status == 405 || status >= 500
    }

    companion object {
        // Plain HTTP would expose the API key and source diff to the local network.
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")
        private val CODE_FENCE_START = Regex("^```[a-zA-Z0-9_-]*\\s*")
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_RESPONSE_CHARS = 100_000
    }
}
