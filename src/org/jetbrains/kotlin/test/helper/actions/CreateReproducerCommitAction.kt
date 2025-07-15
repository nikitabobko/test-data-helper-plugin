package org.jetbrains.kotlin.test.helper.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.actions.RefreshAction
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.test.helper.generateTestsAndWait
import org.jetbrains.kotlin.test.helper.services.TestDataRunnerService

class CreateReproducerCommitAction : RunSelectedFilesActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: return

        val ticketNumber = Messages.showInputDialog(
            project,
            "Enter ticket number (e.g., KT-12345):",
            "Create Reproducer Commit",
            Messages.getQuestionIcon()
        )?.trim()?.ifBlank { null } ?: return

        val service = project.service<TestDataRunnerService>()
        service.scope.launch {
            withBackgroundProgress(project, "Creating Reproducer Commit for $ticketNumber") {
                reportSequentialProgress { reporter ->
                    reporter.nextStep(25)
                    reporter.nextStep(50, "Generating Tests") {
                        generateTestsAndWait(project, files)
                    }
                    reporter.nextStep(75, "Running Tests and Applying Diffs") {
                        runTestAndApplyDiffLoop(project) { service.doCollectAndRunAllTests(e, files, debug = false) }
                    }

                    reporter.nextStep(100, "Committing Changes") {
                        commitAll(project, ticketNumber)

                        ActionUtil.performActionDumbAwareWithCallbacks(RefreshAction(), e)
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Kotlin Compiler DevKit Notifications")
                        .createNotification("Finished creating reproducer for $ticketNumber." , NotificationType.INFORMATION)
                        .notify(project)
                }
            }
        }
    }

    override val isDebug: Boolean
        get() = false

    private fun commitAll(project: Project, ticketNumber: String) {
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
            ?: return

        val git = Git.getInstance()

        git.runCommand(
            GitLineHandler(
                project,
                repository.root,
                GitCommand.ADD
            ).apply {
                addParameters(".")
            }
        )

        git.runCommand(
            GitLineHandler(
                project,
                repository.root,
                GitCommand.COMMIT
            ).apply {
                addParameters("-m", "[Tests] Reproduce #$ticketNumber")
            }
        )
    }
}