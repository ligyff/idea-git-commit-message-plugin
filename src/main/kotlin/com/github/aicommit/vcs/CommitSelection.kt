package com.github.aicommit.vcs

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.impl.PartialChangesUtil

/**
 * A change selected for commit, plus the selected text captured on EDT when the selection
 * is only a few blocks of the file. Capturing it up front stops a later checkbox change
 * from altering what gets sent to the model.
 */
class CommitSelection(
    val change: Change,
    val selectedAfterText: String?,
    val hasPartialSelection: Boolean,
) {
    companion object {
        private val LOG = logger<CommitSelection>()

        fun capture(project: Project, changes: List<Change>): List<CommitSelection> {
            val wrapped = PartialChangesUtil.wrapPartialChanges(project, changes)

            return wrapped.mapIndexed { index, change ->
                // wrapPartialChanges only returns a new instance when the change is partially committed.
                val isPartial = change !== changes[index]
                CommitSelection(
                    change = change,
                    selectedAfterText = if (isPartial) readPartialText(change) else null,
                    hasPartialSelection = isPartial,
                )
            }
        }

        // Partial revisions are Document-backed, so this stays cheap; plain revisions may run a VCS command.
        private fun readPartialText(change: Change): String? = try {
            change.afterRevision?.content
        } catch (e: Exception) {
            LOG.info("Cannot read partial content: ${e.javaClass.simpleName}")
            null
        }
    }
}
