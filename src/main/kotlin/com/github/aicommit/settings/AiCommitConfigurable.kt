package com.github.aicommit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPasswordField

class AiCommitConfigurable : BoundSearchableConfigurable(
    "AI Commit Message",
    "com.github.aicommit.settings",
) {
    private val settings = AiCommitSettings.getInstance()
    private val draft = settings.state.copy()
    private val apiKeyField = JPasswordField()

    override fun createPanel(): DialogPanel = panel {
        row("Base URL:") {
            textField()
                .bindText(draft::baseUrl)
                .columns(40)
                .comment("OpenAI 兼容服务地址，例如 https://api.openai.com/v1；http:// 仅允许本机地址")
        }
        row("模型:") {
            textField()
                .bindText(draft::model)
                .columns(40)
                .comment("服务商提供的模型 ID，例如 gpt-4o-mini")
        }
        row("API Key:") {
            cell(apiKeyField)
                .columns(40)
                .comment("留空表示保留已保存的密钥；密钥仅存储在 IDEA PasswordSafe 中")
        }
        row("提交信息语言:") {
            textField()
                .bindText(draft::language)
                .columns(12)
        }
        row {
            checkBox("我已了解：生成时会把勾选的代码变更发送到上述模型服务")
                .bindSelected(draft::codeSharingConfirmed)
        }
    }

    override fun isModified(): Boolean = draft != settings.state || apiKeyField.password.isNotEmpty()

    override fun apply() {
        // panel.apply() writes the bound component values into `draft`; skipping it stores stale values.
        super.apply()

        val newApiKey = String(apiKeyField.password)
        settings.updateFrom(draft)

        if (newApiKey.isNotBlank()) {
            ApplicationManager.getApplication().executeOnPooledThread {
                settings.saveApiKey(newApiKey)
                ApplicationManager.getApplication().invokeLater({
                    apiKeyField.text = ""
                }, ModalityState.any())
            }
        }
    }

    override fun reset() {
        val state = settings.state
        draft.baseUrl = state.baseUrl
        draft.model = state.model
        draft.language = state.language
        draft.codeSharingConfirmed = state.codeSharingConfirmed
        apiKeyField.text = ""
        super.reset()
    }
}
