package com.github.aicommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "AiCommitMessage", storages = [Storage("aiCommitMessage.xml")])
class AiCommitSettings : PersistentStateComponent<AiCommitSettings.State> {
    data class State(
        var baseUrl: String = "https://api.openai.com/v1",
        var model: String = "",
        var language: String = "中文",
        var codeSharingConfirmed: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun updateFrom(source: State) {
        XmlSerializerUtil.copyBean(source, state)
    }

    @RequiresBackgroundThread
    fun loadApiKey(): String =
        PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()

    @RequiresBackgroundThread
    fun saveApiKey(apiKey: String) {
        PasswordSafe.instance.setPassword(
            credentialAttributes(),
            apiKey.trim().ifEmpty { null },
        )
    }

    companion object {
        fun getInstance(): AiCommitSettings =
            ApplicationManager.getApplication().getService(AiCommitSettings::class.java)

        private fun credentialAttributes() = CredentialAttributes(
            generateServiceName("AI Commit Message", "OpenAI-compatible API key"),
        )
    }
}
