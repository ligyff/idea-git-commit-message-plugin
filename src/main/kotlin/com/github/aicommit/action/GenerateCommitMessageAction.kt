package com.github.aicommit.action

import com.github.aicommit.ai.OpenAiCompatibleClient
import com.github.aicommit.vcs.CommitDiffCollector
import com.github.aicommit.vcs.CommitSelection
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.CommitWorkflowUi
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class GenerateCommitMessageAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val commitUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        e.presentation.isVisible = commitUi != null
        e.presentation.isEnabled = commitUi != null && !isRunning(commitUi)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) ?: return
        if (isRunning(commitUi)) return

        val changes = commitUi.getIncludedChanges()
        val unversioned = commitUi.getIncludedUnversionedFiles()
        if (changes.isEmpty() && unversioned.isEmpty()) {
            notify(project, "没有可生成的变更", "请先在提交面板中勾选要提交的文件。", false)
            return
        }

        val messageUi = commitUi.commitMessageUi
        val originalMessage = messageUi.text.orEmpty()

        // Snapshot the selected content on EDT: partial selections can change while the request is in flight.
        val snapshot = CommitSelection.capture(project, changes)
        val running = RunningCommitUi(commitUi)
        runningTasks.add(running)

        object : Task.Backgroundable(project, "AI 生成提交信息", true) {
            private var generated: String? = null
            private var failure: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    val diff = CommitDiffCollector().collect(snapshot, unversioned, indicator)
                    generated = project.getService(OpenAiCompatibleClient::class.java)
                        .generate(diff, indicator)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: IllegalArgumentException) {
                    failure = e.message ?: "插件配置无效"
                } catch (e: IOException) {
                    LOG.info("AI commit message request failed: ${e.javaClass.simpleName}")
                    failure = e.message ?: "请求模型服务失败"
                } catch (e: Exception) {
                    LOG.warn("AI commit message generation failed: ${e.javaClass.simpleName}")
                    failure = e.message ?: e.javaClass.simpleName
                }
            }

            override fun onSuccess() {
                runningTasks.remove(running)
                if (project.isDisposed) return

                val message = generated
                when {
                    failure != null -> notify(project, "生成提交信息失败", failure!!, true)
                    message.isNullOrBlank() -> notify(project, "生成提交信息失败", "模型返回了空内容。", true)
                    else -> writeBack(project, messageUi, originalMessage, message)
                }
            }

            override fun onCancel() {
                runningTasks.remove(running)
            }

            override fun onThrowable(error: Throwable) {
                runningTasks.remove(running)
                super.onThrowable(error)
            }
        }.queue()
    }

    private fun writeBack(
        project: Project,
        messageUi: CommitMessageUi,
        originalMessage: String,
        generated: String,
    ) {
        if (project.isDisposed) return

        val current = messageUi.text.orEmpty()
        if (current != originalMessage) {
            notify(project, "提交信息已变化", "生成期间提交信息被修改，结果未写入。请重新生成。", true)
            return
        }

        messageUi.text = generated
        messageUi.focus()
    }

    private fun isRunning(commitUi: CommitWorkflowUi): Boolean =
        runningTasks.any { it.commitUi === commitUi }

    private fun notify(project: Project, title: String, content: String, isWarning: Boolean) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                title,
                content,
                if (isWarning) NotificationType.WARNING else NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private data class RunningCommitUi(val commitUi: CommitWorkflowUi)

    private companion object {
        val LOG = logger<GenerateCommitMessageAction>()
        val runningTasks = CopyOnWriteArrayList<RunningCommitUi>()
        const val NOTIFICATION_GROUP = "AI Commit Composer"
    }
}
