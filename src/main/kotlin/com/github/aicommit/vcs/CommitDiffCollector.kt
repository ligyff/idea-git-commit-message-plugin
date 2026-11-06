package com.github.aicommit.vcs

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class CommitDiffCollector {
    fun collect(
        selections: List<CommitSelection>,
        unversionedFiles: List<FilePath>,
        indicator: ProgressIndicator,
    ): String {
        val result = StringBuilder()
        result.append("本次提交勾选的文件数：").append(selections.size + unversionedFiles.size).append('\n')

        if (selections.any { it.hasPartialSelection }) {
            result.append("提示：部分文件只提交了选中的代码块，未选中的代码不在下面的 diff 中。\n")
        }

        var remainingChars = MAX_TOTAL_CHARS
        var truncated = false

        for (selection in selections) {
            indicator.checkCanceled()
            if (remainingChars <= 0) {
                truncated = true
                break
            }

            val fileDiff = buildFileDiff(selection, indicator)
            val accepted = fileDiff.take(remainingChars)
            result.append(accepted)
            remainingChars -= accepted.length
            if (accepted.length < fileDiff.length) truncated = true
        }

        for (file in unversionedFiles) {
            indicator.checkCanceled()
            if (remainingChars <= 0) {
                truncated = true
                break
            }

            val fileDiff = buildNewFileDiff(file)
            val accepted = fileDiff.take(remainingChars)
            result.append(accepted)
            remainingChars -= accepted.length
            if (accepted.length < fileDiff.length) truncated = true
        }

        if (truncated) {
            result.append("\n[注意：因长度限制，上面只列出了部分变更内容，其余未展开]\n")
        }

        return result.toString()
    }

    private fun buildFileDiff(selection: CommitSelection, indicator: ProgressIndicator): String {
        val change = selection.change
        val before = change.beforeRevision
        val after = change.afterRevision
        val path = after?.file?.path ?: before?.file?.path ?: "未知路径"
        val header = "\n---\n文件：$path\n状态：${change.type}\n"

        if (before?.file?.isDirectory == true || after?.file?.isDirectory == true) {
            return "$header[目录变更不展开]\n"
        }

        val beforeText = readRevision(before)
        val afterText = selection.selectedAfterText ?: readRevision(after)

        if (beforeText == null || afterText == null) {
            return "$header[二进制、超长或无法读取，未发送其内容]\n"
        }
        if (beforeText == afterText) {
            return "$header[仅路径或文件状态变化]\n"
        }

        return header + createUnifiedDiff(beforeText, afterText, indicator)
    }

    private fun buildNewFileDiff(file: FilePath): String {
        val virtualFile = file.virtualFile
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(file.path)
            ?: return "\n---\n文件：${file.path}\n状态：新增\n[无法读取]\n"

        val text = readVirtualFile(virtualFile)
            ?: return "\n---\n文件：${file.path}\n状态：新增\n[二进制、超长或无法读取，未发送其内容]\n"

        val header = "\n---\n文件：${file.path}\n状态：新增\n"
        val lines = text.lineSequence().toList()
        val body = lines.take(MAX_NEW_FILE_LINES).joinToString("\n") { "+$it" }
        val footer = if (lines.size > MAX_NEW_FILE_LINES) "\n[其余新增行已省略]\n" else "\n"

        return header + body + footer
    }

    private fun readRevision(revision: ContentRevision?): String? {
        if (revision == null) return ""

        return try {
            val content = revision.content ?: return null
            content.takeIf { it.length <= MAX_FILE_CHARS && it.indexOf('\u0000') < 0 }
        } catch (e: Exception) {
            LOG.info("Cannot read revision content: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun readVirtualFile(file: VirtualFile): String? = try {
        val content = VfsUtilCore.loadText(file)
        content.takeIf { it.length <= MAX_FILE_CHARS && it.indexOf('\u0000') < 0 }
    } catch (e: Exception) {
        LOG.info("Cannot read file content: ${e.javaClass.simpleName}")
        null
    }

    private fun createUnifiedDiff(before: String, after: String, indicator: ProgressIndicator): String {
        val beforeLines = before.split('\n')
        val afterLines = after.split('\n')
        val fragments = ComparisonManager.getInstance().compareLines(
            before,
            after,
            ComparisonPolicy.DEFAULT,
            indicator,
        )
        val result = StringBuilder()

        fragments.forEachIndexed { index, fragment ->
            appendHunk(result, fragment, beforeLines, afterLines, index == fragments.lastIndex)
            if (result.length > MAX_FILE_CHARS) return@forEachIndexed
        }

        val text = result.toString()
        if (text.length <= MAX_FILE_CHARS) return text

        val cut = text.lastIndexOf('\n')
        return if (cut <= 0) text.take(MAX_FILE_CHARS) else text.substring(0, cut)
    }

    private fun appendHunk(
        result: StringBuilder,
        fragment: LineFragment,
        before: List<String>,
        after: List<String>,
        last: Boolean,
    ) {
        val beforeStart = fragment.startLine1
        val afterStart = fragment.startLine2
        val beforeEnd = fragment.endLine1
        val afterEnd = fragment.endLine2

        result.append("@@ -").append(beforeStart + 1).append(',')
            .append(beforeEnd - beforeStart).append(" +").append(afterStart + 1).append(',')
            .append(afterEnd - afterStart).append(" @@\n")

        for (line in beforeStart until beforeEnd) {
            if (line in before.indices) result.append('-').append(before[line]).append('\n')
        }
        for (line in afterStart until afterEnd) {
            if (line in after.indices) result.append('+').append(after[line]).append('\n')
        }

        if (!last) result.append("... 未变化的代码已省略 ...\n")
    }

    private companion object {
        val LOG = logger<CommitDiffCollector>()
        const val MAX_FILE_CHARS = 60_000
        const val MAX_TOTAL_CHARS = 120_000
        const val MAX_NEW_FILE_LINES = 2_000
    }
}
